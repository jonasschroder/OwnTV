package tv.own.owntv.features.home

import org.json.JSONArray
import org.json.JSONObject

internal enum class HockeyOutcome {
    REQUESTED, DISABLED, ACCESS_BLOCKED, RATE_LIMITED, BACKOFF, CACHE_HIT, CACHE_MISS,
    CACHE_REJECTED, HTTP_RESPONSE, TIMEOUT, NETWORK_ERROR, FORMAT_ERROR, SEASON_MISSING,
    VALIDATION_REJECTED, CACHE_WRITE_FAILED, VALID_SUCCESS, EMPTY_SUCCESS, CANCELLED, PUBLISHED,
}

/** Fixed public source/league identifiers and bounded numbers; no free-form server/user text. */
internal data class HockeyTrace(val league: String, val stage: HockeyStage, val outcome: HockeyOutcome,
    val at: Long, val http: Int? = null, val count: Int? = null, val cacheAge: Long? = null, val retryAt: Long? = null,
    val detail: HockeyFormatReason? = null) {
    init {
        require(league in listOf(SportsCatalog.shl.id, SportsCatalog.allsvenskan.id))
        require(at >= 0 && (http == null || http in 100..599) && (count == null || count in 0..400))
        require(cacheAge == null || cacheAge >= 0)
        require(retryAt == null || retryAt >= 0)
    }
}

/** One small persistent journal per league, compatible with existing caches/preferences. */
internal class HockeyDiagnostics(private val storage: HockeyStorage) {
    private val fallback = mutableMapOf<String, List<HockeyTrace>>()
    @Synchronized fun events(c: Competition): List<HockeyTrace> = fallback[c.id] ?: runCatching {
        val raw = storage.text("hockey-trace:" + c.id) ?: return emptyList()
        require(raw.length <= 16_384)
        val rows = JSONArray(raw); require(rows.length() <= 24)
        (0 until rows.length()).map { i -> val row = rows.getJSONObject(i)
            HockeyTrace(c.id, HockeyStage.valueOf(row.getString("stage")), HockeyOutcome.valueOf(row.getString("outcome")),
                row.getLong("at"), row.optInt("http").takeIf { it != 0 }, row.optInt("count", -1).takeIf { it >= 0 },
                row.optLong("age", -1).takeIf { it >= 0 }, row.optLong("retry", -1).takeIf { it >= 0 },
                HockeyFormatReason.entries.firstOrNull { it.name == row.optString("detail") })
        }
    }.getOrDefault(emptyList())

    @Synchronized fun record(value: HockeyTrace) {
        val c = if (value.league == SportsCatalog.shl.id) SportsCatalog.shl else SportsCatalog.allsvenskan
        val rows = (events(c) + value).takeLast(24)
        fallback[c.id] = rows
        try {
            if (value.outcome == HockeyOutcome.REQUESTED)
                storage.journal(mapOf("hockey-attempt-at:" + c.id to value.at))
            if (value.stage == HockeyStage.SCHEDULE && value.outcome in listOf(HockeyOutcome.VALID_SUCCESS, HockeyOutcome.EMPTY_SUCCESS))
                storage.journal(mapOf("hockey-cache-count:" + c.id to (value.count ?: 0).toLong(), "hockey-cache-at:" + c.id to value.at))
            if (value.outcome in listOf(HockeyOutcome.VALID_SUCCESS, HockeyOutcome.EMPTY_SUCCESS))
                storage.journal(mapOf("hockey-success-at:" + c.id to value.at, "hockey-success-count:" + c.id to (value.count ?: 0).toLong()))
            if (value.outcome in listOf(HockeyOutcome.CACHE_HIT, HockeyOutcome.CACHE_MISS, HockeyOutcome.CACHE_REJECTED))
                storage.journal(mapOf("hockey-cache-count:" + c.id to (value.count ?: 0).toLong(), "hockey-cache-at:" + c.id to value.at))
            if (value.outcome == HockeyOutcome.CACHE_HIT && value.cacheAge != null &&
                storage.number("hockey-success-at:" + c.id) == 0L && value.at > value.cacheAge)
                storage.journal(mapOf("hockey-success-at:" + c.id to value.at - value.cacheAge,
                    "hockey-success-count:" + c.id to (value.count ?: 0).toLong()))
            storage.text("hockey-trace:" + c.id, JSONArray().apply { rows.forEach { trace ->
                put(JSONObject().put("stage", trace.stage.name).put("outcome", trace.outcome.name).put("at", trace.at)
                    .put("http", trace.http ?: 0).put("count", trace.count ?: -1).put("age", trace.cacheAge ?: -1)
                    .put("retry", trace.retryAt ?: -1).put("detail", trace.detail?.name.orEmpty()))
            } }.toString())
        } catch (_: Exception) {
            // A broken preference store cannot journal its own failure. Keep classified evidence
            // in memory so Settings/export can still report it; no retry or network work here.
            fallback[c.id] = (rows + value.copy(stage = HockeyStage.CACHE, outcome = HockeyOutcome.CACHE_WRITE_FAILED)).takeLast(24)
        }
    }

    fun lastAttempt(c: Competition): Long? = runCatching { storage.number("hockey-attempt-at:" + c.id).takeIf { it > 0 } }.getOrNull()
    fun lastSuccess(c: Competition): Long? = runCatching { storage.number("hockey-success-at:" + c.id).takeIf { it > 0 } }.getOrNull()
    fun cachedCount(c: Competition): Int = runCatching { storage.number("hockey-cache-count:" + c.id).coerceIn(0, 400).toInt() }.getOrDefault(0)

    fun report(): String = listOf(SportsCatalog.shl, SportsCatalog.allsvenskan).flatMap { c -> events(c) }.take(48)
        .joinToString("\n") { "source=SWEHOCKEY league=${it.league} stage=${it.stage} outcome=${it.outcome} at=${it.at} http=${it.http ?: 0} count=${it.count ?: -1} cacheAgeMs=${it.cacheAge ?: -1} retryAt=${it.retryAt ?: 0} detail=${it.detail?.name.orEmpty()}" }
}
