package tv.own.owntv.player

import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LivePlaybackDiagnosticsTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun pinnedCoreStalkerStartReturnsBeforeActualEngineAcknowledgement() = runTest {
        val source = tv.own.owntv.core.database.entity.SourceEntity(id = 1, name = "Synthetic", type = tv.own.owntv.core.model.SourceType.STALKER, url = "http://example.invalid/portal")
        val channel = tv.own.owntv.core.database.entity.ChannelEntity(id = 1, sourceId = 1, name = "SVT1", streamUrl = "auto /media/1")
        val response = CompletableDeferred<String?>()
        val resolve: suspend () -> String? = { response.await() }
        val delegate = Proxy.newProxyInstance(LiveTuneController.Host::class.java.classLoader, arrayOf(LiveTuneController.Host::class.java)) { _, method, args ->
            when (method.name) {
                "resolve" -> {
                    @Suppress("UNCHECKED_CAST")
                    resolve.startCoroutineUninterceptedOrReturn(args!!.last() as Continuation<String?>)
                }
                "needsResolve" -> true
                "globalPreference" -> tv.own.owntv.core.player.EnginePreference.EXO_FIRST
                "globalBudgetSecs" -> 0
                "nowMs" -> testScheduler.currentTime
                "meta" -> MediaMeta()
                else -> null
            }
        } as LiveTuneController.Host
        var dispatched = 0
        val engines = Proxy.newProxyInstance(LiveEngines::class.java.classLoader, arrayOf(LiveEngines::class.java)) { _, method, _ ->
            when (method.name) { "exoPlay" -> { dispatched++; Unit }; else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null }
        } as LiveEngines
        val accepted = CompletableDeferred<Boolean>()
        val host = DiagnosticLiveHost(delegate, started = { accepted.complete(true) }, resolutionFailure = { _, _ -> { accepted.complete(false); Unit } })
        val core = LiveTuneController(backgroundScope, engines, host)
        core.start(channel, source)
        runCurrent()
        // Regression: checking a revision immediately after start() falsely rejected this valid tune.
        assertEquals(0, dispatched); assertFalse(accepted.isCompleted)
        response.complete("http://example.invalid/synthetic.ts"); runCurrent()
        assertTrue(accepted.await()); assertEquals(1, dispatched)
        core.stop()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun failedResolutionRejectsOnlyTheRequestCapturedBeforeSuspension() = runTest {
        val response = CompletableDeferred<String?>()
        val resolve: suspend () -> String? = { response.await() }
        val delegate = Proxy.newProxyInstance(LiveTuneController.Host::class.java.classLoader, arrayOf(LiveTuneController.Host::class.java)) { _, method, args ->
            if (method.name == "resolve") {
                @Suppress("UNCHECKED_CAST")
                resolve.startCoroutineUninterceptedOrReturn(args!!.last() as Continuation<String?>)
            } else null
        } as LiveTuneController.Host
        val old = CompletableDeferred<Boolean>(); val newer = CompletableDeferred<Boolean>()
        var current = old
        val host = DiagnosticLiveHost(delegate, resolutionFailure = { _, _ ->
            val captured = current
            val fail: () -> Unit = { captured.complete(false); Unit }
            fail
        })
        val source = tv.own.owntv.core.database.entity.SourceEntity(id = 1, name = "Synthetic", type = tv.own.owntv.core.model.SourceType.STALKER, url = "http://example.invalid/portal")
        val pending = async { host.resolve(source, "synthetic command") }; runCurrent()
        current = newer; response.complete(null); runCurrent()
        assertNull(pending.await()); assertFalse(old.await()); assertFalse(newer.isCompleted)
    }

    @Test fun traceIsBoundedAndRecordsMonotonicEventOrder() {
        val trace = LivePlaybackTrace()
        repeat(100) { trace.record(it.toLong(), LivePlaybackTrace.Kind.ENGINE_STARTED) }
        val lines = trace.report().lines()
        assertEquals(32, lines.size)
        assertTrue(lines.first().contains("elapsedMs=68 "))
        assertTrue(lines.last().contains("elapsedMs=99 "))
    }

    @Test fun providerTextNeverSurvivesClassificationOrExport() {
        val secret = "https://private.example/live/user/password?token=secret"
        val trace = LivePlaybackTrace()
        trace.record(42, LivePlaybackTrace.Kind.FALLBACK, LivePlaybackTrace.Engine.EXOPLAYER,
            LivePlaybackTrace.cause("'$secret': mpv + TS — no decodable audio track"))
        assertTrue(trace.report().contains("AUDIO_UNDECODABLE"))
        assertEquals(LivePlaybackTrace.Cause.OTHER, LivePlaybackTrace.cause(secret))
        for (privateText in listOf("https", "private.example", "password", "token", "secret")) {
            assertFalse(trace.report().contains(privateText))
        }
    }

    @Test fun observerPreservesCoreCallbackArgumentsAndLifecycleOrder() {
        val calls = mutableListOf<String>()
        var receivedDetail: String? = null
        val delegate = Proxy.newProxyInstance(LiveTuneController.Host::class.java.classLoader,
            arrayOf(LiveTuneController.Host::class.java)) { _, method, args ->
            calls += method.name
            if (method.name == "recordLadderEvent") {
                assertEquals(true, args!![0])
                assertEquals(PlayerFailureReason.LIVE_FALLBACK, args[1])
                receivedDetail = args[2] as String
            }
            if (method.name == "nowMs") 123L else null
        } as LiveTuneController.Host
        val host = DiagnosticLiveHost(delegate)
        val detail = "private-channel: mpv + TS — no decodable audio track"
        host.recordLadderEvent(true, PlayerFailureReason.LIVE_FALLBACK, detail)
        host.onBackToLiveEdge()
        host.onEngineStarted()
        assertEquals(detail, receivedDetail)
        assertEquals(listOf("nowMs", "recordLadderEvent", "nowMs", "onBackToLiveEdge", "nowMs", "onEngineStarted"), calls)
        assertFalse(LivePlaybackDiagnostics.report().contains("private-channel"))
    }
}
