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
