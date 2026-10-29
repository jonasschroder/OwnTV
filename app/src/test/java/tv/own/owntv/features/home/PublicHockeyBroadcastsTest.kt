package tv.own.owntv.features.home

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.SwedishChoices

class PublicHockeyBroadcastsTest {
    private val now = Instant.parse("2026-10-29T08:00:00Z").toEpochMilli()
    private val fixture = SportsCatalog.fixture(SportsCatalog.shl,
        ShlGame("synthetic", "Brynäs IF", "Färjestad BK", now + 2 * 86_400_000, null, "Synthetic Arena"))
    private val programme = PublicHockeyProgramme("TV4 Sport Live 1", "Live: Ishockey: SHL", "Från Synthetic Arena.",
        fixture.faceoff - 900_000, fixture.faceoff + 9_000_000, now, now)
    private fun assignment(p: List<PublicHockeyProgramme> = listOf(programme), known: List<SportFixture> = listOf(fixture), at: Long = now) =
        publicHockeyAssignment(fixture, known, p, at)
    private fun body(slug: String, generated: Long = now) = JSONObject().put("generatedAt", Instant.ofEpochMilli(generated).toString())
        .put("channel", JSONObject().put("name", publicTvChannels.getValue(slug)).put("slug", slug))
        .put("programs", JSONArray().put(JSONObject().put("title", programme.title).put("description", programme.description)
            .put("isLive", true).put("start", Instant.ofEpochMilli(programme.start).toString()).put("end", Instant.ofEpochMilli(programme.end).toString()))).toString()

