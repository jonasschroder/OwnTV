package tv.own.owntv.features.sporthub

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.home.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class HubExperienceTest {
    @Test fun watchRanksEvidenceWithoutLexicographicLive10BeforeLive2() {
        val channels = listOf(10L,2L,1L,0L).map { n -> tv.own.owntv.core.database.entity.ChannelEntity(
            id=n,sourceId=1,name=if(n==0L) "TV4 Hockey HD" else "TV4 Sport Live $n HD",streamUrl="https://example.invalid") }
        assertEquals(listOf(0L,1L,2L,10L),hubRankWatchChannels(channels) { HubWatchConfidence.UNKNOWN }.map { it.id })
        assertEquals(listOf(10L,0L,1L,2L),hubRankWatchChannels(channels) {
            if(it.id==10L) HubWatchConfidence.GUIDE else HubWatchConfidence.UNKNOWN
        }.map { it.id })
    }
    private val now = Instant.parse("2026-11-08T18:00:00Z")
    private val teamA = HubTeam("football:openliga:1", "Town Alpha United")
    private val teamB = HubTeam("football:openliga:2", "City Beta FC")
    private val game = HubMatch("football:openliga:3", "gb.premier", "2026", teamA, teamB, now,
        HubMatchStatus.SCHEDULED, null)
    @Test fun reviewedSeasonDoesNotFloatAtNewYearOrRunPastItsReviewedCoverage() {
        assertEquals("2026",hubSeason(HubLeagues.premier,Instant.parse("2027-01-10T12:00:00Z")))
        assertTrue(hubBindingCurrent(HubLeagues.premier,Instant.parse("2027-01-10T12:00:00Z")))
        assertFalse(hubBindingCurrent(HubLeagues.premier,Instant.parse("2027-07-01T12:00:00Z")))
        assertFalse(hubBindingCurrent(HubLeagues.allsvenskan,Instant.parse("2027-01-01T12:00:00Z")))
        assertTrue(hubBindingCurrent(HubLeagues.shl,Instant.parse("2028-01-01T12:00:00Z")))
    }
    @Test fun overviewMergesActualPublicMatchesOnlyForThisProfilesFollowsAndConsent() {
        val follow=HubFollow(teamA.id,teamA.name,game.league,HubSport.FOOTBALL)
        val prefs=SportPreferences(listOf(SportsCatalog.farjestadId),hubTeams=listOf(follow),hubProviders=setOf(HubProvider.OPENLIGA))
        val snapshot=HubSnapshot(game.league,game.season,HubProvider.OPENLIGA,listOf(game,game),now)
        val reads=mapOf(game.league to HubRead(snapshot))
        assertEquals(listOf(game),hubOverviewMatches(reads,prefs,now).map { it.match })
        assertTrue(hubOverviewMatches(reads,prefs.copy(hubTeams=emptyList()),now).isEmpty())
        assertTrue(hubOverviewMatches(reads,prefs.copy(hubProviders=emptySet()),now).isEmpty())
        assertTrue(hubOverviewMatches(reads,prefs,now.minusSeconds(1)).isEmpty())
        val legacy=SportsCatalog.fixture(SportsCatalog.shl,ShlGame("legacy","Färjestad BK","Skellefteå AIK",now.plusSeconds(60).toEpochMilli(),null))
        val merged=sportRailItems(listOf(legacy,legacy),hubOverviewMatches(reads,prefs,now))
        assertEquals(2,merged.size);assertTrue(merged.first() is SportRailItem.Public)
        val later=legacy.copy(id="later",kickoff=legacy.kickoff.plusSeconds(60))
        assertEquals(listOf(later.id,legacy.id),sportRailItems(listOf(later,legacy),emptyList()).map { it.key })
    }
    @Test fun evidenceRankingDoesNotUpgradeProbabilityOrManualChoices() {
        val fx=SportsCatalog.fixture(SportsCatalog.shl,ShlGame("event","Färjestad BK","Skellefteå AIK",now.plusSeconds(3600).toEpochMilli(),null,"Test Arena"))
        val a=BroadcastAssignment(fx.broadcastFixture(),listOf(BroadcastChannel("TV4 Sport Live 3",true)),now.minusSeconds(5),now.plusSeconds(1800),"Synthetic metadata")
        assertEquals(HubWatchConfidence.EVENT,hubEventConfidence(fx,a,"TV4 Sport Live 3 HD SE",now))
        val likely=hubEventConfidence(fx,a.copy(evidence=BroadcastEvidence.PROBABLE_EXTERNAL),"TV4 Sport Live 3 HD SE",now)
        assertEquals(HubWatchConfidence.LIKELY,likely)
        assertEquals(HubWatchConfidence.MANUAL,hubWatchConfidence(false,likely,true))
        assertEquals(HubWatchConfidence.GUIDE,hubWatchConfidence(true,likely,true))
        assertEquals(HubWatchConfidence.EVENT,hubWatchConfidence(false,HubWatchConfidence.EVENT,true))
        assertEquals(HubWatchConfidence.UNKNOWN,hubEventConfidence(fx,null,"TV4 Sport Live 3",now))
        assertEquals(HubWatchConfidence.UNKNOWN,hubEventConfidence(fx,a,"TV4 Play",now))
        assertEquals(HubWatchConfidence.UNKNOWN,hubEventConfidence(fx,a.copy(expiresAt=now),"TV4 Sport Live 3",now))
    }
    @Test fun cachedBroadcastLookupsNeverFetchReserveBudgetOrBypassDenial() = runBlocking {
        val fx=SportsCatalog.fixture(SportsCatalog.shl,ShlGame("cached","Färjestad BK","Skellefteå AIK",now.plusSeconds(3600).toEpochMilli(),null,"Test Arena"))
        var calls=0;var writes=0;var allowed=true
        val state=BroadcastReadState()
        val reader=ExperimentalBroadcastReader(BroadcastPageReader { _,_ -> calls++;error("No network permitted") },{allowed},state,{writes++},{now.toEpochMilli()})
        assertNull(reader.cachedAssignment(fx.broadcastFixture()))
        val a=BroadcastAssignment(fx.broadcastFixture(),listOf(BroadcastChannel("TV4 Hockey",true)),now.minusSeconds(1),now.plusSeconds(600),"Synthetic metadata")
        state.assignments+=a;assertEquals(a,reader.cachedAssignment(fx.broadcastFixture()))
        state.blocked=true;assertNull(reader.cachedAssignment(fx.broadcastFixture()))
        state.blocked=false;allowed=false;assertNull(reader.cachedAssignment(fx.broadcastFixture()))
        val public=PublicHockeyReader(PublicBroadcastTransport { calls++;error("No network permitted") },object : PublicBroadcastCache {
            override fun read(): String? = null
            override fun write(value: String) { writes++ }
        })
        assertTrue(public.assignments(listOf(fx),now.toEpochMilli(),true,allowFetch=false).isEmpty())
        assertEquals(0,calls);assertEquals(0,writes);assertEquals(0,state.requests)
    }
    private fun programme(title: String = "Town Alpha United – City Beta FC") = EpgProgrammeEntity(
        id = 4, sourceId = 1, epgChannelId = "test", startMs = now.minusSeconds(300).toEpochMilli(),
        stopMs = now.plusSeconds(7200).toEpochMilli(), title = title, description = "Premier League")
    @Test fun completeTeamPairAndKickoffProduceGuideProof() {
        val p = hubProgrammeProof(game, programme(), listOf(game), now)!!
        assertEquals(HubWatchConfidence.GUIDE, p.confidence)
        assertTrue(p.usable(game, now.plusSeconds(179)))
    }
    @Test fun genericChannelLabelAndDescriptionMentionDoNotConfirmAMatch() {
        assertNull(hubProgrammeProof(game, programme("TV4 Sport Live 3"), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme("Sports magazine").copy(description = "Town Alpha United – City Beta FC"), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme("United – City"), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme().copy(description = "Another league"), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme().copy(description = "Premier League highlights"), listOf(game), now))
    }
    @Test fun replayHighlightsAndAmbiguousFixturesAreNeverGuideProof() {
        for (label in listOf("Repris", "Replay", "Highlights", "Studio", "Sammandrag"))
            assertNull(hubProgrammeProof(game, programme("$label Town Alpha United – City Beta FC"), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme(), listOf(game, game.copy(id = "other")), now))
    }
    @Test fun wrongIntervalInvalidDurationAndFinalResultFailClosed() {
        assertNull(hubProgrammeProof(game, programme().copy(startMs = now.plusSeconds(1).toEpochMilli()), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme().copy(stopMs = now.toEpochMilli()), listOf(game), now))
        assertNull(hubProgrammeProof(game, programme().copy(startMs = now.minusSeconds(6*3600).toEpochMilli()), listOf(game), now))
        assertNull(hubProgrammeProof(game.copy(status = HubMatchStatus.REPORTED_FINAL), programme(), listOf(game), now))
    }
    @Test fun proofCannotOutliveRescheduleClockRollbackOrScanAge() {
        val p = hubProgrammeProof(game, programme(), listOf(game), now)!!
        assertFalse(p.usable(game, now.minusSeconds(1)))
        assertFalse(p.usable(game, now.plusSeconds(181)))
        assertFalse(p.usable(game.copy(startsAt = now.plusSeconds(60)), now))
        assertFalse(p.usable(game.copy(away = teamB.copy(name = "Another Club")), now))
    }
    @Test fun newManualChoicesAreBoundToProfileLifetimeAndFixtureIdentity() {
        assertNotEquals(fixtureKey(hubChoiceFixture(game, 1)), fixtureKey(hubChoiceFixture(game, 2)))
        assertNotEquals(fixtureKey(hubChoiceFixture(game, 1)), fixtureKey(hubChoiceFixture(game.copy(startsAt = now.plusSeconds(60)), 1)))
        val f = hubChoiceFixture(game,1)
        val c = FixtureChannelChoice(7,fixtureKey(f),4,5,"Test channel",now.plusSeconds(3600).toEpochMilli())
        assertTrue(c.usable(7,f,now.toEpochMilli(),4,5,"Test channel"))
        assertFalse(c.usable(8,f,now.toEpochMilli(),4,5,"Test channel"))
        assertFalse(c.usable(7,f,now.toEpochMilli(),4,6,"Test channel"))
    }
    @Test fun calendarUsesStockholmDateAndTeamSelectionUsesIdentityNotSimilarNames() {
        val late = game.copy(startsAt = Instant.parse("2026-11-08T23:30:00Z"))
        assertEquals(listOf(late),hubDayMatches(listOf(late,late),LocalDate.parse("2026-11-09")))
        assertTrue(hubDayMatches(listOf(late),LocalDate.parse("2026-11-08")).isEmpty())
        assertTrue(hubTeamMatches(listOf(game),"hockey:openliga:1").isEmpty())
        assertEquals(listOf(game),hubTeamMatches(listOf(game,game),teamA.id))
    }
    @Test fun routeStackIsBoundedAndBackRestoresExactParentKey() {
        val root = HubRoute(HubPage.DISCOVER).key
        val league = hubPush(root,HubRoute(HubPage.LEAGUE,"us.nhl"))
        val team = hubPush(league,HubRoute(HubPage.TEAM,"us.nhl","hockey:events24:abc"))
        assertEquals(league,hubPop(team)); assertEquals(root,hubPop(league));assertNull(hubPop(root))
        assertEquals(HubRoute(HubPage.LEAGUE,"us.nhl"),HubRoute.decode(league.substringAfterLast('\n')))
        var stack = root;repeat(30) { stack=hubPush(stack,HubRoute(HubPage.LEAGUE,"us.nhl")) }
        assertEquals(8,stack.split('\n').size);assertNull(HubRoute.decode("bad"))
    }
    @Test fun hubFollowsAndConsentRoundTripWithoutMigratingLegacyClubs() {
        val follow = HubFollow(teamA.id,teamA.name,game.league,HubSport.FOOTBALL)
        val saved = SportPreferences(listOf(SportsCatalog.farjestadId), false, true, listOf(follow), setOf(HubProvider.OPENLIGA))
        val encoded = SportPreferencesCodec.bound(saved,100)
        assertEquals(saved,SportPreferencesCodec.forOwner(encoded,100))
        assertEquals(SportPreferences(),SportPreferencesCodec.forOwner(encoded,101))
        val oldReader = JSONObject(SportPreferencesCodec.encode(saved)).getJSONArray("teams")
        assertEquals(SportsCatalog.farjestadId,oldReader.getString(0));assertEquals(1,oldReader.length())
        assertEquals(saved.hubTeams,saved.toggle("hockey:other").hubTeams)
    }
    @Test fun corruptNewFieldsDoNotEraseLegacyFavouritesOrEnableNetwork() {
        val original=SportPreferences(listOf(SportsCatalog.farjestadId), false, true)
        val root=JSONObject(SportPreferencesCodec.encode(original)).put("hubProviders",org.json.JSONArray(listOf("ESPN")))
            .put("hubTeams",org.json.JSONArray(listOf(JSONObject().put("id","../../private"))))
        assertEquals(original,SportPreferencesCodec.decode(root.toString()))
    }
    @Test fun followValidationRejectsSourceUrlsCrossSportIdsAndOversizedLabels() {
        assertTrue(HubFollow(teamA.id,teamA.name,game.league,HubSport.FOOTBALL).valid())
        assertFalse(HubFollow(teamA.id,teamA.name,game.league,HubSport.ICE_HOCKEY).valid())
        assertFalse(HubFollow(teamA.id,"x".repeat(161),game.league,HubSport.FOOTBALL).valid())
        assertFalse(HubFollow(teamA.id,teamA.name,"https://private.invalid",HubSport.FOOTBALL).valid())
    }
    @Test fun unavailableAndChangedMatchesCannotBeRecalledByIdentifierAlone() {
        val s=HubSnapshot(game.league,game.season,HubProvider.OPENLIGA,listOf(game),now)
        assertEquals(game,hubCurrentMatch(s,game.id,now));assertNull(hubCurrentMatch(s,game.id,now.minusSeconds(1)))
        assertNull(hubCurrentMatch(s.copy(matches=emptyList()),game.id,now))
        assertNull(hubCurrentMatch(s.copy(matches=listOf(game,game)),game.id,now))
    }
    @Test fun swedishMatcherReusesExistingAliasesAndNeverAdoptsAnotherFixture() {
        val fixture=SportsCatalog.fixture(SportsCatalog.shl,ShlGame("test","Färjestad BK","Skellefteå AIK",now.toEpochMilli(),null))
        val original=SportSnapshot(SportsCatalog.shl,"2026",listOf(fixture),now.minusSeconds(60).toEpochMilli(),null)
        val m=original.toHub().matches.single()
        val p=programme("SHL: Färjestad – Skellefteå").copy(description=null)
        val matcher=HubGuideMatcher(m,original,SportPreferences(listOf(SportsCatalog.farjestadId)),listOf(m),now)
        assertNotNull(matcher.proof(p))
        assertNull(HubGuideMatcher(m.copy(id="changed"),original,SportPreferences(),listOf(m),now).proof(p))
    }
}
