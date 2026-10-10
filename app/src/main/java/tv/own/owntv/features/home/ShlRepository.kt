package tv.own.owntv.features.home

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Personal, experimental public-page reader; no requests until the user explicitly enables it. */
internal class ShlRepository(context: Context, client: OkHttpClient) {
    // Shares Core's dispatcher/pool; redirects never forward credentials or bypass an access gate.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val refreshMutex = Mutex()
    val preferences = context.getSharedPreferences("mintv-companion", Context.MODE_PRIVATE)
    private val cacheDirectory = context.cacheDir
    private fun cache(competition: Competition) = AtomicFile(File(cacheDirectory,
        if (competition.id == SportsCatalog.shl.id) "mintv-shl.json" else "mintv-ha.json"))
    private val snapshots = mutableMapOf<String, ShlSnapshot>()
    private val tables = mutableMapOf<String, Pair<Long, List<ShlStanding>>>()
    private var lastTableAt = 0L
    val standingsFetchedAt get() = lastTableAt
    private var robotsCheckedAt = 0L

    private suspend fun access(now: Long) {
        check(preferences.getBoolean("shl-enabled", false))
        check(!preferences.getBoolean("shl-access-blocked", false))
        if (now - robotsCheckedAt in 1 until 6 * 60 * 60_000L) return
        reserve(now)
        val (status, body) = client.companionRequest(request("/robots.txt"), 32_768)
        if (status == 401 || status == 403 || status == 429 || status == 200 && !SwehockeyParser.robotsPermit(body)) {
            preferences.edit().putBoolean("shl-access-blocked", true).apply()
            error("access denied")
        }
        require(status == 404 || status == 200)
        robotsCheckedAt = now
    }

    suspend fun cached(competition: Competition = SportsCatalog.shl): ShlSnapshot? = withContext(Dispatchers.IO) {
        require(competition in listOf(SportsCatalog.shl, SportsCatalog.allsvenskan))
        val cache = cache(competition)
        snapshots[competition.id] ?: runCatching {
            require(cache.baseFile.length() in 1..200_000)
            val root = JSONObject(cache.openRead().bufferedReader().use { it.readText() })
            val rows = root.getJSONArray("games")
            require(rows.length() in 1..400)
            ShlSnapshot(root.getString("season"), (0 until rows.length()).map { i ->
                val g = rows.getJSONObject(i)
                ShlGame(g.getString("id"), g.getString("home"), g.getString("away"), g.getLong("faceoff"),
                    g.optString("result").takeIf { it.isNotEmpty() })
            }, root.getLong("at"), root.optLong("sourceAt").takeIf { it > 0 })
        }.getOrNull()?.also { snapshots[competition.id] = it }
    }

    suspend fun refresh(now: Long, competition: Competition = SportsCatalog.shl): ShlSnapshot = refreshMutex.withLock { withContext(Dispatchers.IO) {
        check(preferences.getBoolean("shl-enabled", false))
        val current = cached(competition)
        val today = Instant.ofEpochMilli(now).atZone(Stockholm).toLocalDate()
        // This endpoint is a schedule/result snapshot, never represented as a verified live feed.
        val ttl = if (current?.games?.any { it.on(today) } == true) 60 * 60_000L else 6 * 60 * 60_000L
        current?.takeIf { now - it.fetchedAt in 0 until ttl }?.let { return@withContext it }
        access(now)
        val season = SwehockeyParser.discover(page("/"), competition.name)
        val html = page("/ScheduleAndResults/Schedule/$season")
        require(Regex("<h2>\\s*${Regex.escape(competition.name)}\\s*</h2>").containsMatchIn(html))
        val games = SwehockeyParser.schedule(html)
        val sourceAt = SwehockeyParser.lastUpdated(html)
        val seasonYear = if (today.monthValue >= 8) today.year else today.year - 1
        require(games.all {
            val date = Instant.ofEpochMilli(it.faceoff).atZone(Stockholm).toLocalDate()
            date >= java.time.LocalDate.of(seasonYear, 8, 1) && date < java.time.LocalDate.of(seasonYear + 1, 8, 1)
        })
        val fresh = ShlSnapshot(season, games, now, sourceAt)
        val root = JSONObject().put("season", season).put("at", now).put("sourceAt", sourceAt ?: 0).put("games", JSONArray().apply {
            games.forEach { g -> put(JSONObject().put("id", g.id).put("home", g.home).put("away", g.away)
                .put("faceoff", g.faceoff).put("result", g.result.orEmpty())) }
        })
        val cache = cache(competition)
        val stream = cache.startWrite()
        try {
            stream.write(root.toString().toByteArray())
            cache.finishWrite(stream)
        } catch (error: Exception) { cache.failWrite(stream); throw error }
        snapshots[competition.id] = fresh
        fresh
    } }

    suspend fun standings(season: String, now: Long, competition: Competition = SportsCatalog.shl): List<ShlStanding> = refreshMutex.withLock { withContext(Dispatchers.IO) {
        check(preferences.getBoolean("shl-enabled", false))
        require(competition in listOf(SportsCatalog.shl, SportsCatalog.allsvenskan))
        require(season.matches(Regex("\\d{1,9}")))
        val key = competition.id + ":" + season
        val existing = tables[key]?.takeIf { now - it.first in 0 until 6 * 60 * 60_000L }
        if (existing != null) { lastTableAt = existing.first; return@withContext existing.second }
        access(now)
        val html = page("/ScheduleAndResults/Standings/$season")
        require(Regex("<h2>\\s*${Regex.escape(competition.name)}\\s*</h2>").containsMatchIn(html))
        SwehockeyParser.standings(html).also {
            tables[key] = now to it; lastTableAt = now
            while (tables.size > 2) tables.remove(tables.minBy { it.value.first }.key)
        }
    } }

    private suspend fun page(path: String): String {
        check(preferences.getBoolean("shl-enabled", false) && !preferences.getBoolean("shl-access-blocked", false))
        reserve(System.currentTimeMillis())
        val (status, body) = client.companionRequest(request(path), 600_000)
        if (status == 401 || status == 403 || status == 429) preferences.edit().putBoolean("shl-access-blocked", true).apply()
        require(status == 200)
        return body
    }

    /** Journal before dispatch: repeated openings/recreation cannot bypass the shared two-league cap. */
    private fun reserve(now: Long) {
        check(preferences.getBoolean("shl-enabled", false) && !preferences.getBoolean("shl-access-blocked", false))
        val start = preferences.getLong("hockey-window-at", 0L)
        val next = HockeyRequestBudget(start, preferences.getInt("hockey-window-count", 0)).reserve(now)
        check(preferences.edit().putLong("hockey-window-at", next.windowAt)
            .putInt("hockey-window-count", next.count).commit())
    }

    private fun request(path: String): Request = Request.Builder().url("https://stats.swehockey.se$path")
        .header("User-Agent", "MinTV-ExperimentalSHL/0.2").build()
}

/** Shared persistent two-league budget; a backwards clock cannot open a fresh window. */
internal data class HockeyRequestBudget(val windowAt: Long = 0L, val count: Int = 0) {
    fun reserve(now: Long): HockeyRequestBudget {
        check(now >= 0 && windowAt in 0..now && count in 0..12)
        val expired = windowAt == 0L || now - windowAt >= 2 * 60 * 60_000L
        val used = if (expired) 0 else count
        check(used < 12)
        return HockeyRequestBudget(if (expired) now else windowAt, used + 1)
    }
}
