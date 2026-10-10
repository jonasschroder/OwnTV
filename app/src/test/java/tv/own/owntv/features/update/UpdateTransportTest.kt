package tv.own.owntv.features.update

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class UpdateTransportTest {
    private val initial = "https://api.github.com/repos/jonasschroder/OwnTV/releases?per_page=20"
    @Test fun backgroundAndCancelledRequestsNeverReachTheTransport() {
        var requests = 0
        val transport = UpdateTransport(OkHttpClient.Builder().addInterceptor { requests++; error("Unexpected request") }.build())
        try { transport.response(initial); fail() } catch (_: IOException) { }
        assertEquals(0, requests)
        transport.setForeground(true); transport.setForeground(false)
        try { transport.response(initial); fail() } catch (_: IOException) { }
        assertEquals(0, requests)
    }
    @Test fun github403And429AreNotRetriedOrBypassed() {
        for (code in listOf(403, 429)) {
            var requests = 0
            val transport = UpdateTransport(OkHttpClient.Builder().addInterceptor {
                requests++
                Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                    .header("Retry-After", "86400").body("limited".toResponseBody()).build()
            }.build())
            transport.setForeground(true)
            try { transport.response(initial); fail() } catch (failure: UpdateTransport.HttpFailure) {
                assertEquals(code, failure.status)
                assertTrue(failure.blockedUntil > System.currentTimeMillis() + UpdatePolicy.CHECK_INTERVAL)
            }
            assertEquals(1, requests)
        }
    }
    @Test fun networkFailureIsPropagatedWithoutAutomaticRequests() {
        var requests = 0
        val transport = UpdateTransport(OkHttpClient.Builder().addInterceptor { requests++; throw IOException("Synthetic disconnect") }.build())
        transport.setForeground(true)
        try { transport.response(initial); fail() } catch (_: IOException) { }
        assertEquals(1, requests)
    }
    @Test fun untrustedRedirectIsRejectedBeforeFollowingIt() {
        var requests = 0
        val transport = UpdateTransport(OkHttpClient.Builder().followRedirects(false).addInterceptor {
            requests++
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(302).message("fixture")
                .header("Location", "https://evil.example/update.apk").body("".toResponseBody()).build()
        }.build())
        transport.setForeground(true)
        try { transport.response(initial); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, requests)
    }
    @Test fun missingReleaseAssetFailsInsteadOfSelectingAnotherApk() {
        val transport = UpdateTransport(OkHttpClient.Builder().addInterceptor {
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(404).message("fixture").body("".toResponseBody()).build()
        }.build())
        transport.setForeground(true)
        try { transport.response(initial); fail() } catch (failure: UpdateTransport.HttpFailure) { assertEquals(404, failure.status) }
    }
}
