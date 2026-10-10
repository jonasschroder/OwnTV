package tv.own.owntv.features.home

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Shared Core HTTP dispatcher/pool; separate bounded cache, no background job and no stream access. */
internal class TvmatchenBroadcastSource(context: Context, client: OkHttpClient) : BroadcastMetadataSource {
    private val preferences = context.getSharedPreferences("mintv-companion", Context.MODE_PRIVATE)
    private val cache = AtomicFile(File(context.cacheDir, "mintv-broadcast.json"))
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private var reader: ExperimentalBroadcastReader? = null
    private val initialize = Mutex()
    val blocked get() = preferences.getBoolean("broadcast-blocked", false)

    override suspend fun assignment(fixture: BroadcastFixture): BroadcastAssignment? = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext null
        try {
            val current = initialize.withLock { reader ?: createReader().also { reader = it } }
            current.assignment(fixture)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // A failed cache/journal write must not crash Home or playback.
    }
    private fun enabled() = preferences.getBoolean("shl-enabled", false) && preferences.getBoolean("broadcast-enabled", false)
    private fun createReader(): ExperimentalBroadcastReader {
        val state = BroadcastReadState(blocked = blocked, windowAt = preferences.getLong("broadcast-window", 0),
            requests = preferences.getInt("broadcast-requests", 0).coerceIn(0, 6),
            fixtureRequests = preferences.getInt("broadcast-fixtures", 0).coerceIn(0, 3),
            retryAt = preferences.getLong("broadcast-retry", 0), failures = preferences.getInt("broadcast-failures", 0).coerceIn(0, 4))
        runCatching {
            require(cache.baseFile.length() in 1..100_000)
            val root = JSONObject(cache.openRead().bufferedReader().use { it.readText() })
            require(root.getString("termsHash") == TvmatchenHtml.reviewedTermsHash)
            state.robots = root.getString("robots").also { require(it.length <= 32_768) }
            state.robotsAt = root.getLong("robotsAt"); state.termsAt = root.getLong("termsAt")
            state.listingAt = root.getLong("listingAt")
            val index = root.getJSONObject("index"); require(index.length() <= 32)
            state.index = index.keys().asSequence().associateWith { key ->
                val paths = index.getJSONArray(key); require(paths.length() <= 3)
                (0 until paths.length()).map { paths.getString(it).also { path -> require(path.matches(Regex("/match/[a-z0-9-]{1,140}-[0-9]{1,10}"))) } }
            }
            val values = root.getJSONArray("assignments"); require(values.length() <= 16)
            for (i in 0 until values.length()) {
                val v = values.getJSONObject(i); val items = v.getJSONArray("channels"); require(items.length() in 1..8)
                state.assignments += BroadcastAssignment(BroadcastFixture("SHL", v.getString("home"), v.getString("away"),
                    LocalDate.parse(v.getString("date")), Instant.ofEpochMilli(v.getLong("faceoff"))),
                    (0 until items.length()).map { j -> items.getJSONObject(j).let { BroadcastChannel(it.getString("name"), it.getBoolean("linear")) } },
                    Instant.ofEpochMilli(v.getLong("at")), Instant.ofEpochMilli(v.getLong("expires")), "TVmatchen")
            }
            val attempted = root.getJSONObject("attempted"); require(attempted.length() <= 16)
            attempted.keys().forEach { state.attempted[it] = attempted.getLong(it) }
        }.onFailure {
            state.robotsAt = 0; state.termsAt = 0; state.listingAt = 0; state.index = emptyMap(); state.assignments.clear(); state.attempted.clear()
        }
        return ExperimentalBroadcastReader(BroadcastPageReader { path, limit ->
            client.companionRequest(Request.Builder().url("https://www.tvmatchen.nu$path")
                .header("User-Agent", "MinTV-ExperimentalBroadcast/0.2 (personal non-commercial test)").build(), limit)
        }, ::enabled, state, {
            // Small synchronous journal ensures cancellation/process death cannot reset rate limits.
            check(preferences.edit().putBoolean("broadcast-blocked", state.blocked).putLong("broadcast-window", state.windowAt)
                .putInt("broadcast-requests", state.requests).putInt("broadcast-fixtures", state.fixtureRequests)
                .putLong("broadcast-retry", state.retryAt).putInt("broadcast-failures", state.failures).commit())
            val root = JSONObject().put("termsHash", TvmatchenHtml.reviewedTermsHash).put("robots", state.robots).put("robotsAt", state.robotsAt)
                .put("termsAt", state.termsAt).put("listingAt", state.listingAt).put("index", JSONObject(state.index as Map<*, *>))
                .put("attempted", JSONObject(state.attempted as Map<*, *>)).put("assignments", JSONArray().apply {
                    state.assignments.forEach { v -> put(JSONObject().put("home", v.fixture.home).put("away", v.fixture.away)
                        .put("date", v.fixture.date).put("faceoff", v.fixture.faceoff.toEpochMilli()).put("at", v.checkedAt.toEpochMilli())
                        .put("expires", v.expiresAt.toEpochMilli()).put("channels", JSONArray().apply {
                            v.channels.forEach { put(JSONObject().put("name", it.name).put("linear", it.linear)) }
                        })) }
                })
            val bytes = root.toString().toByteArray(); require(bytes.size <= 100_000)
            val stream = cache.startWrite()
            try { stream.write(bytes); cache.finishWrite(stream) } catch (e: Exception) { cache.failWrite(stream); throw e }
        })
    }
}
