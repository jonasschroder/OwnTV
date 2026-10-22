package tv.own.owntv.features.home

internal const val SMART_SERIES_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
internal enum class SmartResumeKind { MOVIE, EPISODE }
internal data class SmartResume(
    val key: String, val kind: SmartResumeKind, val itemId: Long, val seriesId: Long? = null,
    val title: String, val seriesTitle: String? = null, val season: Int? = null, val episode: Int? = null,
    val poster: String? = null, val playedAt: Long, val position: Long, val duration: Long,
    val completed: Boolean = false,
)
internal fun SmartResume.eligible(): Boolean = !completed && position > 0 && duration > 0 &&
    position.toDouble() / duration.toDouble() < .9

/** Exact catalog identity only. No title matching, automatic next episode, or timer-driven promotion. */
internal fun smartResumeOrder(items: List<SmartResume>): List<SmartResume> = items.filter { it.eligible() }
    .sortedWith(compareByDescending<SmartResume> { it.playedAt }.thenBy { it.key }).distinctBy { it.key }

internal fun smartHeroResume(items: List<SmartResume>, livePlayedAt: Long?, now: Long): SmartResume? {
    val latest = smartResumeOrder(items).firstOrNull() ?: return null
    if (livePlayedAt == null) return latest
    return latest.takeIf { it.kind == SmartResumeKind.EPISODE && it.playedAt > livePlayedAt &&
        it.playedAt <= now && now - it.playedAt <= SMART_SERIES_WINDOW_MS }
}

/** Keep the logical key; removed controls fall to their nearest retained neighbour, then first. */
internal fun smartFocusKey(key: String?, previous: List<String>, current: List<String>): String? {
    if (key in current) return key
    val index = previous.indexOf(key)
    if (index >= 0) for (distance in 1..previous.size) {
        listOf(index + distance, index - distance).mapNotNull(previous::getOrNull)
            .firstOrNull { it in current }?.let { return it }
    }
    return current.firstOrNull()
}
