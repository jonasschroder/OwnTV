package tv.own.owntv.features.sporthub

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Separate keyless client: no IPTV interceptors, cookies, authorization or redirect forwarding. */
internal class PublicHubTransport : HubTransport {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    override suspend fun fetch(league: HubLeague, season: String, now: java.time.Instant, window: HubWindow): HubHttp {
        require(league.access == HubAccess.VERIFIED_PUBLIC)
        val url = when (league.provider) {
            HubProvider.OPENLIGA -> {
                require(league.sourceSlug in setOf("del", "pl", "CHL") && season.matches(Regex("[0-9]{4}")))
                "https://api.openligadb.de/getmatchdata/${league.sourceSlug}/$season"
            }
            HubProvider.EVENTS24 -> {
                require(league.sourceSlug?.toLongOrNull()?.let { it > 0 } == true)
                require(window.allowed(now))
                "https://sportsevents24.com/api/v1/events?tournament_id=${league.sourceSlug}&limit=100&from=${window.from}&to=${window.to}"
            }
            else -> error("Unsupported public sports host")
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", "MinTV/0.19 (+https://github.com/jonasschroder/OwnTV)")
            .header("Accept", "application/json").build()
        return suspendCancellableCoroutine { continuation ->
            tv.own.owntv.features.catalog.NativeReadWork.record(tv.own.owntv.features.catalog.NativeReadKind.PUBLIC_HTTP)
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(HubHttp(0, ""))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        val retry = response.header("Retry-After")?.let(::hubRetryAfter)
                        if (response.code != 200) return@use HubHttp(response.code, "", retry, java.time.Instant.now())
                        val body = response.body
                        require(body.contentLength() <= HUB_MAX_BODY)
                        HubHttp(200, body.byteStream().use(::readBoundedHubBody), receivedAt = java.time.Instant.now())
                    } }.getOrDefault(HubHttp(0, ""))
                    // Never report exception/request text: other transports may use private keys.
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }
}

internal fun hubRetryAfter(value: String, now: java.time.Instant = java.time.Instant.now()): Long? =
    value.toLongOrNull()?.takeIf { it >= 0 }?.let { if (it > Long.MAX_VALUE / 1000) Long.MAX_VALUE else it * 1000 }
        ?: runCatching { java.time.Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
            .seconds.coerceAtLeast(0).let { if (it > Long.MAX_VALUE / 1000) Long.MAX_VALUE else it * 1000 } }.getOrNull()