    @Test fun uniqueVenueSuggestsExactNumberWithoutTeams() {
        val a = assignment()!!
        assertEquals(BroadcastEvidence.PROBABLE_EXTERNAL, a.evidence)
        assertEquals("TV4 Sport Live 1", a.channels.single().name)
        assertEquals(a, BroadcastResolver.usable(fixture.broadcastFixture(), a, Instant.ofEpochMilli(now)))
    }
    @Test fun explicitTeamsHaveConfirmedStrength() {
        assertEquals(BroadcastEvidence.CONFIRMED, assignment(listOf(programme.copy(title="SHL Brynäs IF – Färjestad BK")))?.evidence)
    }
    @Test fun wrongRenamedOrMissingArenaNeverGuesses() {
        assertNull(assignment(listOf(programme.copy(description="Från Another Arena."))))
        assertNull(publicHockeyAssignment(fixture.copy(venue=null), listOf(fixture), listOf(programme), now))
        assertNull(assignment(listOf(programme.copy(description="Från Synthetic Arenaberg."))))
        assertNull(assignment(listOf(programme.copy(description="Från New Synthetic Arena."))))
        assertNull(assignment(listOf(programme.copy(description="Från Synthetic Arena B."))))
    }
    @Test fun concurrentChannelsOrFixturesRequireManualChoice() {
        assertNull(assignment(listOf(programme, programme.copy(channel="TV4 Sport Live 2"))))
        assertNull(assignment(known=listOf(fixture, fixture.copy(id="other", homeTeam=SportsCatalog.inCompetition(SportsCatalog.shl)[1]))))
    }
    @Test fun inconsistentSameIdFixtureIsRejected() {
        assertNull(assignment(known=listOf(fixture, fixture.copy(kickoff=fixture.kickoff.plusSeconds(86_400)))))
    }
    @Test fun overlappingDifferentBroadcastOnSameStationIsRejected() {
        assertNull(assignment(listOf(programme, programme.copy(description="Another Arena"))))
    }
    @Test fun staleFutureOrRescheduledAssignmentsAreNotReusable() {
        assertNull(assignment(at=now+publicTvTtl))
        assertNull(assignment(listOf(programme.copy(fetchedAt=now+1))))
        val a=assignment()!!
        assertNull(BroadcastResolver.usable(fixture.copy(kickoff=fixture.kickoff.plusSeconds(3600)).broadcastFixture(),a,Instant.ofEpochMilli(now)))
        assertNull(BroadcastResolver.usable(fixture.copy(venue="Renamed Arena").broadcastFixture(),a,Instant.ofEpochMilli(now)))
    }
    @Test fun replayStudioOtherLeagueAndStreamingNeverBecomeLinearRecommendations() {
        for (word in listOf("studio","repris","U20","HockeyAllsvenskan")) assertNull(assignment(listOf(programme.copy(title="SHL $word"))))
        assertNull(assignment(listOf(programme.copy(channel="TV4 Play Sport Hockey"))))
        assertNull(assignment(listOf(programme.copy(channel="C More Extreme"))))
    }
    @Test fun parserChecksChannelIdentityLiveFlagAndSourceClock() {
        assertEquals(1,parsePublicHockey("tv4-sport-live-1",body("tv4-sport-live-1"),now).size)
        for (value in listOf(now-publicTvTtl,now+600_000)) {
            assertTrue(runCatching { parsePublicHockey("tv4-sport-live-1",body("tv4-sport-live-1",value),now) }.isFailure)
        }
        assertTrue(runCatching { parsePublicHockey("tv4-sport-live-2",body("tv4-sport-live-1"),now) }.isFailure)
        val raw=JSONObject(body("tv4-hockey"));raw.getJSONArray("programs").getJSONObject(0).put("isLive",false)
        assertTrue(parsePublicHockey("tv4-hockey",raw.toString(),now).isEmpty())
    }
    @Test fun localEpgConfirmationOverridesProbableAndUsesAuthorizedChannelOnly() {
        val external=assignment()!!
        val ch=ChannelEntity(id=1,sourceId=101,name="TV Hockey Live 1 HD",streamUrl="http://127.0.0.1:9/synthetic",number=1)
        val other=ch.copy(id=2,name="TV Hockey Live 2 HD")
        val probable=hockeyCardBroadcast(fixture,null,emptyMap(),external,listOf(ch,other),true,SwedishChoices(),now)
        assertTrue(probable.probable);assertEquals(1L,probable.playable?.id)
        val local=EpgHockeyMatch(fixture,listOf(2),mapOf(2L to (fixture.faceoff+3_600_000)),false,channelStarts=mapOf(2L to (fixture.faceoff-900_000)))
        val confirmed=hockeyCardBroadcast(fixture,local,mapOf(2L to other),external,listOf(ch),true,SwedishChoices(),now)
        assertFalse(confirmed.probable);assertEquals(2L,confirmed.playable?.id)
        assertNull(hockeyCardBroadcast(fixture,null,emptyMap(),external,listOf(other),true,SwedishChoices(),now).playable)
    }
    @Test fun externalSourceConflictIsNotSilentlyResolved() {
        val a=assignment()!!;val b=a.copy(channels=listOf(BroadcastChannel("TV4 Sport Live 3",true)),evidence=BroadcastEvidence.CONFIRMED)
        assertNull(mergedHockeyAssignment(fixture.broadcastFixture(),a,b,Instant.ofEpochMilli(now)))
    }
    private class Cache : PublicBroadcastCache {
        var text:String?=null
        override fun read()=text
        override fun write(value:String){text=value}
    }
    @Test fun completeScanCachedAcrossProfilesAndRestartWithoutIpTvState() = runTest {
        val cache=Cache();var requests=0;var generation=now
        val transport=PublicBroadcastTransport { slug -> requests++;CompanionResponse(200,body(slug,generation)) }
        val reader=PublicHockeyReader(transport,cache,{})
        // Six channels intentionally conflict in this synthetic fixture. No automatic choice.
        assertTrue(reader.assignments(listOf(fixture),now,true).isEmpty());assertEquals(6,requests)
        reader.assignments(listOf(fixture),now+1000,true);assertEquals(6,requests)
        PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now+2000,true);assertEquals(6,requests)
        generation=now+publicTvTtl
        reader.assignments(listOf(fixture),now+publicTvTtl,true);assertEquals(12,requests)
    }
    @Test fun disabledAndMissingVenueDoNotFetch() = runTest {
        var requests=0;val reader=PublicHockeyReader(PublicBroadcastTransport { requests++;error("unexpected") },Cache(),{})
        assertTrue(reader.assignments(listOf(fixture),now,false).isEmpty())
        assertTrue(reader.assignments(listOf(fixture.copy(venue=null)),now,true).isEmpty());assertEquals(0,requests)
    }
    @Test fun accessDenialOrPartialFailureNeverPublishesIncompleteScan() = runTest {
        for (code in listOf(401,403,429,500)) {
            var requests=0;val cache=Cache()
            val transport=PublicBroadcastTransport { requests++;CompanionResponse(code,"", "7200") }
            assertTrue(PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now,true).isEmpty());assertEquals(1,requests)
            PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now+publicTvTtl,true);assertEquals(1,requests)
        }
    }
    @Test fun partialScanAndCancellationReserveBudgetWithoutPublishing() = runTest {
        var requests=0;val cache=Cache()
        val transport=PublicBroadcastTransport { slug -> requests++; if(requests==2) CompanionResponse(503, "") else CompanionResponse(200,body(slug)) }
        assertTrue(PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now,true).isEmpty())
        assertEquals(2,requests);assertEquals(0,JSONObject(cache.text!!).getJSONArray("rows").length())
        PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now+1000,true);assertEquals(2,requests)
        val cancelled=Cache();var calls=0
        val stop=PublicBroadcastTransport { calls++;throw kotlinx.coroutines.CancellationException() }
        assertTrue(runCatching { PublicHockeyReader(stop,cancelled,{}).assignments(listOf(fixture),now,true) }.isFailure)
        PublicHockeyReader(stop,cancelled,{}).assignments(listOf(fixture),now+1000,true);assertEquals(1,calls)
    }
    @Test fun backwardsClockDoesNotFetchOrReuseFutureData() = runTest {
        var requests=0;val cache=Cache()
        val transport=PublicBroadcastTransport { slug -> requests++;CompanionResponse(200,body(slug)) }
        PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now,true)
        assertTrue(PublicHockeyReader(transport,cache,{}).assignments(listOf(fixture),now-1000,true).isEmpty());assertEquals(6,requests)
    }
    @Test fun realPublicObservationReproducesTwelveProbableMatchesIncludingBothFbkReferences() {
        val root=JSONObject(javaClass.getResource("/public-hockey/observed-2026-10-29.json")!!.readText())
        val at=Instant.parse(root.getString("checked_at")).toEpochMilli()
        val known=root.getJSONArray("fixtures").let { array -> (0 until array.length()).map { i -> val f=array.getJSONObject(i)
            SportsCatalog.fixture(SportsCatalog.shl,ShlGame(f.getString("id"),f.getString("home"),f.getString("away"),Instant.parse(f.getString("kickoff_local")).toEpochMilli(),null,f.getString("venue"))) } }
        val rows=root.getJSONArray("programmes").let { array -> (0 until array.length()).map { i -> val p=array.getJSONObject(i)
            PublicHockeyProgramme(p.getString("channel"),p.getString("title"),p.getString("description"),Instant.parse(p.getString("start")).toEpochMilli(),Instant.parse(p.getString("end")).toEpochMilli(),Instant.parse(p.getString("generated")).toEpochMilli(),Instant.parse(p.getString("fetched")).toEpochMilli()) } }
        val assignments=known.mapNotNull { f -> publicHockeyAssignment(f,known,rows,at) }
        assertEquals(12,assignments.size);assertTrue(assignments.all { it.evidence==BroadcastEvidence.PROBABLE_EXTERNAL })
        assertEquals("TV4 Sport Live 1", assignments.single { it.fixture.home=="Brynäs IF" && it.fixture.away=="Färjestad BK" }.channels.single().name)
        assertEquals("TV4 Sport Live 3", assignments.single { it.fixture.home=="Växjö Lakers HC" && it.fixture.away=="Färjestad BK" }.channels.single().name)
    }
}
