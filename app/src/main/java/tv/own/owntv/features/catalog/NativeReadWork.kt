package tv.own.owntv.features.catalog

import java.util.concurrent.atomic.AtomicLongArray

/** Fixed aggregate counts only: no SQL, URLs, parameters, content IDs or retained owners. */
internal enum class NativeReadKind { CATALOG, GUIDE_SUMMARIES, PUBLIC_HTTP }
internal object NativeReadWork {
    private val totals = AtomicLongArray(NativeReadKind.entries.size)
    fun record(kind: NativeReadKind) { totals.incrementAndGet(kind.ordinal) }
    fun snapshot(): List<Long> = NativeReadKind.entries.map { totals.get(it.ordinal) }
}
