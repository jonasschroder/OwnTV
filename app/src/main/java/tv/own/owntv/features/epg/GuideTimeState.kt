package tv.own.owntv.features.epg

/** Absolute cursor shared by the timeline and details. Elapsed time, not wall time, measures idleness. */
internal data class GuideTimeState(
    val cursor: Long,
    val followsNow: Boolean = true,
    val lastInteractionElapsed: Long,
) {
    fun interacted(elapsed: Long) = copy(lastInteractionElapsed = elapsed)
    fun browse(time: Long, elapsed: Long) = GuideTimeState(time, false, elapsed)
    fun now(wall: Long, elapsed: Long) = GuideTimeState(wall, true, elapsed)

    /** An active manual browse survives midnight and short resumes. A long idle returns to live time. */
    fun refreshed(wall: Long, elapsed: Long): GuideTimeState = when {
        followsNow -> copy(cursor = wall)
        elapsed - lastInteractionElapsed >= IDLE_RESET_MS -> now(wall, elapsed)
        else -> this
    }

    companion object { const val IDLE_RESET_MS = 30L * 60 * 1000 }
}

/** Never retain a programme object after its channel/cursor or backing row changes. */
internal fun programmeAt(programmes: List<tv.own.owntv.core.database.entity.EpgProgrammeEntity>?, time: Long) =
    programmes?.firstOrNull { time in it.startMs until it.stopMs }

/** A data refresh may reorder channels without moving the keyed Compose focus node. */
internal fun guideRowIndex(
    channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    focusedChannelId: Long?,
    fallbackIndex: Int,
): Int = channels.indexOfFirst { it.id == focusedChannelId }.takeIf { it >= 0 }
    ?: fallbackIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))

/** Full descriptions come from stored time; keep the row's already-offset display interval. */
internal fun guideProgrammeDetails(
    summary: tv.own.owntv.core.database.entity.EpgProgrammeEntity?,
    full: tv.own.owntv.core.database.entity.EpgProgrammeEntity?,
): tv.own.owntv.core.database.entity.EpgProgrammeEntity? = summary?.let { row ->
    full?.takeIf { it.id == row.id }?.copy(startMs = row.startMs, stopMs = row.stopMs) ?: row
}
