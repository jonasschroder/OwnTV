package tv.own.owntv.player

import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EntryDiagnosticEnginesTest {
    @Test fun onlyActualExoOpenedCallbackIsMarkedAndProviderTextIsNotRetained() = runTest {
        var called = 0
        val delegate = Proxy.newProxyInstance(LiveEngines::class.java.classLoader, arrayOf(LiveEngines::class.java)) { _, method, args ->
            if (method.name == "watchExo") { (args!![3] as Function0<*>).invoke(); Unit } else null
        } as LiveEngines
        val observer = EntryDiagnosticEngines(delegate, { 88141 }, { 10 })
        observer.watchExo("private channel https://private.invalid/password", { true }, {}, { called++ }, {}, {})
        assertEquals(1, called)
        val line = LiveEntryDiagnostics.trace.report().lines().single { it.contains("request=88141 ") }
        assertTrue(line.contains("OPENED_EXO")); assertFalse(line.contains("private")); assertFalse(line.contains("password"))
    }
    @Test fun mpvOutcomeIsDelegatedAndFailureOrSupersededOpenIsNotSuccess() = runTest {
        var outcome = MpvOutcome(false, "private failure")
        var current = 88142L
        var supersede = false
        val delegate = Proxy.newProxyInstance(LiveEngines::class.java.classLoader, arrayOf(LiveEngines::class.java)) { _, method, _ ->
            if (method.name == "awaitMpvOutcome") { if (supersede) current++; outcome } else null
        } as LiveEngines
        val observer = EntryDiagnosticEngines(delegate, { current }, { 20 })
        assertSame(outcome, observer.awaitMpvOutcome(100))
        assertFalse(LiveEntryDiagnostics.trace.report().contains("request=88142 "))
        outcome = MpvOutcome(true, null)
        assertSame(outcome, observer.awaitMpvOutcome(100))
        assertEquals(1, LiveEntryDiagnostics.trace.report().lines().count { it.contains("request=88142 ") && it.contains("OPENED_MPV") })
        supersede = true; observer.awaitMpvOutcome(100)
        assertFalse(LiveEntryDiagnostics.trace.report().contains("request=88143 "))
    }
}
