package tv.own.owntv.features.home

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

internal data class HockeyEpgRead(val rows: List<EpgProgrammeEntity>, val truncated: Boolean = false)
internal data class HockeyEpgWindow(val from: Long, val to: Long)

/** Exact schedule windows fill the tail omitted by the existing 128-programme local reader.
 * Merge simultaneous games before querying. No provider calls, persistent cache or wider horizon. */
internal fun hockeyEpgLookahead(known: List<SportFixture>, now: Long): List<HockeyEpgWindow> {
    val windows = known.filter { it.status == MatchStatus.SCHEDULED && it.competition.sport == Sport.ICE_HOCKEY &&
        it.faceoff >= now - 5 * 60 * 60_000L && it.faceoff <= now + 7 * 24 * 60 * 60_000L }
        .map { HockeyEpgWindow(it.faceoff - 60 * 60_000L, it.faceoff + 3 * 60 * 60_000L) }.distinct().sortedBy { it.from }
    return windows.fold(mutableListOf()) { merged, window ->
        val previous = merged.lastOrNull()
        if (previous != null && window.from <= previous.to) merged[merged.lastIndex] = previous.copy(to = maxOf(previous.to, window.to))
        else merged += window
        merged
    }
}

/** Preserve the cheap current guide, then read only missing, bounded exact-fixture windows. */
internal suspend fun hockeyEpgWithLookahead(upcoming: List<EpgProgrammeEntity>, windows: List<HockeyEpgWindow>,
    readWindow: suspend (Long, Long) -> List<EpgProgrammeEntity>): HockeyEpgRead {
    if (upcoming.size < 128) return HockeyEpgRead(upcoming)
    val lastStart = upcoming.maxOf { it.startMs }
    val extra = mutableListOf<EpgProgrammeEntity>()
    var truncated = windows.size > 12
    for (window in windows.take(12).filter { it.to > lastStart }) {
        currentCoroutineContext().ensureActive()
        val rows = readWindow(window.from, window.to)
        truncated = truncated || rows.size >= 128
        extra += rows
    }
    return HockeyEpgRead((upcoming + extra).distinct(), truncated)
}
