package tv.own.owntv.features.live

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val SWEDISH_EPG_WINDOW = 16
internal const val SWEDISH_EPG_CACHE = 32
internal data class SwedishRowKey(val id: Long, val sourceId: Long, val stableKey: String)
internal fun SwedishVariant.rowKey() = SwedishRowKey(id, sourceId, stableKey)
internal data class SwedishProgramme(val id: Long, val title: String, val startMs: Long, val stopMs: Long) {
    fun airing(at: Long) = title.isNotBlank() && startMs <= at && at < stopMs && startMs < stopMs
    fun progress(at: Long): Float = if (startMs >= stopMs) 0f else
        ((at.toDouble() - startMs) / (stopMs.toDouble() - startMs)).toFloat().coerceIn(0f, 1f)
}
internal data class SwedishRowInfo(val logo: String?, val now: SwedishProgramme?, val next: SwedishProgramme?) {
    fun at(at: Long): SwedishRowInfo {
        val current = now?.takeIf { it.airing(at) } ?: next?.takeIf { it.airing(at) }
        val upcoming = next?.takeIf { it.title.isNotBlank() && it.startMs > at && it.startMs < it.stopMs &&
            (current == null || it.startMs >= current.stopMs) }
        return copy(now = current, next = upcoming)
    }
    fun expires(at: Long): Long = minOf(at + if (now == null && next == null) 30_000 else 60_000,
        now?.stopMs?.takeIf { it > at } ?: Long.MAX_VALUE,
        next?.startMs?.takeIf { it > at } ?: Long.MAX_VALUE)
}

/** One foreground owner. At most 32 tiny summaries; no programme descriptions or stream URLs. */
internal class SwedishRowCache {
    private data class Entry(val fetchedAt: Long, val until: Long, val info: SwedishRowInfo)
    private val entries = object : LinkedHashMap<SwedishRowKey, Entry>(SWEDISH_EPG_CACHE, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SwedishRowKey, Entry>?) = size > SWEDISH_EPG_CACHE
    }
    val size get() = entries.size
    suspend fun load(keys: List<SwedishRowKey>, at: Long,
        read: suspend (List<SwedishRowKey>, Long) -> Map<SwedishRowKey, SwedishRowInfo>): Map<SwedishRowKey, SwedishRowInfo> {
        val window = keys.distinct().take(SWEDISH_EPG_WINDOW)
        val missing = window.filter { key -> entries[key]?.let { at >= it.fetchedAt && at < it.until } != true }
        if (missing.isNotEmpty()) {
            val fresh = read(missing, at)
            currentCoroutineContext().ensureActive() // Even a non-cooperative DB read cannot publish after cancellation.
            missing.forEach { key ->
                entries.remove(key)
                fresh[key]?.let { entries[key] = Entry(at, it.expires(at), it) }
            }
        }
        return window.mapNotNull { key -> entries[key]?.let { key to it.info.at(at) } }.toMap()
    }
}

/** Only visible logical groups plus two following groups; never the loaded library/guide. */
internal fun swedishEpgWindow(groups: List<SwedishGroup>, visible: List<String>, choices: SwedishChoices): List<SwedishVariant> {
    val positions = groups.withIndex().filter { it.value.identity in visible }.map { it.index }
    if (positions.isEmpty()) return emptyList()
    val indices = (positions + ((positions.last() + 1)..minOf(groups.lastIndex, positions.last() + 2))).distinct()
    return indices.take(SWEDISH_EPG_WINDOW).mapNotNull { groups[it].preferred(choices.preferred[groups[it].identity], choices.showUhd) }
}
