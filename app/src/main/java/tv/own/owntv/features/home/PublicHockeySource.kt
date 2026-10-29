package tv.own.owntv.features.home

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal interface PublicBroadcastCache { fun read(): String?; fun write(value: String) }
internal fun interface PublicBroadcastTransport { suspend fun read(slug: String): CompanionResponse }

/** Six public schedule requests at most once per hour, persisted across screen/app restarts. */
internal class PublicHockeyReader(private val transport: PublicBroadcastTransport, private val cache: PublicBroadcastCache,
    private val pause: suspend () -> Unit = { delay(1_100) }) {
    private val mutex = Mutex()
    private var initialized = false
    private var attempt = 0L
    private var retry = 0L
    private var rows = emptyList<PublicHockeyProgramme>()
    private fun store() = cache.write(JSONObject().put("attempt", attempt).put("retry", retry).put("rows", JSONArray().apply {
        rows.forEach { p -> put(JSONObject().put("channel", p.channel).put("title", p.title).put("description", p.description)
            .put("start", p.start).put("end", p.end).put("generated", p.generatedAt).put("fetched", p.fetchedAt)) }
    }).toString())
    suspend fun assignments(known: List<SportFixture>, now: Long, enabled: Boolean): Map<String, BroadcastAssignment> = mutex.withLock {
        if (!enabled || known.none { it.competition == SportsCatalog.shl && it.venue != null && it.faceoff in now..(now + 7 * 86_400_000L) }) return@withLock emptyMap()
        if (!initialized) {
            initialized = true
            runCatching { cache.read()?.let { text ->
                require(text.length <= 600_000)
                val root = JSONObject(text); attempt = root.getLong("attempt"); retry = root.getLong("retry")
                val list = root.getJSONArray("rows"); require(list.length() <= 600)
                rows = (0 until list.length()).map { i -> val p = list.getJSONObject(i)
                    PublicHockeyProgramme(p.getString("channel"), p.getString("title"), p.getString("description"),
                        p.getLong("start"), p.getLong("end"), p.getLong("generated"), p.getLong("fetched"))
                }.also { values -> require(values.all { it.channel in publicTvChannels.values && it.end > it.start &&
                    it.title.length <= 240 && it.description.length <= 4000 && it.generatedAt <= it.fetchedAt + 300_000L }) }
            } }.onFailure { rows = emptyList(); attempt = now; retry = now + publicTvTtl }
        }
        if (now < attempt) return@withLock emptyMap() // Clock rollback never creates a new budget window.
        if (now >= retry) {
            attempt = now; retry = now + publicTvTtl; rows = emptyList()
            try {
                store() // Reserve before HTTP, including process death/cancellation.
                val fresh = withTimeout(55_000) {
                    publicTvChannels.keys.flatMap { slug ->
                        val response = transport.read(slug)
                        if (response.status != 200) {
                            val header = response.retryAfter
                            val requested = header?.toLongOrNull()?.takeIf { it in 0..604_800 }?.let { now + it * 1000 }
                                ?: runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                            retry = maxOf(retry, requested ?: 0, if (response.status in listOf(401, 403)) now + 86_400_000L else 0)
                            error("Public schedule unavailable")
                        }
                        parsePublicHockey(slug, response.body, now).also { pause() }
                    }
                }
                rows = fresh; store() // Publish only after the entire six-channel scan succeeds.
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { rows = emptyList(); runCatching { store() } }
        }
        known.mapNotNull { fixture -> publicHockeyAssignment(fixture, known, rows, now)?.let { fixtureKey(fixture.broadcastFixture()) to it } }.toMap()
    }
}

/** Public metadata only. Uses the existing pool but never inherits IPTV auth/cookies/interceptors. */
internal class PublicHockeySource(context: Context, client: OkHttpClient) {
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val http = client.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false).followSslRedirects(false).build()
    private val file = AtomicFile(File(context.cacheDir, "mintv-public-hockey.json"))
    private val reader = PublicHockeyReader(PublicBroadcastTransport { slug ->
        require(slug in publicTvChannels)
        http.companionResponse(Request.Builder().url(publicTvBase + slug)
            .header("Accept", "application/json").header("User-Agent", "MinTV-Broadcast/0.1").build(), 300_000)
    }, object : PublicBroadcastCache {
        override fun read(): String? = runCatching {
            require(file.baseFile.length() in 1..600_000); file.openRead().bufferedReader().use { it.readText() }
        }.getOrNull()
        override fun write(value: String) {
            require(value.toByteArray().size <= 600_000)
            val stream = file.startWrite()
            try { stream.write(value.toByteArray()); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
    })
    suspend fun assignments(known: List<SportFixture>, now: Long, enabled: Boolean) =
        withContext(io) { reader.assignments(known, now, enabled) }
}
