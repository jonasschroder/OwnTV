package tv.own.owntv.features.home

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HockeyReaderTest {
    private val now = Instant.parse("2026-10-10T15:06:00Z").toEpochMilli()
    private val shl = SportsCatalog.shl
    private val ha = SportsCatalog.allsvenskan
    private fun html(name: String) = javaClass.getResource("/swehockey-2026-10-10/$name.html")!!.readText()
    private class Store : HockeyStorage {
        var on = true; var denied = false
        val numbers = mutableMapOf<String, Long>(); val texts = mutableMapOf<String, String>(); val files = mutableMapOf<String, String>()
        override fun enabled() = on
        override fun blocked() = denied
        override fun number(key: String) = numbers[key] ?: 0
        override fun journal(values: Map<String, Long>) { numbers.putAll(values) }
        override fun text(key: String) = texts[key]
        override fun text(key: String, value: String) { texts[key] = value }
        override fun block(code: Int?) { denied = true; numbers["shl-blocked-code"] = code?.toLong() ?: 0 }
        override fun cache(key: String) = files[key]
        override fun cache(key: String, value: String) { files[key] = value }
    }
    private inner class Source : HockeyPages {
        val requested = mutableListOf<String>()
        var response: CompanionResponse? = null; var error: IOException? = null
        override suspend fun read(path: String, limit: Int): CompanionResponse {
            requested += path; error?.let { throw it }; response?.let { return it }
            return if (path == "/robots.txt") CompanionResponse(404, "") else CompanionResponse(200, html(when (path) {
                "/" -> "index"
                "/ScheduleAndResults/Schedule/20961" -> "shl-schedule"
                "/ScheduleAndResults/Standings/20961" -> "shl-table"
                "/ScheduleAndResults/Schedule/20962" -> "ha-schedule"
                "/ScheduleAndResults/Standings/20962" -> "ha-table"
                else -> error("Unexpected source path")
            }))
        }
    }
    private suspend fun failure(operation: suspend () -> Any?): HockeyIssue = try { operation(); error("Expected failure") }
        catch (e: HockeyDataException) { e.issue }

    @Test fun disabledAndPreviouslyBlockedReadersSendZeroRequests() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        store.on = false; assertEquals(HockeyFailureKind.DISABLED, failure { reader.refresh(shl, now) }.kind)
        store.on = true; store.denied = true; assertEquals(HockeyFailureKind.ACCESS, failure { reader.refresh(shl, now) }.kind)
        assertTrue(source.requested.isEmpty())
    }
    @Test fun bothLeaguesShareDiscoveryBudgetButKeepFixturesTablesAndDiskCacheSeparate() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val a = reader.refresh(shl, now); val b = reader.refresh(ha, now)
        assertEquals("20961", a.seasonId); assertEquals("20962", b.seasonId)
        assertEquals(14, reader.standings(shl, a.seasonId, now).rows.size)
        assertEquals(14, reader.standings(ha, b.seasonId, now).rows.size)
        assertEquals(6, source.requested.size)
        assertEquals(6L, store.numbers["hockey-window-count"])
        val recreated = HockeyReader(store, source)
        assertEquals(a, recreated.refresh(shl, now + 1000))
        assertEquals(b, recreated.refresh(ha, now + 1000))
        assertEquals(14, recreated.standings(ha, b.seasonId, now + 1000).rows.size)
        assertEquals(6, source.requested.size)
        assertNull(recreated.cachedTable(shl, b.seasonId))
    }
    @Test fun transientFailureKeepsValidCacheAndPersistsShortBackoffWithoutBypassingIt() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val initial = reader.refresh(shl, now)
        source.error = IOException("Synthetic network failure")
        val issue = failure { reader.refresh(shl, now + 60 * 60_000) }
        assertEquals(HockeyFailureKind.NETWORK, issue.kind)
        assertEquals(now + 62 * 60_000, issue.retryAt)
        assertEquals(initial, reader.cached(shl, now + 60 * 60_000))
        val calls = source.requested.size
        val recreated = HockeyReader(store, source)
        assertEquals(issue, failure { recreated.refresh(shl, now + 60 * 60_000 + 1000) })
        assertEquals(calls, source.requested.size)
        source.error = null
        assertTrue(recreated.refresh(shl, issue.retryAt!!).fetchedAt > initial.fetchedAt)
    }
    @Test fun parsingFailureAndWrongCompetitionNeverReplaceVerifiedData() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val before = reader.refresh(shl, now)
        source.response = CompanionResponse(200, html("ha-schedule"))
        assertEquals(HockeyFailureKind.FORMAT, failure { reader.refresh(shl, now + 60 * 60_000) }.kind)
        assertEquals(before, reader.cached(shl, now + 60 * 60_000))
        assertEquals("20961", HockeyReader(store, source).cached(shl, now + 60 * 60_000)!!.seasonId)
    }
    @Test fun deniedAccessAndRobotsRestrictionStayBlockedAcrossRecreationAndOptInToggles() = runBlocking {
        for (response in listOf(CompanionResponse(403, ""), CompanionResponse(200, "User-agent: *\nDisallow: /\n"))) {
            val store = Store(); val source = Source().apply { this.response = response }; val reader = HockeyReader(store, source)
            assertEquals(HockeyFailureKind.ACCESS, failure { reader.refresh(shl, now) }.kind)
            store.on = false; store.on = true
            assertEquals(HockeyFailureKind.ACCESS, failure { HockeyReader(store, source).refresh(ha, now + 10_000) }.kind)
            assertEquals(1, source.requested.size)
        }
    }
    @Test fun temporary429HonorsRetryAfterAndHardWindowStillCannotBeReset() = runBlocking {
        val store = Store(); val source = Source().apply { response = CompanionResponse(429, "", "7200") }
        val issue = failure { HockeyReader(store, source).refresh(shl, now) }
        assertEquals(HockeyFailureKind.RATE_LIMITED, issue.kind); assertEquals(now + 2 * 60 * 60_000, issue.retryAt)
        assertFalse(store.denied)
        assertEquals(HockeyFailureKind.RATE_LIMITED, failure { HockeyReader(store, source).refresh(ha, now + 1000) }.kind)
        assertEquals(1, source.requested.size)
        val exhausted = Store().apply { numbers["hockey-window-at"] = now; numbers["hockey-window-count"] = 12 }
        source.response = null
        assertEquals(now + 2 * 60 * 60_000, failure { HockeyReader(exhausted, source).refresh(shl, now + 1000) }.retryAt)
        assertEquals(1, source.requested.size)
    }
    @Test fun cancellationCancelsVisibleFetchWithoutPublishingOrInventingFailure() = runBlocking {
        val store = Store(); val started = CompletableDeferred<Unit>(); var cancelled = false
        val reader = HockeyReader(store, HockeyPages { _, _ ->
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        })
        val job = launch { reader.refresh(shl, now) }; started.await(); job.cancelAndJoin()
        assertTrue(cancelled); assertNull(reader.cached(shl, now)); assertNull(reader.diagnostic(shl))
        assertEquals(1L, store.numbers["hockey-window-count"]) // Dispatched requests still consume the hard budget.
    }
    @Test fun stalePreviousSeasonCacheNeverAppearsAsCurrentAndRedirectsAreNotFollowed() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        reader.refresh(shl, now)
        assertNull(reader.cached(shl, Instant.parse("2027-10-10T15:06:00Z").toEpochMilli()))
        val redirect = Source().apply { response = CompanionResponse(302, "") }
        assertEquals(302, failure { HockeyReader(Store(), redirect).refresh(shl, now) }.httpCode)
        assertEquals(1, redirect.requested.size)
    }
    @Test fun selectedTeamsLimitCompetitionScopeAndForegroundGateIsIndependentOfAnyPlayerState() {
        val frolunda = SportsCatalog.identity("Frölunda HC", Sport.ICE_HOCKEY)!!
        val bik = SportsCatalog.identity("BIK Karlskoga", Sport.ICE_HOCKEY)!!
        val a = SportPreferences(listOf(frolunda.id)); val b = SportPreferences(listOf(bik.id))
        assertEquals(listOf(shl), sportsRequestCompetitions(a, sportsActive(true, true), false, true, true))
        assertEquals(listOf(ha), sportsRequestCompetitions(b, sportsActive(true, true), false, true, true))
        assertTrue(sportsRequestCompetitions(a, sportsActive(false, true), true, true, true).isEmpty())
        assertTrue(sportsRequestCompetitions(a, sportsActive(true, false), true, true, true).isEmpty())
        assertFalse(retryReady(HockeyIssue(HockeyFailureKind.ACCESS, HockeyStage.POLICY, now), now))
        assertFalse(retryReady(HockeyIssue(HockeyFailureKind.NETWORK, HockeyStage.SCHEDULE, now, retryAt = now + 1000), now))
        assertTrue(retryReady(HockeyIssue(HockeyFailureKind.NETWORK, HockeyStage.SCHEDULE, now, retryAt = now + 1000), now + 1000))
    }
    @Test fun failedLeagueDoesNotInvalidateOtherLeagueAndMatchcenterRequestsOnlySelectedCompetition() = runBlocking {
        val frolunda = SportsCatalog.identity("Frölunda HC", Sport.ICE_HOCKEY)!!
        val bik = SportsCatalog.identity("BIK Karlskoga", Sport.ICE_HOCKEY)!!
        val degerfors = SportsCatalog.identity("Degerfors IF", Sport.FOOTBALL)!!
        val selection = SportPreferences(listOf(frolunda.id, bik.id, degerfors.id))
        assertEquals(shl, homeSportsCompetition(SportsCatalog.required(selection), SportsCatalog.football))
        assertEquals(ha, homeSportsCompetition(listOf(ha, SportsCatalog.football), SportsCatalog.football))
        assertEquals(SportsCatalog.football, homeSportsCompetition(listOf(SportsCatalog.football), SportsCatalog.football))
        assertEquals(listOf(shl, ha), sportsRequestCompetitions(selection, true, true, false, true))
        assertEquals(listOf(shl), sportsRequestCompetitions(selection, true, false, true, true, shl))
        assertEquals(listOf(ha), sportsRequestCompetitions(selection, true, false, true, true, ha))
        assertTrue(sportsRequestCompetitions(selection, true, false, true, true, SportsCatalog.football).isEmpty())
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val good = reader.refresh(shl, now)
        val sport = SportSnapshot(shl, good.seasonId, good.games.map { SportsCatalog.fixture(shl, it) }, good.fetchedAt, null)
        assertTrue(sportsSnapshotFresh(sport, SportsLoadState(), now + 1000))
        assertFalse(sportsSnapshotFresh(null, SportsLoadState(issue = HockeyIssue(HockeyFailureKind.NETWORK, HockeyStage.SCHEDULE, now)), now))
        assertTrue(sportsSnapshotFresh(sport, SportsLoadState(), now + 1000))
        assertFalse(sportsSnapshotFresh(sport, SportsLoadState(), now + 6 * 60 * 60_000))
    }
    @Test fun tableParseFailureKeepsVerifiedRanksAndItsOwnTimestamp() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val season = reader.refresh(shl, now).seasonId
        val previous = reader.standings(shl, season, now)
        // Advance beyond the table TTL, retaining the real season schedule. Only robots succeeds.
        store.numbers["hockey-robots-at"] = now + 6 * 60 * 60_000
        source.response = CompanionResponse(200, html("ha-table"))
        val issue = failure { reader.standings(shl, season, now + 6 * 60 * 60_000) }
        assertEquals(HockeyFailureKind.FORMAT, issue.kind)
        assertEquals(previous, HockeyReader(store, source).cachedTable(shl, season))
        assertEquals(now, previous.fetchedAt)
        assertEquals(14, previous.rows.size)
    }

    @Test fun invalidSourceDateIsAFormatErrorAndNeverPresentedAsCacheIoFailure() = runBlocking {
        val store = Store(); val source = Source(); val reader = HockeyReader(store, source)
        val before = reader.refresh(shl, now)
        source.response = CompanionResponse(200, html("shl-schedule").replace(Regex("2026-[0-9]{2}-[0-9]{2}"), "2026-13-40"))
        val issue = failure { reader.refresh(shl, now + 60 * 60_000) }
        assertEquals(HockeyFailureKind.FORMAT, issue.kind)
        assertEquals(HockeyStage.SCHEDULE, issue.stage)
        assertEquals(before, reader.cached(shl, now + 60 * 60_000))
    }

}
