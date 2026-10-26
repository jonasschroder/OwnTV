package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExperimentalBroadcastReaderTest {
    private val fixture = BroadcastFixture("SHL", "Färjestad BK", "Malmö Redhawks", LocalDate.parse("2026-10-10"), Instant.parse("2026-10-10T13:15:00Z"))
    private val path = "/match/farjestad-bk-malmo-redhawks-1896181"
    private val html = javaClass.getResource("/shl-broadcast/tvmatchen-match.html")!!.readText()
    private var now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli()
    private var enabled = true
    private var status = 200
    private var robot = "User-agent: *\nDisallow: /api\n"
    private val calls = mutableListOf<String>()
    private val state = BroadcastReadState()
    private var saved = 0
    private val diagnostics = mutableListOf<BroadcastLookupStatus>()
    private fun reader(pageReader: BroadcastPageReader = BroadcastPageReader { url, _ ->
        calls += url
        status to when (url) { "/robots.txt" -> robot; "/page/anvandarvillkor" -> "reviewed terms";
            "/ishockey/shl" -> javaClass.getResource("/shl-broadcast/tvmatchen-listing.html")!!.readText(); else -> html }
    }) = ExperimentalBroadcastReader(pageReader, { enabled }, state, { saved++ }, { now }, { it == "reviewed terms" }, diagnostics::add)

    @Test fun disabledMeansNoRequestsIncludingRobotsAndCachedAnswers() = runTest {
        enabled = false; assertNull(reader().assignment(fixture)); assertTrue(calls.isEmpty()); assertEquals(0, saved)
    }
    @Test fun verifiedResultCachedWithoutRequestsOrParsingOnEveryOpening() = runTest {
        assertNotNull(reader().assignment(fixture)); assertEquals(4, calls.size)
        assertNotNull(reader().assignment(fixture)); assertEquals(4, calls.size)
    }
    @Test fun deniedStatusesPersistStopWithNoAutomaticRetry() = runTest {
        for (code in listOf(401, 403, 429)) {
            now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli()
            state.blocked = false; state.retryAt = 0; status = code
            assertNull(reader().assignment(fixture)); assertTrue(state.blocked)
            val count = calls.size; status = 200; now += 24 * 60 * 60_000L
            assertNull(reader().assignment(fixture)); assertEquals(count, calls.size)
        }
    }
    @Test fun robotsDenialStopsBeforeListingOrFixtureAndNeverCallsApi() = runTest {
        robot = "User-agent: *\nDisallow: /ishockey/\n"
        assertNull(reader().assignment(fixture)); assertTrue(state.blocked); assertEquals(listOf("/robots.txt"), calls)
    }
    @Test fun changedTermsStopBeforeListing() = runTest {
        val reader = ExperimentalBroadcastReader(BroadcastPageReader { url, _ -> calls += url; 200 to robot },
            { true }, state, {}, { now }, { false })
        assertNull(reader.assignment(fixture)); assertTrue(state.blocked)
        assertEquals(listOf("/robots.txt", "/page/anvandarvillkor"), calls)
    }
    @Test fun cancellationCountsTheRequestAndNeverConfirmsResult() = runTest {
        val r = reader(BroadcastPageReader { _, _ -> throw CancellationException() })
        try { r.assignment(fixture); fail("must cancel") } catch (_: CancellationException) { }
        assertEquals(1, state.requests); assertTrue(state.assignments.isEmpty()); assertTrue(saved > 0)
    }
    @Test fun persistedBudgetsPreventNewReaderFromResettingLimits() = runTest {
        state.windowAt = now; state.requests = 6
        assertNull(reader().assignment(fixture)); assertTrue(calls.isEmpty())
    }
    @Test fun outsideRelevantDaysOrUnknownTeamsMakesNoRequests() = runTest {
        listOf(fixture.copy(date = fixture.date.plusDays(3), faceoff = fixture.faceoff.plusSeconds(259200)),
            fixture.copy(home = "Okänt lag"), fixture.copy(league = "NHL"),
            fixture.copy(faceoff = fixture.faceoff.minusSeconds(86400))).forEach { assertNull(reader().assignment(it)) }
        assertTrue(calls.isEmpty())
    }
    @Test fun staleRescheduledAndMalformedDataIsNeverReturnedAsConfirmed() = runTest {
        val r = reader(); assertNotNull(r.assignment(fixture))
        assertNull(r.assignment(fixture.copy(faceoff = fixture.faceoff.plusSeconds(3600))))
        now += ExperimentalBroadcastReader.interval
        assertNull(reader(BroadcastPageReader { _, _ -> 200 to "<html>changed</html>" }).assignment(fixture))
    }
    @Test fun diagnosticStatesSeparateDisabledWindowInvalidAndCacheHitWithoutNetwork() = runTest {
        enabled = false; reader().assignment(fixture)
        assertEquals(BroadcastLookupOutcome.DISABLED, diagnostics.last().outcome)
        enabled = true
        reader().assignment(fixture.copy(date = fixture.date.plusDays(3), faceoff = fixture.faceoff.plusSeconds(259200)))
        assertEquals(BroadcastLookupOutcome.OUTSIDE_WINDOW, diagnostics.last().outcome)
        reader().assignment(fixture.copy(home = fixture.away))
        assertEquals(BroadcastLookupOutcome.INVALID_FIXTURE, diagnostics.last().outcome)
        assertTrue(calls.isEmpty())
        reader().assignment(fixture); assertEquals(BroadcastLookupOutcome.VERIFIED, diagnostics.last().outcome)
        val requests = calls.size
        reader().assignment(fixture); assertEquals(BroadcastLookupOutcome.CACHE_HIT, diagnostics.last().outcome)
        assertEquals(requests, calls.size)
    }
    @Test fun networkAndRedirectFailuresAreNotReportedAsNoBroadcaster() = runTest {
        val r = reader(BroadcastPageReader { _, _ -> throw java.io.IOException("synthetic private endpoint must not appear") })
        assertNull(r.assignment(fixture))
        assertEquals(BroadcastLookupOutcome.NETWORK_ERROR, diagnostics.last().outcome)
        assertEquals(BroadcastLookupStage.ROBOTS, diagnostics.last().stage)
        assertFalse(diagnostics.last().toString().contains("private endpoint"))
        r.assignment(fixture); assertEquals(BroadcastLookupOutcome.BACKOFF, diagnostics.last().outcome)
        state.retryAt = 0
        assertNull(reader(BroadcastPageReader { _, _ -> 302 to "redirect" }).assignment(fixture))
        assertEquals(BroadcastLookupOutcome.HTTP_ERROR, diagnostics.last().outcome)
        assertEquals(302, diagnostics.last().http)
    }
    @Test fun robotsAndTermsDenialShowStageAndPersistBlockWithoutRetry() = runTest {
        robot = "User-agent: *\nDisallow: /ishockey/\n"
        assertNull(reader().assignment(fixture))
        assertEquals(BroadcastLookupOutcome.ACCESS_BLOCKED, diagnostics.last().outcome)
        assertEquals(BroadcastLookupStage.ROBOTS, diagnostics.last().stage)
        val count = calls.size
        reader().assignment(fixture); assertEquals(count, calls.size)
    }
    @Test fun noListingCandidateMalformedFixtureAndAmbiguityHaveDifferentReasons() = runTest {
        fun cached(paths: List<String>) {
            state.robots = robot; state.robotsAt = now; state.termsAt = now; state.listingAt = now
            state.index = mapOf(fixtureKey(fixture) to paths); state.attempted.clear()
        }
        cached(emptyList()); assertNull(reader().assignment(fixture))
        assertEquals(BroadcastLookupOutcome.NOT_FOUND, diagnostics.last().outcome)
        cached(listOf(path))
        assertNull(reader(BroadcastPageReader { _, _ -> 200 to """<link href="https://www.tvmatchen.nu$path"><a href="/ishockey/shl">SHL</a>""" }).assignment(fixture))
        assertEquals(BroadcastLookupOutcome.FIELDS_REJECTED, diagnostics.last().outcome)
        cached(listOf(path, path + "1", path + "2")); assertNull(reader().assignment(fixture))
        assertEquals(BroadcastLookupOutcome.AMBIGUOUS, diagnostics.last().outcome)
    }
    @Test fun invalidStructuredListingIsAFormatFailureNotANetworkFailure() = runTest {
        state.robotsAt = now; state.termsAt = now; state.robots = robot
        val malformed = """<a href="/ishockey/shl">SHL</a><script>self.__next_f.push([1,invalid])</script>"""
        assertNull(reader(BroadcastPageReader { _, _ -> 200 to malformed }).assignment(fixture))
        assertEquals(BroadcastLookupOutcome.FORMAT_ERROR, diagnostics.last().outcome)
        assertEquals(BroadcastLookupStage.LISTING, diagnostics.last().stage)
    }
    @Test fun journalFailureStopsBeforeHttpAndReportsStorageFailure() = runTest {
        val r = ExperimentalBroadcastReader(BroadcastPageReader { _, _ -> fail("No HTTP after failed budget journal"); 200 to "" },
            { true }, state, { error("synthetic private path") }, { now }, { true }, diagnostics::add)
        assertNull(r.assignment(fixture))
        assertEquals(BroadcastLookupOutcome.CACHE_ERROR, diagnostics.last().outcome)
        assertTrue(state.retryAt > now)
    }

}
