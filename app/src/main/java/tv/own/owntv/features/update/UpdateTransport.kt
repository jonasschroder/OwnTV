package tv.own.owntv.features.update

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** No credentials/interceptors from IPTV. Tests inject a non-networking fake OkHttp interceptor. */
internal class UpdateTransport(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS).build()) {
    internal class HttpFailure(val status: Int, val blockedUntil: Long = 0) : IOException()
    @Volatile private var foreground = false
    @Volatile private var activeCall: Call? = null
    fun setForeground(value: Boolean) { foreground = value; if (!value) cancel() }
    fun cancel() { activeCall?.cancel() }
    fun response(initial: String): Response {
        var url = initial
        repeat(4) {
            require(UpdatePolicy.allowedUrl(url, initial))
            val call = client.newCall(Request.Builder().url(url).build()); activeCall = call
            call.timeout().timeout(if (initial.endsWith(".apk")) 15 * 60L else 30L, TimeUnit.SECONDS)
            if (!foreground) { call.cancel(); throw IOException("Not visible") }
            val response = call.execute()
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val destination = response.header("Location"); response.close()
                require(destination != null && UpdatePolicy.allowedUrl(destination, initial)); url = destination
            } else {
                if (response.code == 403 || response.code == 429) {
                    val now = System.currentTimeMillis()
                    val retry = response.header("Retry-After")?.toLongOrNull()?.coerceIn(60, 86400)?.times(1000)
                    val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()?.coerceIn(0, Long.MAX_VALUE / 1000)?.times(1000)
                    val block = maxOf(now + UpdatePolicy.CHECK_INTERVAL, now + (retry ?: 0), (reset ?: 0).coerceAtMost(now + 86400000))
                    response.close(); throw HttpFailure(response.code, block)
                }
                if (!response.isSuccessful) { response.close(); throw HttpFailure(response.code) }
                return response
            }
        }
        throw HttpFailure(310)
    }
}
