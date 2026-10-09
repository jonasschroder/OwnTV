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
    private val cache = AtomicFile(File(context.cacheDir, "mintv-shl.json"))
    private var snapshot: ShlSnapshot? = null
    private var table: Pair<Long, List<ShlStanding>>? = null
    private var tableSeason: String? = null
    val standingsFetchedAt get() = table?.first ?: 0L
    private var robotsCheckedAt = 0L

    private suspend fun access(now: Long) {
        check(preferences.getBoolean("shl-enabled", false))
        check(!preferences.getBoolean("shl-access-blocked", false))
        if (now - robotsCheckedAt in 1 until 6 * 60 * 60_000L) return
        val (status, body) = client.companionRequest(request("/robots.txt"), 32_768)
        if (status == 401 || status == 403 || status == 429 || status == 200 && !SwehockeyParser.robotsPermit(body)) {
            preferences.edit().putBoolean("shl-access-blocked", true).apply()
            error("access denied")
        }
        require(status == 404 || status == 200)
        robotsCheckedAt = now
    }

    suspend fun cached(): ShlSnapshot? = withContext(Dispatchers.IO) {
        snapshot ?: runCatching {
            require(cache.baseFile.length() in 1..200_000)
            val root = JSONObject(cache.openRead().bufferedReader().use { it.readText() })
            val rows = root.getJSONArray("games")
            require(rows.length() in 1..400)
            ShlSnapshot(root.getString("season"), (0 until rows.length()).map { i ->
                val g = rows.getJSONObject(i)
                ShlGame(g.getString("id"), g.getString("home"), g.getString("away"), g.getLong("faceoff"),
                    g.optString("result").takeIf { it.isNotEmpty() })
            }, root.getLong("at"), root.optLong("sourceAt").takeIf { it > 0 })
        }.getOrNull()?.also { snapshot = it }
    }

    suspend fun refresh(now: Long): ShlSnapshot = refreshMutex.withLock { withContext(Dispatchers.IO) {
        val current = cached()
        val today = Instant.ofEpochMilli(now).atZone(Stockholm).toLocalDate()
        // This endpoint is a schedule/result snapshot, never represented as a verified live feed.
        val ttl = if (current?.games?.any { it.on(today) } == true) 10 * 60_000L else 6 * 60 * 60_000L
        current?.takeIf { now - it.fetchedAt in 0 until ttl }?.let { return@withContext it }
        access(now)
        val season = SwehockeyParser.discover(page("/"))
        val html = page("/ScheduleAndResults/Schedule/$season")
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
        val stream = cache.startWrite()
        try {
            stream.write(root.toString().toByteArray())
            cache.finishWrite(stream)
        } catch (error: Exception) { cache.failWrite(stream); throw error }
        snapshot = fresh
        fresh
    } }

    suspend fun standings(season: String, now: Long): List<ShlStanding> = refreshMutex.withLock { withContext(Dispatchers.IO) {
        require(season.matches(Regex("\\d{1,9}")))
        table?.takeIf { tableSeason == season && now - it.first in 0 until 6 * 60 * 60_000L }?.second
            ?: run { access(now); SwehockeyParser.standings(page("/ScheduleAndResults/Standings/$season")) }
                .also { table = now to it; tableSeason = season }
    } }

    private suspend fun page(path: String): String {
        check(preferences.getBoolean("shl-enabled", false) && !preferences.getBoolean("shl-access-blocked", false))
        val (status, body) = client.companionRequest(request(path), 600_000)
        if (status == 401 || status == 403 || status == 429) preferences.edit().putBoolean("shl-access-blocked", true).apply()
        require(status == 200)
        return body
    }

    private fun request(path: String): Request = Request.Builder().url("https://stats.swehockey.se$path")
        .header("User-Agent", "MinTV-ExperimentalSHL/0.2").build()
}
