package tv.own.owntv.features.epg

/** Process-local click guard. Elapsed time avoids wall-clock changes; no stored preference changes. */
internal class ManualEpgThrottle(private val intervalMs: Long = 60_000) {
    private var last: Long? = null
    var running = false
        private set
    fun begin(elapsed: Long): Boolean {
        if (running || last?.let { elapsed - it in 0 until intervalMs } == true) return false
        running = true; last = elapsed
        return true
    }
    fun finish() { running = false }
}
internal enum class ManualEpgResult { IDLE, RUNNING, SUCCESS, FAILED, EMPTY, PROFILE_CHANGED }
internal data class ManualEpgStatus(val profile: Long? = null, val result: ManualEpgResult = ManualEpgResult.IDLE,
    val lastSuccess: Long? = null)
