package tv.own.owntv.player

/** In-memory bounded trace: only enum values and monotonic timings, never provider text or URLs. */
internal class LivePlaybackTrace {
    enum class Kind { ENGINE_STARTED, HANDOVER, FALLBACK, EXHAUSTED }
    enum class Engine { EXOPLAYER, MPV, UNSPECIFIED }
    enum class Cause { AUDIO_UNDECODABLE, OTHER, NONE }
    data class Event(val elapsedMs: Long, val kind: Kind, val engine: Engine, val cause: Cause)
    private val events = ArrayDeque<Event>()

    @Synchronized fun record(elapsedMs: Long, kind: Kind, engine: Engine = Engine.UNSPECIFIED, cause: Cause = Cause.NONE) {
        events.addLast(Event(elapsedMs.coerceAtLeast(0), kind, engine, cause))
        while (events.size > 32) events.removeFirst()
    }

    @Synchronized fun report(): String = events.joinToString("\n") {
        "LIVE_TRACE elapsedMs=${it.elapsedMs} event=${it.kind} engine=${it.engine} cause=${it.cause}"
    }

    companion object {
        // Only classify a known Core suffix. Never retain the input, an exception message, or a channel name.
        fun cause(detail: String) = if (detail.endsWith(" — no decodable audio track")) Cause.AUDIO_UNDECODABLE else Cause.OTHER
    }
}

internal object LivePlaybackDiagnostics {
    val trace = LivePlaybackTrace()
    fun report() = trace.report() + "\n" + LiveEntryDiagnostics.trace.report()
}

/** Observe existing Core callbacks; delegate every playback decision and lifecycle call unchanged. */
internal class DiagnosticLiveHost(private val delegate: LiveTuneController.Host, private val started: () -> Unit = {}, private val exhausted: () -> Unit = {}, private val fallback: () -> Unit = {},
    private val resolutionFailure: (tv.own.owntv.core.database.entity.SourceEntity, String) -> (() -> Unit) = { _, _ -> {} }) : LiveTuneController.Host by delegate {
    override suspend fun resolve(source: tv.own.owntv.core.database.entity.SourceEntity, cmd: String): String? {
        // Capture the request before suspension; a failed old lookup cannot reject a newer tune.
        val failed = resolutionFailure(source, cmd)
        return delegate.resolve(source, cmd).also { if (it == null) failed() }
    }

    override fun recordLadderEvent(onExo: Boolean, reason: PlayerFailureReason, detail: String) {
        if (reason == PlayerFailureReason.LIVE_FALLBACK || reason == PlayerFailureReason.LIVE_NO_FALLBACK) {
            LivePlaybackDiagnostics.trace.record(
                delegate.nowMs(),
                if (reason == PlayerFailureReason.LIVE_FALLBACK) LivePlaybackTrace.Kind.FALLBACK else LivePlaybackTrace.Kind.EXHAUSTED,
                if (onExo) LivePlaybackTrace.Engine.EXOPLAYER else LivePlaybackTrace.Engine.MPV,
                LivePlaybackTrace.cause(detail),
            )
        }
        if (reason == PlayerFailureReason.LIVE_NO_FALLBACK) exhausted()
        if (reason == PlayerFailureReason.LIVE_FALLBACK) fallback()
        delegate.recordLadderEvent(onExo, reason, detail)
    }

    override fun onEngineStarted() {
        LivePlaybackDiagnostics.trace.record(delegate.nowMs(), LivePlaybackTrace.Kind.ENGINE_STARTED)
        started()
        delegate.onEngineStarted()
    }

    override fun onBackToLiveEdge() {
        LivePlaybackDiagnostics.trace.record(delegate.nowMs(), LivePlaybackTrace.Kind.HANDOVER)
        delegate.onBackToLiveEdge()
    }
}
