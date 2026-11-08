package tv.own.owntv.features.sporthub

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class HubSourcesTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")
    private val del = HubLeague("de.del", HubSport.ICE_HOCKEY, 0, HubProvider.OPENLIGA, HubAccess.VERIFIED_PUBLIC, "del")
    private val nhl = HubLeague("us.nhl", HubSport.ICE_HOCKEY, 0, HubProvider.EVENTS24, HubAccess.VERIFIED_PUBLIC,
        "123176", "NHL 2026/2027", "US")
    private fun olRow(id: Long = 1, at: String = "2026-11-09T18:00:00Z", finished: Boolean = false): JSONObject = JSONObject()
        .put("matchID", id).put("leagueShortcut", "del").put("leagueSeason", 2026).put("matchDateTimeUTC", at)
        .put("matchIsFinished", finished).put("team1", JSONObject().put("teamId", 11).put("teamName", "Team Alpha").put("shortName", "Alpha"))
        .put("team2", JSONObject().put("teamId", 12).put("teamName", "Team Beta"))
    private fun ol(vararg rows: JSONObject) = JSONArray(rows.toList()).toString()
    private fun result(kind: String, h: Any, a: Any) = JSONObject().put("resultTypeKind", kind).put("pointsTeam1", h).put("pointsTeam2", a)
    private fun event(id: Long = 1) = JSONObject().put("id", id).put("tournamentId", 123176).put("tournament", "NHL 2026/2027")
        .put("sportId", 3).put("country", "US").put("teamHome", "Montréal Test Club").put("teamAway", "Boston Test Club")
        .put("startsAt", "2026-11-09T00:00:00Z").put("localStartsAt", "2026-11-08T19:00:00").put("timeZone", "America/Toronto")
        .put("category", "upcoming").put("result", "").put("updatedAt", "2026-11-07T10:00:00Z")
    private fun events(vararg rows: JSONObject, remaining: Int = 4000, cursor: Any = JSONObject.NULL): String = JSONObject()
        .put("data", JSONArray(rows.toList())).put("nextCursor", cursor).put("usage", JSONObject().put("day", "2026-11-08")
            .put("month", "2026-11").put("requestsRemaining", 80).put("rowsRemaining", remaining)).toString()
    private class Cache : HubCache {
        val entries = mutableMapOf<String, HubCacheEntry>()
        override suspend fun read(key: String) = entries[key]
        override suspend fun write(key: String, value: HubCacheEntry) { entries[key] = value }
    }

    @Test fun openLigaUsesExplicitUtcAndProviderTeamIds() {
        val m = parseOpenLiga(ol(olRow()), del, "2026", now).matches.single()
        assertEquals("hockey:openliga:11", m.home.id)
        assertEquals(Instant.parse("2026-11-09T18:00:00Z"), m.startsAt)
        assertEquals(HubMatchStatus.SCHEDULED, m.status); assertNull(m.score)
    }
    @Test fun resultStagesDoNotSumPeriodsOrLoseShootout() {
        val row = olRow(finished = true).put("matchResults", JSONArray(listOf(
            result("HalfTime", 1, 0), result("After90Minutes", 2, 2), result("AfterExtraTime", 2, 2), result("AfterPenalties", 3, 2))))
        assertEquals(HubScore(3, 2, HubResultStage.PENALTIES), parseOpenLiga(ol(row), del, "2026", now).matches.single().score)
    }
    @Test fun absentAndConflictingScoresNeverBecomeZeroZero() {
        val row = olRow(finished = true)
        assertNull(parseOpenLiga(ol(row), del, "2026", now).matches.single().score)
        row.put("matchResults", JSONArray(listOf(result("After90Minutes", 1, 2), result("After90Minutes", 2, 1))))
        assertNull(parseOpenLiga(ol(row), del, "2026", now).matches.single().score)
        row.put("matchResults", JSONArray(listOf(result("After90Minutes", JSONObject.NULL, 2))))
        assertNull(parseOpenLiga(ol(row), del, "2026", now).matches.single().score)
    }
    @Test fun unfinishedPastGameIsNotInventedLiveOrFinal() {
        val s = parseOpenLiga(ol(olRow(at = "2026-10-30T18:00:00Z")), del, "2026", now)
        assertEquals(HubMatchStatus.UNCONFIRMED, s.matches.single().status)
        assertTrue(HubCoverageIssue.PAST_WITHOUT_FINAL in s.coverage)
    }
    @Test fun wrongLeagueSeasonAndNoUtcAreRejected() {
        for (row in listOf(olRow().put("leagueShortcut", "other"), olRow().put("leagueSeason", 2025),
            olRow().put("matchDateTimeUTC", "2026-11-09T19:00:00")))
            assertTrue(runCatching { parseOpenLiga(ol(row), del, "2026", now) }.isFailure)
    }
    @Test fun duplicatesCollapseAndConflictingIdentityFailsClosed() {
        assertEquals(1, parseOpenLiga(ol(olRow(), olRow()), del, "2026", now).matches.size)
        val s = parseOpenLiga(ol(olRow(), olRow(at = "2026-11-10T18:00:00Z"), olRow(id = 2)), del, "2026", now)
        assertEquals(1, s.matches.size); assertTrue(HubCoverageIssue.CONFLICTING_IDS in s.coverage)
    }
    @Test fun sportProviderAndSeasonIdentityCannotCollide() {
        assertNotEquals(hubTeamId(HubSport.ICE_HOCKEY, HubProvider.OPENLIGA, 1), hubTeamId(HubSport.FOOTBALL, HubProvider.OPENLIGA, 1))
        assertNotEquals(hubTeamId(HubSport.ICE_HOCKEY, HubProvider.OPENLIGA, 1), hubTeamId(HubSport.ICE_HOCKEY, HubProvider.EVENTS24, 1))
        assertEquals(parseOpenLiga(ol(olRow()), del, "2026", now).teams.first().id,
            parseOpenLiga(ol(olRow().put("leagueSeason", 2027)), del, "2027", now).teams.first().id)
    }
    @Test fun events24HasRealUtcAndExplicitlyNameDerivedIdentities() {
        val s = parseEvents24(events(event()), nhl, "2026-27", now)
        assertEquals(1, s.matches.size); assertFalse(s.hasMore)
        assertEquals(HubTeamIdentity.NAME_DERIVED, s.teams.first().identity)
        assertEquals(s.teams.first().id, events24Team("MONTREAL Test Club", HubSport.ICE_HOCKEY, "US").id)
        assertNotEquals(s.teams.first().id, events24Team("Montréal Test Club", HubSport.FOOTBALL, "US").id)
        assertNotEquals(s.teams.first().id, events24Team("Montréal Test Club", HubSport.ICE_HOCKEY, "CA").id)
    }
    @Test fun events24RejectsMismatchedTournamentCountrySportAndTimezone() {
        for (r in listOf(event().put("tournamentId", 99), event().put("country", "CA"), event().put("sportId", 1),
            event().put("timeZone", "Europe/Stockholm"), event().put("startsAt", "2026-11-09T00:00:00")))
            assertTrue(runCatching { parseEvents24(events(r), nhl, "2026-27", now) }.isFailure)
    }
    @Test fun partialPagingIsNotReportedAsCompleteAndNoLiveScoreIsGuessed() {
        val s = parseEvents24(events(event().put("startsAt", "2026-11-08T00:00:00Z").put("localStartsAt", "2026-11-07T19:00:00"), cursor = "opaque"), nhl, "2026-27", now)
        assertTrue(s.hasMore); assertNull(s.matches.single().score)
        assertEquals(HubMatchStatus.UNCONFIRMED, s.matches.single().status)
    }
    @Test fun events24FinalScoreDoesNotInventOvertimeDetails() {
        for (score in listOf("2:4", "2-4")) {
            val s = parseEvents24(events(event().put("category", "result").put("result", score)), nhl, "2026-27", now)
            assertEquals(HubScore(2,4,HubResultStage.UNSPECIFIED),s.matches.single().score)
        }
        assertNull(parseEvents24(events(event().put("category", "result").put("result", "unavailable")),nhl,"2026-27",now).matches.single().score)
    }
    @Test fun contradictorySourceTimezoneIsExcludedRatherThanRepairedOrMarkedComplete() {
        // Reproduces the shape observed in the public NHL response; no assumption about which field is right.
        val conflict = event(2).put("startsAt", "2026-11-09T00:00:00Z")
            .put("localStartsAt", "2026-11-08T18:00:00").put("timeZone", "America/Edmonton")
        val s = parseEvents24(events(event(), conflict), nhl, "2026-27", now)
        assertEquals(1, s.matches.size)
        assertTrue(HubCoverageIssue.TIME_CONFLICT in s.coverage)
    }
    @Test fun fractionalAndOversizedSourceIdsCannotSelectAnotherEntity() {
        for (id in listOf<Any>(1.5, "1", java.math.BigInteger("9223372036854775808"))) {
            assertTrue(runCatching { parseOpenLiga(ol(olRow().put("matchID",id)), del,"2026",now) }.isFailure)
            assertTrue(runCatching { parseEvents24(events(event().put("id",id)),nhl,"2026-27",now) }.isFailure)
        }
    }
    @Test fun midnightAndDstUseStockholmDatesWithoutChangingTheInstant() {
        val m = parseOpenLiga(ol(olRow(at = "2026-10-24T22:30:00Z")), del, "2026", now).matches.single()
        assertTrue(m.on(LocalDate.parse("2026-10-25")))
        assertFalse(m.on(LocalDate.parse("2026-10-24")))
        assertEquals(listOf(LocalDate.parse("2026-10-25"),LocalDate.parse("2026-11-08")),hubCalendarDays(listOf(m,m),LocalDate.parse("2026-11-08")))
        assertEquals(HubWindow(LocalDate.parse("2026-10-24"),LocalDate.parse("2026-10-26")),HubWindow.stockholmDay(LocalDate.parse("2026-10-25")))
    }
    @Test fun dateWindowHonorsProviderBoundsAndExclusiveEnd() {
        assertFalse(HubWindow(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-11-09")).allowed(now))
        assertTrue(HubWindow.upcoming(now).allowed(now))
        assertTrue(runCatching { parseEvents24(events(event().put("startsAt","2026-11-15T00:00:00Z")),nhl,"2026-27",now) }.isFailure)
    }
    @Test fun futureClockDoesNotMakeSnapshotFresh() {
        val s = parseOpenLiga(ol(olRow()),del,"2026",now)
        assertEquals(HubFreshness.INVALID_CLOCK,s.freshness(now.minusSeconds(1)))
        assertEquals(HubFreshness.STALE,s.freshness(now.plusSeconds(901)))
        assertEquals(HubFreshness.STALE,HubRead(s,HubIssue.UNAVAILABLE).freshness(now))
    }
    @Test fun noConsentAndUnverifiedProvidersPerformZeroHttp() = runBlocking {
        var calls=0;val reader=HubSourceReader(HubTransport { _,_,_,_ -> calls++;HubHttp(200,ol(olRow())) },Cache())
        assertEquals(HubIssue.DISABLED,reader.read(del,"2026",now,{false}).issue)
        assertEquals(HubIssue.AUTH_REQUIRED,reader.read(del.copy(access=HubAccess.AUTH_REQUIRED),"2026",now,{true}).issue)
        assertEquals(0,calls)
    }
    @Test fun warmConcurrentReadersCoalesceAndCacheSurvivesReaderRecreation() = runBlocking {
        var calls=0;val cache=Cache();val transport=HubTransport { _,_,_,_ -> calls++;HubHttp(200,ol(olRow())) }
        val reader=HubSourceReader(transport,cache)
        awaitAll(*Array(5) { async { reader.read(del,"2026",now,{true}) } })
        assertEquals(1,calls); assertTrue(HubSourceReader(transport,cache).read(del,"2026",now.plusSeconds(1),{true}).cached)
        assertEquals(1,calls)
    }
    @Test fun accessRevokedDuringReadNeverPublishesOrCachesResult() = runBlocking {
        var allowed=true;val cache=Cache();val reader=HubSourceReader(HubTransport { _,_,_,_ -> allowed=false;HubHttp(200,ol(olRow())) },cache)
        assertEquals(HubIssue.DISABLED,reader.read(del,"2026",now,{allowed}).issue);assertTrue(cache.entries.isEmpty())
    }
    @Test fun parentCancellationPropagatesWithoutSuccessfulCache() = runBlocking {
        val cache=Cache();val started=CompletableDeferred<Unit>()
        val reader=HubSourceReader(HubTransport { _,_,_,_ -> started.complete(Unit);awaitCancellation() },cache)
        val job=launch { reader.read(del,"2026",now,{true}) };started.await();job.cancelAndJoin();assertTrue(cache.entries.isEmpty())
    }
    @Test fun rateLimitIsProviderWidePersistentAndCannotBeBypassedByLeagueSwitch() = runBlocking {
        var calls=0;val cache=Cache();val transport=HubTransport { _,_,_,_ -> calls++;HubHttp(429,"",2*86_400_000L) }
        val reader=HubSourceReader(transport,cache);assertEquals(HubIssue.RATE_LIMITED,reader.read(del,"2026",now,{true}).issue)
        assertEquals(HubIssue.RATE_LIMITED,HubSourceReader(transport,cache).read(del.copy(key="other"),"2026",now.plusSeconds(90),{true}).issue)
        assertEquals(1,calls)
    }
    @Test fun accessDenialIsNotAutomaticallyRetriedOrShownAsFreshCachedData() = runBlocking {
        val cache=Cache();var status=200;var calls=0
        val reader=HubSourceReader(HubTransport { _,_,_,_ -> calls++;HubHttp(status,ol(olRow())) },cache)
        reader.read(del,"2026",now,{true});status=403
        assertNull(reader.read(del,"2026",now.plusSeconds(901),{true}).snapshot)
        assertEquals(HubIssue.ACCESS_DENIED,reader.read(del.copy(key="other"),"2026",now.plusSeconds(4000),{true}).issue)
        assertEquals(2,calls)
    }
    @Test fun transientFailureRetainsOnlyExplicitlyStaleData() = runBlocking {
        var status=200;val reader=HubSourceReader(HubTransport { _,_,_,_ -> HubHttp(status,ol(olRow())) },Cache())
        reader.read(del,"2026",now,{true});status=503
        val result=reader.read(del,"2026",now.plusSeconds(901),{true})
        assertNotNull(result.snapshot);assertEquals(HubIssue.UNAVAILABLE,result.issue);assertEquals(HubFreshness.STALE,result.freshness(now.plusSeconds(901)))
    }
    @Test fun betaMonthlyBudgetSurvivesNewReaderAndBlocksOtherLeagues() = runBlocking {
        var calls=0;val cache=Cache();val transport=HubTransport { _,_,_,_ -> calls++;HubHttp(200,events(event(),remaining=40)) }
        HubSourceReader(transport,cache).read(nhl,"2026-27",now,{true})
        assertEquals(HubIssue.RATE_LIMITED,HubSourceReader(transport,cache).read(nhl.copy(key="other"),"2026-27",now.plusSeconds(60),{true}).issue)
        assertEquals(1,calls)
    }
    @Test fun cacheRoundTripIsBoundedVersionedAndContainsNoTransportSecrets() {
        val snapshot=parseEvents24(events(event()),nhl,"2026-27",now)
        val entry=HubCacheEntry(snapshot,now,Instant.MAX,HubIssue.ACCESS_DENIED)
        val text=HubCacheCodec.encode(entry);assertEquals(entry,HubCacheCodec.decode(text))
        assertFalse(text.contains("streamUrl"));assertFalse(text.contains("Authorization"))
        assertNull(HubCacheCodec.decode("broken"));assertNull(HubCacheCodec.decode(text.replace("\"version\":1","\"version\":99")))
        assertEquals(64,hubCacheName("../private?token=not-a-real-secret").length)
    }
    @Test fun bodyAndRetryAfterBoundsDoNotShortenServerCooldown() {
        assertEquals("[]",readBoundedHubBody(ByteArrayInputStream("[]".toByteArray())))
        assertTrue(runCatching { readBoundedHubBody(ByteArrayInputStream(ByteArray(HUB_MAX_BODY+1))) }.isFailure)
        assertEquals(2*86_400_000L,hubRetryAfter("172800",now))
        assertEquals(Long.MAX_VALUE,hubRetryAfter(Long.MAX_VALUE.toString(),now))
        assertNull(hubRetryAfter("not a date",now))
    }
    @Test fun sourceBindingsAreUniqueAndPreserveSwedishOwnership() {
        assertEquals(HubLeagues.reviewed.size,HubLeagues.reviewed.distinctBy { it.key }.size)
        assertEquals(HubAccess.EXISTING_READER,HubLeagues.shl.access)
        assertEquals("se.hockeyallsvenskan",HubLeagues.ha.key)
        assertEquals(HubAccess.VERIFIED_PUBLIC,HubLeagues.nhl.access)
        assertEquals(HubProvider.EVENTS24,HubLeagues.nhl.provider)
        assertEquals(HubAccess.UNVERIFIED,HubLeagues.international.access)
    }
    @Test fun receiveTimeAndServerCooldownStartAfterTheResponse() = runBlocking {
        val cache=Cache()
        val reader=HubSourceReader(HubTransport { _,_,_,_ -> HubHttp(200,ol(olRow()),receivedAt=now.plusSeconds(5)) },cache)
        assertEquals(now.plusSeconds(5),reader.read(del,"2026",now,{true}).snapshot?.fetchedAt)
        val denied=HubSourceReader(HubTransport { _,_,_,_ -> HubHttp(429,"",120_000,now.plusSeconds(5)) },Cache())
        denied.read(del,"2026",now,{true})
        assertEquals(now.plusSeconds(125),denied.read(del.copy(key="other"),"2026",now.plusSeconds(10),{true}).retryAt)
    }
    @Test fun betaCadenceIsProviderWideAndMissingQuotaIsNotSuccess() = runBlocking {
        var calls=0;val reader=HubSourceReader(HubTransport { _,_,_,_ -> calls++;HubHttp(200,events(event())) },Cache())
        reader.read(nhl,"2026-27",now,{true})
        val next=reader.read(nhl.copy(key="other"),"2026-27",now.plusSeconds(1),{true})
        assertEquals(HubIssue.WAITING,next.issue);assertEquals(now.plusSeconds(7),next.retryAt);assertEquals(1,calls)
        val malformed=JSONObject(events(event())).apply { remove("usage") }.toString()
        assertTrue(runCatching { parseEvents24(malformed,nhl,"2026-27",now) }.isFailure)
    }
    @Test fun cacheCannotSupplyAnotherLeaguesSnapshot() = runBlocking {
        val cache=Cache();cache.entries["${del.key}:2026"]=HubCacheEntry(
            parseOpenLiga(ol(olRow()),del.copy(key="other"),"2026",now),now,Instant.MAX,null)
        var calls=0;val reader=HubSourceReader(HubTransport { _,_,_,_ -> calls++;HubHttp(200,ol(olRow())) },cache)
        assertEquals(del.key,reader.read(del,"2026",now,{true}).snapshot?.league);assertEquals(1,calls)
    }
}
