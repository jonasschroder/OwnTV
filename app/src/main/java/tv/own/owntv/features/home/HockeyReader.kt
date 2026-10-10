package tv.own.owntv.features.home

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal enum class HockeyFailureKind { DISABLED, ACCESS, NETWORK, FORMAT, HTTP, RATE_LIMITED, CACHE }
internal enum class HockeyStage { POLICY, ROBOTS, DISCOVERY, SCHEDULE, TABLE, CACHE }
internal data class HockeyIssue(val kind: HockeyFailureKind, val stage: HockeyStage, val at: Long,
    val httpCode: Int? = null, val retryAt: Long? = null) {
    val retryable get() = kind !in listOf(HockeyFailureKind.DISABLED, HockeyFailureKind.ACCESS)
}
internal class HockeyDataException(val issue: HockeyIssue) : Exception(issue.kind.name)
internal data class HockeyTable(val seasonId: String, val rows: List<ShlStanding>, val fetchedAt: Long)
internal data class HockeyResponseDiagnostic(val stage: HockeyStage, val httpCode: Int, val at: Long)
internal fun interface HockeyPages { suspend fun read(path: String, limit: Int): CompanionResponse }
internal interface HockeyStorage {
    fun enabled(): Boolean
    fun blocked(): Boolean
    fun number(key: String): Long
    fun journal(values: Map<String, Long>)
    fun text(key: String): String?
    fun text(key: String, value: String)
    fun block(code: Int?)
    fun cache(key: String): String?
    fun cache(key: String, value: String)
}

/** Same shared hard budget, bounded cache, typed failures and persistent conservative retry gates. */
internal class HockeyReader(private val storage: HockeyStorage, private val pages: HockeyPages) {
    private val mutex = Mutex()
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, ShlSnapshot>()
    private val tables = java.util.concurrent.ConcurrentHashMap<String, HockeyTable>()
    private var discovery: Pair<Long, Map<String, String>>? = null
    private fun key(c: Competition, table: Boolean) = c.id + if (table) ":table" else ":schedule"
    private fun cacheKey(c: Competition, table: Boolean) = (if (c == SportsCatalog.shl) "mintv-shl" else "mintv-ha") + if (table) "-table" else ""
    private fun requireCompetition(c: Competition) = require(c in listOf(SportsCatalog.shl, SportsCatalog.allsvenskan))
    fun lastResponse(c: Competition): HockeyResponseDiagnostic? {
        val stage = storage.number("hockey-http-stage:" + c.id).toInt() - 1
        val code = storage.number("hockey-http-code:" + c.id).toInt()
        val at = storage.number("hockey-http-at:" + c.id)
        return HockeyStage.entries.getOrNull(stage)?.let { HockeyResponseDiagnostic(it, code, at) }?.takeIf { at > 0 && code in 100..599 }
    }

