package tv.own.owntv.features.sporthub

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal fun hubCacheName(key: String): String = MessageDigest.getInstance("SHA-256")
    .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

internal fun readBoundedHubBody(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val bytes = ByteArray(8192)
    while (true) {
        val size = input.read(bytes)
        if (size < 0) break
        require(output.size() + size <= HUB_MAX_BODY)
        output.write(bytes, 0, size)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal class FileHubCache(context: Context) : HubCache {
    private val root = File(context.cacheDir, "sport-hub-public")
    private val budgets = File(context.filesDir, "sport-hub-budgets")
    private val mutex = Mutex()
    private val protected = HubProvider.entries.map { hubCacheName(it.name) }.toSet()
    private fun fileFor(key: String): File = File(if (hubCacheName(key) in protected) budgets else root, hubCacheName(key))
    override suspend fun read(key: String): HubCacheEntry? = mutex.withLock { withContext(Dispatchers.IO) {
        val file = fileFor(key)
        runCatching { AtomicFile(file).openRead().use { HubCacheCodec.decode(readBoundedHubBody(it)) } }.getOrNull()
            ?.also { file.setLastModified(System.currentTimeMillis()) }
    } }
    override suspend fun write(key: String, value: HubCacheEntry): Unit = mutex.withLock { withContext(Dispatchers.IO) {
        root.mkdirs()
        budgets.mkdirs()
        val bytes = HubCacheCodec.encode(value).toByteArray(Charsets.UTF_8); require(bytes.size <= HUB_MAX_BODY)
        val file = AtomicFile(fileFor(key))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
        // Provider-wide denials/quotas survive ordinary public-cache eviction.
        root.listFiles()?.filter { it.isFile && it.name !in protected }?.sortedByDescending { it.lastModified() }
            ?.drop(64)?.forEach { it.delete() }
    } }
}
