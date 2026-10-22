package tv.own.owntv.features.live.source

/** One physical OK cycle, identified by Android's monotonic downTime. Cancel never becomes Play. */
internal class RemoteOkPress {
    private var token: Long? = null
    private var pending = false
    fun down(downTime: Long, repeat: Int): Boolean {
        if (repeat != 0 || token == downTime) return false
        token = downTime; pending = true
        return true
    }
    fun held(downTime: Long): Boolean {
        if (token != downTime || !pending) return false
        pending = false
        return true
    }
    fun up(downTime: Long): Boolean {
        val short = token == downTime && pending
        if (token == downTime) { token = null; pending = false }
        return short
    }
    fun cancel() { pending = false }
}