    fun diagnostic(c: Competition, table: Boolean = false): HockeyIssue? = runCatching {
        val o = JSONObject(storage.text("hockey-diagnostic:" + key(c, table)) ?: return null)
        HockeyIssue(HockeyFailureKind.valueOf(o.getString("kind")), HockeyStage.valueOf(o.getString("stage")), o.getLong("at"),
            o.optInt("http").takeIf { it > 0 }, o.optLong("retryAt").takeIf { it > 0 })
    }.getOrNull()
    private fun record(c: Competition, table: Boolean, issue: HockeyIssue) {
        storage.text("hockey-diagnostic:" + key(c, table), JSONObject().put("kind", issue.kind.name).put("stage", issue.stage.name)
            .put("at", issue.at).put("http", issue.httpCode ?: 0).put("retryAt", issue.retryAt ?: 0).toString())
    }
    private fun gate(now: Long, c: Competition, table: Boolean) {
        if (!storage.enabled()) throw HockeyDataException(HockeyIssue(HockeyFailureKind.DISABLED, HockeyStage.POLICY, now))
        if (storage.blocked()) throw HockeyDataException(HockeyIssue(HockeyFailureKind.ACCESS, HockeyStage.POLICY, now,
            storage.number("shl-blocked-code").toInt().takeIf { it > 0 }))
        val limited = storage.number("hockey-rate-until")
        if (limited > now) throw HockeyDataException(HockeyIssue(HockeyFailureKind.RATE_LIMITED, HockeyStage.POLICY, now, 429, limited))
        val retry = storage.number("hockey-retry:" + key(c, table))
        if (retry > now) throw HockeyDataException((diagnostic(c, table) ?: HockeyIssue(HockeyFailureKind.NETWORK, HockeyStage.POLICY, now)).copy(retryAt = retry))
    }
    private fun reserve(now: Long) {
        val start = storage.number("hockey-window-at")
        val used = storage.number("hockey-window-count")
        if (now < 0 || start !in 0..now || used !in 0..12) throw HockeyDataException(HockeyIssue(HockeyFailureKind.ACCESS, HockeyStage.POLICY, now))
        if (start > 0 && now - start < 2 * 60 * 60_000L && used >= 12)
            throw HockeyDataException(HockeyIssue(HockeyFailureKind.RATE_LIMITED, HockeyStage.POLICY, now, retryAt = start + 2 * 60 * 60_000L))
        val next = HockeyRequestBudget(start, used.toInt()).reserve(now)
        storage.journal(mapOf("hockey-window-at" to next.windowAt, "hockey-window-count" to next.count.toLong()))
    }
    private suspend fun page(path: String, stage: HockeyStage, now: Long, c: Competition, table: Boolean, limit: Int = 600_000): CompanionResponse {
        currentCoroutineContext().ensureActive(); gate(now, c, table); reserve(now)
        val response = try { pages.read(path, limit) } catch (e: IOException) {
            throw HockeyDataException(HockeyIssue(HockeyFailureKind.NETWORK, stage, now))
        }
        currentCoroutineContext().ensureActive()
        if (!storage.enabled()) throw HockeyDataException(HockeyIssue(HockeyFailureKind.DISABLED, HockeyStage.POLICY, now))
        storage.journal(mapOf("hockey-http-stage:" + c.id to stage.ordinal.toLong() + 1,
            "hockey-http-code:" + c.id to response.status.toLong(), "hockey-http-at:" + c.id to now))
        if (response.status in listOf(401, 403)) {
            storage.block(response.status)
            throw HockeyDataException(HockeyIssue(HockeyFailureKind.ACCESS, stage, now, response.status))
        }
        if (response.status == 429) {
            val headerUntil = response.retryAfter?.let { value -> value.toLongOrNull()?.takeIf { it in 0..604_800 }?.let { now + it * 1000 }
                ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
            val until = maxOf(now + 15 * 60_000L, headerUntil ?: 0)
            storage.journal(mapOf("hockey-rate-until" to until))
            throw HockeyDataException(HockeyIssue(HockeyFailureKind.RATE_LIMITED, stage, now, 429, until))
        }
        if (response.status != 200 && !(stage == HockeyStage.ROBOTS && response.status == 404))
            throw HockeyDataException(HockeyIssue(HockeyFailureKind.HTTP, stage, now, response.status))
        if (response.body.toByteArray().size > limit) throw HockeyDataException(HockeyIssue(HockeyFailureKind.FORMAT, stage, now, response.status))
        return response
    }
    private suspend fun access(now: Long, c: Competition, table: Boolean) {
        val checkedAt = storage.number("hockey-robots-at")
        if (checkedAt > 0 && now - checkedAt in 0 until 6 * 60 * 60_000L) return
        val response = page("/robots.txt", HockeyStage.ROBOTS, now, c, table, 32_768)
        if (response.status == 200 && !parsed(HockeyStage.ROBOTS, now) { SwehockeyParser.robotsPermit(response.body) }) {
            storage.block(null); throw HockeyDataException(HockeyIssue(HockeyFailureKind.ACCESS, HockeyStage.ROBOTS, now))
        }
        storage.journal(mapOf("hockey-robots-at" to now))
    }
    private inline fun <T> parsed(stage: HockeyStage, now: Long, block: () -> T): T = try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw HockeyDataException(HockeyIssue(HockeyFailureKind.FORMAT, stage, now)) }

