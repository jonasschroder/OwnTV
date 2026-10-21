package tv.own.owntv.player

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LivePlaybackDiagnosticsTest {
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
