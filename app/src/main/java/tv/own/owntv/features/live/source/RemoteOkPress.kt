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

/** Destination-owned cancellation, including navigation consumed by an ancestor before the button. */
internal class SourcePressCancellation {
    private var owner: Any? = null
    private var pending: (() -> Unit)? = null
    fun register(owner: Any, cancel: () -> Unit) {
        if (this.owner !== owner) this.cancel()
        this.owner = owner; pending = cancel
    }
    fun clear(owner: Any) { if (this.owner === owner) { this.owner = null; pending = null } }
    fun cancel() {
        val action = pending
        owner = null; pending = null
        action?.invoke()
    }
}
