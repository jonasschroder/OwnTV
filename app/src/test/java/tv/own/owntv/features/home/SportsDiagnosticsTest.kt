package tv.own.owntv.features.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SportsDiagnosticsTest {
    @Test fun exportKeepsExactFramesAndCausesButDropsMessagesAndCredentials() {
        val raw = """
            java.lang.IllegalStateException: https://provider.invalid/user/password?token=secret
                at tv.own.owntv.features.home.MinTvCompanionsKt.MatchScorecard(MinTvCompanions.kt:613)
            Caused by: java.io.IOException: username=private
                at tv.own.owntv.features.home.Sh l(http://private)
            token=secret
        """.trimIndent()
        val safe = SportsDiagnostics.sanitizedStack(raw)
        assertTrue(safe.contains("MinTvCompanions.kt:613"))
        assertTrue(safe.contains("java.io.IOException"))
        listOf("https", "password", "secret", "username", "private").forEach { assertFalse(safe.contains(it)) }
    }
    @Test fun contextIsBoundedAndContainsNoUserIdentifiers() {
        repeat(100) { SportsDiagnostics.record(SportsDiagnostics.Event.HOME) }
        assertEquals(48, SportsDiagnostics.snapshot().lines().size)
        assertTrue(SportsDiagnostics.snapshot().lines().all { it.matches(Regex("[0-9]+ HOME")) })
    }
    @Test fun uiPublicationFailureIsRecordedWithoutCrashingOrMisclassifyingTheSource() = runTest {
        val error = IllegalStateException("Sensitive programming failure")
        val states = mutableListOf<SportsLoadState>()
        val result = fetchSports(1L, HockeyStage.SCHEDULE, { false }, { "verified" }, { throw error }, { states += it })
        assertEquals(HockeyStage.PUBLICATION, result.issue!!.stage)
        assertEquals(HockeyFailureKind.CACHE, result.issue!!.kind)
        assertFalse(states.last().loading)
        assertTrue(SportsDiagnostics.snapshot().contains("java.lang.IllegalStateException"))
        assertFalse(SportsDiagnostics.snapshot().contains("Sensitive"))
    }
}
