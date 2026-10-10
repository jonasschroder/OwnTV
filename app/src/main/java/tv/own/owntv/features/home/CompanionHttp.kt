package tv.own.owntv.features.home

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses Core's ordinary client. Bounded reads run on OkHttp's worker; cancellation closes the call. */
internal data class CompanionResponse(val status: Int, val body: String, val retryAfter: String? = null)

internal suspend fun OkHttpClient.companionRequest(request: Request, limit: Int): Pair<Int, String> =
    companionResponse(request, limit).let { it.status to it.body }

internal suspend fun OkHttpClient.companionResponse(request: Request, limit: Int): CompanionResponse =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        call.timeout().timeout(8, TimeUnit.SECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.request.url.host != request.url.host) throw IOException()
                        if (it.code in listOf(401, 403, 429)) {
                            if (continuation.isActive) continuation.resume(CompanionResponse(it.code, "", it.header("Retry-After")))
                            return
                        }
                        val body = it.body
                        if (body.contentLength() > limit) throw IOException()
                        val bytes = body.byteStream().use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > limit) throw IOException()
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        if (continuation.isActive) continuation.resume(CompanionResponse(it.code, bytes.toString(Charsets.UTF_8), it.header("Retry-After")))
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
