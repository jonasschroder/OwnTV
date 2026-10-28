package tv.own.owntv.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveEntryGateTest {
    private class Harness(scope: TestScope) {
        var stops = 0; var started: String? = null
        val trace = LiveEntryTrace()
        val gate = LiveEntryGate(scope.backgroundScope, {}, { stops++; started = null }, { scope.testScheduler.currentTime }, trace)
    }
    @Test fun everyOriginProtectsTheSameChannelFromBrowseDisposalBeforeAndAfterAcceptance() = runTest {
        for (origin in LiveEntryOrigin.entries) for (name in listOf("SVT1", "SVT2", "TV4")) {
            val h = Harness(this)
            val loading = async { h.gate.enter(origin, when(name) { "SVT1" -> LiveLogicalChannel.SVT1; "SVT2" -> LiveLogicalChannel.SVT2; else -> LiveLogicalChannel.OTHER },
                { delay(100); name }) { h.started = it; LiveEntryOutcome.IN_APP } }
            runCurrent(); assertFalse(h.gate.stopPreview()); assertEquals(0, h.stops)
            advanceTimeBy(100); runCurrent(); assertEquals(LiveEntryOutcome.IN_APP, loading.await())
            assertFalse(h.gate.stopPreview()); assertEquals(name, h.started)
            h.gate.release(); assertTrue(h.gate.stopPreview()); assertNull(h.started)
        }
    }
    @Test fun rapidOkAndScreenSwitchesCannotPublishOlderRequestsOrStopTheWinner() = runTest {
        val h = Harness(this)
        val old = async { h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { delay(1000); "old" }) { h.started = it; LiveEntryOutcome.IN_APP } }
        runCurrent()
        val latest = async { h.gate.enter(LiveEntryOrigin.TV_GRID, LiveLogicalChannel.SVT2, { "new" }) { h.started = it; LiveEntryOutcome.IN_APP } }
        runCurrent(); advanceTimeBy(2000); runCurrent()
        assertEquals(LiveEntryOutcome.SUPERSEDED, old.await()); assertEquals(LiveEntryOutcome.IN_APP, latest.await())
        assertEquals("new", h.started); assertEquals(0, h.stops)
    }
    @Test fun rejectionFailedInitializationAndExternalFailureNeverCountAsAccepted() = runTest {
        val h = Harness(this)
        assertEquals(LiveEntryOutcome.UNAVAILABLE, h.gate.enter<String>(LiveEntryOrigin.GUIDE, LiveLogicalChannel.SVT1, { null }) { error("unauthorized") })
        assertEquals(LiveEntryOutcome.UNAVAILABLE, h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT2, { "channel" }) { error("https://private.invalid/user/password?token=secret") })
        assertEquals(LiveEntryOutcome.UNAVAILABLE, h.gate.enter(LiveEntryOrigin.SPORT, LiveLogicalChannel.OTHER, { "channel" }) { LiveEntryOutcome.UNAVAILABLE })
        assertFalse(h.gate.protectsPlayback); assertFalse(h.trace.report().contains("ACCEPTED"))
        for (secret in listOf("https", "password", "token", "secret")) assertFalse(h.trace.report().contains(secret))
        assertEquals(LiveEntryOutcome.EXTERNAL, h.gate.enter(LiveEntryOrigin.SPORT, LiveLogicalChannel.OTHER, { "channel" }) { LiveEntryOutcome.EXTERNAL })
        assertFalse(h.gate.protectsPlayback)
    }
    @Test fun backAndProfileChangeCancelSlowInitializationAndAllowASecondAttempt() = runTest {
        val h = Harness(this)
        val first = async { h.gate.enter(LiveEntryOrigin.TV_GRID, LiveLogicalChannel.SVT1, { delay(1000); "stale-profile" }) { h.started = it; LiveEntryOutcome.IN_APP } }
        runCurrent(); h.gate.release(); advanceTimeBy(1000); runCurrent()
        assertEquals(LiveEntryOutcome.SUPERSEDED, first.await()); assertNull(h.started)
        assertEquals(LiveEntryOutcome.IN_APP, h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { "fresh-profile" }) { h.started = it; LiveEntryOutcome.IN_APP })
        assertEquals("fresh-profile", h.started)
    }
    @Test fun callerDisposalCancelsAnUnacceptedRequestButNotACompletedPromotion() = runTest {
        val h = Harness(this)
        val caller = launch { h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { delay(1000); "late" }) { h.started = it; LiveEntryOutcome.IN_APP } }
        runCurrent(); caller.cancel(); runCurrent(); advanceTimeBy(1000); runCurrent()
        assertNull(h.started); assertFalse(h.gate.protectsPlayback)
        val accepted = launch { h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { "accepted" }) { h.started = it; LiveEntryOutcome.IN_APP } }
        runCurrent(); accepted.cancel(); runCurrent(); assertEquals("accepted", h.started)
    }
    @Test fun disposalAfterEngineAcceptanceButBeforeCallerResumeMustNotLeaveAnOrphanStream() = runTest {
        val h = Harness(this)
        lateinit var caller: Job
        caller = launch {
            h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { "channel" }) {
                h.started = it; caller.cancel(); LiveEntryOutcome.IN_APP
            }
        }
        runCurrent()
        assertTrue(caller.isCancelled); assertNull(h.started); assertFalse(h.gate.protectsPlayback)
    }
    @Test fun exitDiagnosticRetainsTheClosedRequestIdentity() = runTest {
        val h = Harness(this)
        h.gate.enter(LiveEntryOrigin.HOME, LiveLogicalChannel.SVT1, { "channel" }) { LiveEntryOutcome.IN_APP }
        val id = h.gate.requestId; h.gate.release()
        assertTrue(h.trace.report().lines().single { it.contains("event=EXITED") }.contains("request=$id "))
    }
    @Test fun legacyFullscreenDisposalActuallyCancelsThePinnedCoreTuneJob() = runTest {
        // Same concrete Core launch/stop operations as ensurePlaying + SwedishLiveScreen.onDispose.
        // Delayed source lookup isolates the race from codecs, network and SVT content.
        val host = java.lang.reflect.Proxy.newProxyInstance(LiveTuneController.Host::class.java.classLoader,
            arrayOf(LiveTuneController.Host::class.java)) { _, method, _ -> if (method.name == "nowMs") testScheduler.currentTime else null } as LiveTuneController.Host
        val engines = java.lang.reflect.Proxy.newProxyInstance(LiveEngines::class.java.classLoader,
            arrayOf(LiveEngines::class.java)) { _, _, _ -> null } as LiveEngines
        val core = LiveTuneController(backgroundScope, engines, host)
        var dispatched = false
        val tune = core.launch { delay(100); dispatched = true }
        runCurrent(); core.stop(); advanceTimeBy(100); runCurrent()
        assertTrue(tune.isCancelled); assertFalse(dispatched)
    }
}