    private fun validateGames(c: Competition, games: List<ShlGame>, now: Long) {
        val today = Instant.ofEpochMilli(now).atZone(Stockholm).toLocalDate()
        val year = if (today.monthValue >= 8) today.year else today.year - 1
        val clubs = SportsCatalog.inCompetition(c).map { it.id }.toSet()
        require(games.size in 1..400 && games.map { it.id }.distinct().size == games.size)
        require(games.all { g ->
            val date = Instant.ofEpochMilli(g.faceoff).atZone(Stockholm).toLocalDate()
            date >= LocalDate.of(year, 8, 1) && date < LocalDate.of(year + 1, 8, 1) &&
                SportsCatalog.identity(g.home, c.sport)?.id in clubs && SportsCatalog.identity(g.away, c.sport)?.id in clubs
        })
    }
    private fun validateTable(c: Competition, rows: List<ShlStanding>) {
        require(rows.size == 14 && rows.map { it.rank }.toSet() == (1..14).toSet() && rows.all { it.points in 0..999 })
        require(rows.map { SportsCatalog.identity(it.team, c.sport)?.id }.toSet() == SportsCatalog.inCompetition(c).map { it.id }.toSet())
    }
    fun cached(c: Competition, now: Long): ShlSnapshot? {
        requireCompetition(c)
        return (snapshots[c.id] ?: runCatching {
            val root = JSONObject(storage.cache(cacheKey(c, false)) ?: return null)
            require(root.optString("competition", c.id) == c.id)
            val rows = root.getJSONArray("games"); require(rows.length() in 1..400)
            ShlSnapshot(root.getString("season"), (0 until rows.length()).map { i -> val g = rows.getJSONObject(i)
                ShlGame(g.getString("id"), g.getString("home"), g.getString("away"), g.getLong("faceoff"), g.optString("result").takeIf { it.isNotEmpty() })
            }, root.getLong("at"), root.optLong("sourceAt").takeIf { it > 0 })
        }.getOrNull())?.takeIf { it.fetchedAt in 1..now && runCatching { validateGames(c, it.games, now) }.isSuccess }
            ?.also { snapshots[c.id] = it }
    }
    fun cachedTable(c: Competition, season: String): HockeyTable? {
        requireCompetition(c)
        return (tables[c.id] ?: runCatching {
            val root = JSONObject(storage.cache(cacheKey(c, true)) ?: return null)
            require(root.getString("competition") == c.id)
            val rows = root.getJSONArray("rows"); require(rows.length() == 14)
            HockeyTable(root.getString("season"), (0 until rows.length()).map { i -> val r = rows.getJSONObject(i)
                ShlStanding(r.getInt("rank"), r.getString("team"), r.getInt("points"), r.optInt("played", -1).takeIf { it >= 0 }, r.optInt("difference", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE })
            }, root.getLong("at"))
        }.getOrNull())?.takeIf { it.seasonId == season && runCatching { validateTable(c, it.rows) }.isSuccess }?.also { tables[c.id] = it }
    }
    private suspend fun <T> attempt(c: Competition, table: Boolean, now: Long, operation: suspend () -> T): T {
        gate(now, c, table)
        return try {
            operation().also {
                storage.journal(mapOf("hockey-retry:" + key(c, table) to 0, "hockey-failures:" + key(c, table) to 0))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val issue = (error as? HockeyDataException)?.issue ?: HockeyIssue(HockeyFailureKind.CACHE, HockeyStage.CACHE, now)
            val failures = (storage.number("hockey-failures:" + key(c, table)) + 1).coerceIn(1, 5)
            val retry = issue.retryAt ?: if (issue.retryable) now + minOf(30 * 60_000L,
                (if (issue.kind == HockeyFailureKind.FORMAT) 10 * 60_000L else 2 * 60_000L) * (1L shl (failures.toInt() - 1))) else 0
            val reported = issue.copy(retryAt = retry.takeIf { it > 0 })
            // No valid cache is removed on any failure. A corrupt/unwritable journal fails closed.
            storage.journal(mapOf("hockey-retry:" + key(c, table) to retry, "hockey-failures:" + key(c, table) to failures))
            record(c, table, reported); throw HockeyDataException(reported)
        }
    }
    suspend fun refresh(c: Competition, now: Long): ShlSnapshot = mutex.withLock {
        requireCompetition(c); gate(now, c, false)
        cached(c, now)?.let { current ->
            val ttl = if (current.games.any { it.on(stockholmDay(now)) }) 60 * 60_000L else 6 * 60 * 60_000L
            if (now - current.fetchedAt in 0 until ttl) return@withLock current
        }
        attempt(c, false, now) {
            access(now, c, false)
            val season = discovery?.takeIf { now - it.first in 0 until 6 * 60 * 60_000L }?.second?.get(c.id) ?: run {
                val html = page("/", HockeyStage.DISCOVERY, now, c, false).body
                val ids = parsed(HockeyStage.DISCOVERY, now) { listOf(SportsCatalog.shl, SportsCatalog.allsvenskan).associate { it.id to SwehockeyParser.discover(html, it.name) } }
                discovery = now to ids; ids.getValue(c.id)
            }
            val html = page("/ScheduleAndResults/Schedule/$season", HockeyStage.SCHEDULE, now, c, false).body
            val fresh = parsed(HockeyStage.SCHEDULE, now) {
                SwehockeyParser.competitionPage(html, c, season, now, false)
                val games = SwehockeyParser.schedule(html); validateGames(c, games, now)
                ShlSnapshot(season, games, now, SwehockeyParser.lastUpdated(html))
            }
            currentCoroutineContext().ensureActive()
            val root = JSONObject().put("competition", c.id).put("season", season).put("at", now).put("sourceAt", fresh.sourceUpdatedAt ?: 0)
                .put("games", JSONArray().apply { fresh.games.forEach { g -> put(JSONObject().put("id", g.id).put("home", g.home).put("away", g.away)
                    .put("faceoff", g.faceoff).put("result", g.result.orEmpty())) } })
            storage.cache(cacheKey(c, false), root.toString()); snapshots[c.id] = fresh; fresh
        }
    }
    suspend fun standings(c: Competition, season: String, now: Long): HockeyTable = mutex.withLock {
        requireCompetition(c); require(season.matches(Regex("\\d{1,9}"))); gate(now, c, true)
        require(cached(c, now)?.seasonId == season)
        cachedTable(c, season)?.takeIf { now - it.fetchedAt in 0 until 6 * 60 * 60_000L }?.let { return@withLock it }
        attempt(c, true, now) {
            access(now, c, true)
            val html = page("/ScheduleAndResults/Standings/$season", HockeyStage.TABLE, now, c, true).body
            val fresh = parsed(HockeyStage.TABLE, now) {
                SwehockeyParser.competitionPage(html, c, season, now, true)
                val rows = SwehockeyParser.standings(html); validateTable(c, rows); HockeyTable(season, rows, now)
            }
            currentCoroutineContext().ensureActive()
            val root = JSONObject().put("competition", c.id).put("season", season).put("at", now)
                .put("rows", JSONArray().apply { fresh.rows.forEach { r -> put(JSONObject().put("rank", r.rank).put("team", r.team).put("points", r.points)
                    .put("played", r.played ?: -1).put("difference", r.goalDifference ?: Int.MIN_VALUE)) } })
            storage.cache(cacheKey(c, true), root.toString()); tables[c.id] = fresh; fresh
        }
    }
}
