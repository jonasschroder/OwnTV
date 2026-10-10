package tv.own.owntv.features.home

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CompanionHttpTest {
    private val request = Request.Builder().url("https://example.invalid/test").build()
    @Test fun rejectsOversizedBodyAndAcceptsBoundedResponse() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("abcdefgh".toResponseBody()).build()
        }.build()
        try {
            assertEquals(200 to "abcdefgh", client.companionRequest(request, 8))
            try { client.companionRequest(request, 7); fail("Oversized response accepted") }
            catch (_: IOException) { }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun cancellationCancelsTheUnderlyingCall() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.yield()
            if (chain.call().isCanceled()) cancelled.countDown()
            throw IOException()
        }.build()
        try {
            withTimeout(5000) {
                val job = async { client.companionRequest(request, 32) }
                kotlinx.coroutines.yield()
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                job.cancel()
                job.join()
                assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
