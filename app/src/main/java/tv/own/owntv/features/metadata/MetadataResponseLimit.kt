package tv.own.owntv.features.metadata

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Only the metadata client's decompressed response; never changes IPTV transport or headers. */
internal class MetadataResponseLimit : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val original = response.body
        if (original.contentLength() > LIMIT) { response.close(); throw IOException("Metadata response budget") }
        val bounded = object : ResponseBody() {
            private val reader = object : ForwardingSource(original.source()) {
                private var total = 0L
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val count = super.read(sink, minOf(byteCount, LIMIT - total + 1))
                    if (count > 0) total += count
                    if (total > LIMIT) throw IOException("Metadata response budget")
                    return count
                }
            }.buffer()
            override fun contentType() = original.contentType()
            override fun contentLength() = original.contentLength()
            override fun source(): BufferedSource = reader
        }
        return response.newBuilder().body(bounded).build()
    }
    companion object { private const val LIMIT = 1_048_576L }
}
