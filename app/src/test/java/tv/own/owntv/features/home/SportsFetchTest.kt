package tv.own.owntv.features.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SportsFetchTest {
    private val now = 1_791_720_000_000L

    @Test fun successfulAndEmptyResponsesAlwaysFinishLoading() = runTest {
        for (value in listOf("verified", null)) {
            val states = mutableListOf<SportsLoadState>()
            var accepted: String? = null
            val final = fetchSports(now, HockeyStage.SCHEDULE, { false }, { value },
                { accepted = it; false }, { states += it })
            assertTrue(states.first().loading)
            assertFalse(states.last().loading)
            assertEquals(value, accepted)
            assertNull(final.issue)
        }
    }

    @Test fun slowResponsesHaveDeadlineAndDoNotPublishLateData() = runTest {
        val states = mutableListOf<SportsLoadState>()
        var accepted = false
        val final = fetchSports(now, HockeyStage.SCHEDULE, { false }, { delay(60_000); "late" },
            { accepted = true; false }, { states += it }, timeoutMillis = 200)
        assertFalse(accepted)
        assertFalse(states.last().loading)
        assertEquals(HockeyFailureKind.TIMEOUT, final.issue!!.kind)
        assertEquals(HockeyStage.SCHEDULE, final.issue!!.stage)
        assertFalse(retryReady(final.issue, now + 1000))
        assertTrue(retryReady(final.issue, now + 120_000))
    }

    @Test fun leavingAndReopeningCancelsWorkButKeepsExistingCacheAndCanLoadAgain() = runTest {
        val entered = CompletableDeferred<Unit>()
        val states = mutableListOf<SportsLoadState>()
        var cancelled = false
        val work = launch {
            fetchSports<String>(now, HockeyStage.TABLE, { true }, {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }, { error("Cancelled data was published") }, { states += it })
        }
        entered.await(); work.cancelAndJoin()
        assertTrue(cancelled)
        assertFalse(states.last().loading)
        assertTrue(states.last().cached)
        assertNull(states.last().issue) // Back/background is not a server failure.
        val next = fetchSports(now, HockeyStage.TABLE, { true }, { "verified" }, { true }, { states += it })
        assertFalse(next.loading)
        assertTrue(next.cached)
    }

    @Test fun parentDeadlineIsCancellationRatherThanInventedTimeoutFailure() = runTest {
        val states = mutableListOf<SportsLoadState>()
        try {
            kotlinx.coroutines.withTimeout(100) {
                fetchSports<String>(now, HockeyStage.SCHEDULE, { false }, { awaitCancellation() },
                    { false }, { states += it }, timeoutMillis = 1000)
            }
            fail("Parent timeout did not cancel")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        assertFalse(states.last().loading)
        assertNull(states.last().issue)
    }

    @Test fun cacheFailureAndHttp200InvalidFormatBecomeTerminalErrorsWithoutDroppingCache() = runTest {
        for (error in listOf(IllegalStateException("Synthetic storage failure"),
            HockeyDataException(HockeyIssue(HockeyFailureKind.FORMAT, HockeyStage.SCHEDULE, now,
                httpCode = 200, retryAt = now + 600_000, detail = HockeyFormatReason.COMPETITION)))) {
            val states = mutableListOf<SportsLoadState>()
            val final = fetchSports<String>(now, HockeyStage.SCHEDULE, { true }, { throw error },
                { false }, { states += it })
            assertFalse(states.last().loading)
            assertTrue(final.cached)
            assertNotNull(final.issue)
            assertFalse(retryReady(final.issue, now))
        }
    }

    @Test fun scopeRemainsTheSameAcrossHomeAndMatchcenterButNeverRequestsWhenHidden() {
        val selected = SportPreferences(listOf(SportsCatalog.farjestadId))
        val home = sportsRequestCompetitions(selected, true, true, false, true)
        val center = sportsRequestCompetitions(selected, true, false, true, true, SportsCatalog.shl)
        assertEquals(home, center) // Effect keys now use this scope, not the screen boolean.
        assertTrue(sportsRequestCompetitions(selected, false, true, true, true).isEmpty())
        assertTrue(sportsRequestCompetitions(selected, true, false, false, true).isEmpty())
        assertTrue(sportsRequestCompetitions(selected, true, true, true, false).isEmpty())
        assertFalse(SportsLoadState().loading)
    }
}
