package tv.own.owntv.player

/** Observe actual Core outcomes; isPlaying/engine command dispatch is not decoded-picture proof. */
internal class EntryDiagnosticEngines(private val delegate: LiveEngines, private val current: () -> Long,
    private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) : LiveEngines by delegate {
    override suspend fun watchExo(channelName: String, stillOurs: () -> Boolean, handOver: suspend (String) -> Unit,
        onOpened: () -> Unit, postponeDeadline: (Long) -> Unit, log: (String) -> Unit) {
        val id = current()
        delegate.watchExo(channelName, stillOurs, handOver, {
            if (current() == id && stillOurs()) LiveEntryDiagnostics.trace.record(id,
                now(), event = LiveEntryEvent.OPENED_EXO)
            onOpened()
        }, postponeDeadline, log)
    }
    override suspend fun awaitMpvOutcome(timeoutMs: Long): MpvOutcome? {
        val id = current()
        return delegate.awaitMpvOutcome(timeoutMs).also { outcome ->
            if (outcome?.opened == true && current() == id) LiveEntryDiagnostics.trace.record(id,
                now(), event = LiveEntryEvent.OPENED_MPV)
        }
    }
}
