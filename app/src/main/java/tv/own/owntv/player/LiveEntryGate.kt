package tv.own.owntv.player

import kotlinx.coroutines.*

/** Main-thread ownership above Core, not another player/fallback engine. */
internal class LiveEntryGate(private val scope: CoroutineScope, private val cancelTune: () -> Unit,
    private val stop: () -> Unit, private val clock: () -> Long, private val trace: LiveEntryTrace, private val onFailure: (Long) -> Unit = {}) {
    private var serial = 0L
    private var pending: Deferred<LiveEntryOutcome>? = null
    var protectsPlayback = false; private set
    val requestId get() = serial

    suspend fun <T : Any> enter(origin: LiveEntryOrigin, logical: LiveLogicalChannel,
        prepare: suspend () -> T?, activate: suspend (T) -> LiveEntryOutcome): LiveEntryOutcome {
        val id = ++serial
        pending?.cancel(); cancelTune()
        protectsPlayback = true // before any IO or disposal of the launching screen
        trace.record(id, clock(), origin, logical, LiveEntryEvent.REQUESTED)
        val work = scope.async(start = CoroutineStart.LAZY) {
            try {
                val target = prepare()
                currentCoroutineContext().ensureActive()
                if (id != serial) return@async LiveEntryOutcome.SUPERSEDED
                if (target == null) {
                    protectsPlayback = false; stop()
                    trace.record(id, clock(), event = LiveEntryEvent.UNAVAILABLE); onFailure(id)
                    return@async LiveEntryOutcome.UNAVAILABLE
                }
                trace.record(id, clock(), event = LiveEntryEvent.SOURCE_VALIDATED)
                val outcome = activate(target)
                currentCoroutineContext().ensureActive()
                if (id != serial) return@async LiveEntryOutcome.SUPERSEDED
                protectsPlayback = outcome == LiveEntryOutcome.IN_APP
                if (outcome == LiveEntryOutcome.UNAVAILABLE) { stop(); onFailure(id) }
                trace.record(id, clock(), event = when (outcome) {
                    LiveEntryOutcome.IN_APP -> LiveEntryEvent.ACCEPTED
                    LiveEntryOutcome.EXTERNAL -> LiveEntryEvent.EXTERNAL
                    else -> LiveEntryEvent.UNAVAILABLE
                })
                outcome
            } catch (cancelled: CancellationException) {
                trace.record(id, clock(), event = if (id == serial) LiveEntryEvent.CANCELLED else LiveEntryEvent.SUPERSEDED)
                if (id == serial) { protectsPlayback = false; stop() }
                throw cancelled
            } catch (_: Exception) {
                if (id == serial) { protectsPlayback = false; stop() }
                trace.record(id, clock(), event = LiveEntryEvent.INITIALIZATION_FAILED)
                if (id == serial) onFailure(id)
                LiveEntryOutcome.UNAVAILABLE
            }
        }
        pending = work; work.start()
        return try { val outcome = work.await(); if (id == serial) outcome else LiveEntryOutcome.SUPERSEDED } catch (cancelled: CancellationException) {
            // A disposed caller must not leave an orphan source lookup playing later. A completed
            // accepted request, however, belongs to fullscreen and survives disposal of its caller.
            if (!currentCoroutineContext().isActive) {
                if (id == serial) {
                    ++serial; work.cancel(); cancelTune(); protectsPlayback = false; stop()
                    trace.record(id, clock(), event = LiveEntryEvent.CANCELLED)
                }
                throw cancelled
            }
            LiveEntryOutcome.SUPERSEDED
        }
    }

    fun stopPreview(): Boolean {
        if (protectsPlayback) return false
        stop(); return true
    }
    fun release() {
        val closed = serial
        ++serial; pending?.cancel(); pending = null; cancelTune(); protectsPlayback = false
        trace.record(closed, clock(), event = LiveEntryEvent.EXITED)
    }
}

internal enum class LiveEntryOutcome { IN_APP, EXTERNAL, UNAVAILABLE, SUPERSEDED }
internal enum class LiveEntryOrigin { HOME, TV_GRID, SOURCE_PICKER, GUIDE, SPORT, HISTORY, CONTINUE, ZAP, NUMBER, PREVIOUS, DEEP_LINK, MULTIVIEW }
internal enum class LiveLogicalChannel { SVT1, SVT2, SVT1_VARMLAND, SVT2_VARMLAND, OTHER }
internal fun liveLogicalChannel(identity: String?): LiveLogicalChannel = when (identity) {
    "svt1" -> LiveLogicalChannel.SVT1
    "svt2" -> LiveLogicalChannel.SVT2
    "svt1varmland" -> LiveLogicalChannel.SVT1_VARMLAND
    "svt2varmland" -> LiveLogicalChannel.SVT2_VARMLAND
    else -> LiveLogicalChannel.OTHER
}
internal enum class LiveEntryEvent { SELECTION_REQUESTED, SELECTION_BUSY, SELECTION_CONTEXT_CHANGED, SELECTION_MISSING, SELECTION_METADATA_CHANGED, SELECTION_FAILED, SELECTION_DISPATCHED, CHANNEL_PREPARING, CHANNEL_STALE, REQUESTED, SOURCE_VALIDATED, ACCEPTED, OPENED_EXO, OPENED_MPV, FALLBACK, EXHAUSTED, EXTERNAL, UNAVAILABLE, INITIALIZATION_FAILED, CANCELLED, SUPERSEDED, EXITED }

/** Only finite enums, request sequence and monotonic timing: never provider/profile strings. */
internal class LiveEntryTrace {
    private val lines = ArrayDeque<String>()
    @Synchronized fun record(id: Long, at: Long, origin: LiveEntryOrigin? = null,
        logical: LiveLogicalChannel? = null, event: LiveEntryEvent) {
        lines.addLast("LIVE_ENTRY request=$id elapsedMs=${at.coerceAtLeast(0)} origin=$origin logical=$logical event=$event")
        while (lines.size > 64) lines.removeFirst()
    }
    @Synchronized fun report() = lines.joinToString("\n")
}

internal object LiveEntryDiagnostics { val trace = LiveEntryTrace() }
