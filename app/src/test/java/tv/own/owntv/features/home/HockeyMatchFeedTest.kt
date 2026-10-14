package tv.own.owntv.features.home

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class HockeyMatchFeedTest {
    private val now = Instant.parse("2026-10-14T17:15:00Z").toEpochMilli()
    private val favorites = SportPreferences(listOf(SportsCatalog.farjestadId))
    private fun game(id: String, at: Long = now - 15 * 60_000) =
        SportsCatalog.fixture(SportsCatalog.shl, ShlGame(id, "Färjestad BK", "Luleå HF", at, null))
    private fun evidence(fixture: SportFixture, start: Long = fixture.faceoff - 15 * 60_000,
        stop: Long = fixture.faceoff + 3 * 60 * 60_000) =
        EpgHockeyMatch(fixture, listOf(10L), mapOf(10L to stop), true, channelStarts = mapOf(10L to start))

    @Test fun personalFeedRetainsOngoingGameBeforeUpcomingAndRemovesItAtProgrammeEnd() {
        val ongoing = game("ongoing")
        val upcoming = (1..4).map { game("next-$it", now + it * 24 * 60 * 60_000L) }
        val saved = (1..6).map { game("result-$it", now - it * 24 * 60 * 60_000L)
            .copy(status = MatchStatus.RESULT_SNAPSHOT, score = Score(it, 1)) }
        val proof = evidence(ongoing)
        val rows = upcoming.reversed() + saved.reversed() + ongoing
        assertEquals(listOf(ongoing) + upcoming.take(3) + saved.take(5), personalHockeyFeed(rows, favorites, listOf(proof), now))
        assertEquals(ongoing, nextPersonalHockeyFixture(rows, favorites, listOf(proof), now))
        val ended = proof.channelStops.getValue(10L)
        assertFalse(ongoing in personalHockeyFeed(rows, favorites, listOf(proof), ended))
        assertEquals(upcoming.first(), nextPersonalHockeyFixture(rows, favorites, listOf(proof), ended))
    }

    @Test fun programmeAcrossStockholmMidnightRemainsInHomeAndPersonalFeed() {
        val kickoff = Instant.parse("2026-10-24T21:45:00Z").toEpochMilli()
        val afterMidnight = Instant.parse("2026-10-24T22:15:00Z").toEpochMilli()
        val ongoing = game("midnight", kickoff)
        val proof = evidence(ongoing)
        assertNotEquals(stockholmDay(kickoff), stockholmDay(afterMidnight))
        assertEquals(ongoing, nextPersonalHockeyFixture(listOf(ongoing), favorites, listOf(proof), afterMidnight))
        assertEquals(listOf(ongoing), personalHockeyFeed(listOf(ongoing), favorites, listOf(proof), afterMidnight))
        assertEquals(listOf(ongoing), personalHockeyFeed(listOf(ongoing), favorites, listOf(proof), kickoff + 2 * 60 * 60_000))
    }

    @Test fun futureOrMissingProgrammeStartAndExpiredOrInvertedIntervalsDoNotProveOnAir() {
        val ongoing = game("interval")
        val proof = evidence(ongoing)
        for (bad in listOf(proof.copy(channelStarts = emptyMap()),
            evidence(ongoing, now + 60_000), evidence(ongoing, now - 60_000, now),
            evidence(ongoing, now + 60_000, now))) {
            assertTrue(onAirHockeyFixtures(listOf(bad), now).isEmpty())
            assertNull(nextPersonalHockeyFixture(listOf(ongoing), favorites, listOf(bad), now))
        }
        assertTrue(onAirHockeyFixtures(listOf(proof, proof), now).isEmpty())
        assertTrue(onAirHockeyFixtures(listOf(evidence(game("pregame", now + 60_000), now - 60_000)), now).isEmpty())
    }

    @Test fun rescheduledOrChangedFixtureCannotReusePreviousOnAirProof() {
        val original = game("reused")
        val proof = evidence(original)
        val changes = listOf(original.copy(kickoff = original.kickoff.plusSeconds(60)),
            original.copy(homeTeam = original.awayTeam, awayTeam = original.homeTeam),
            original.copy(competition = SportsCatalog.allsvenskan))
        for (changed in changes) {
            assertNull(nextPersonalHockeyFixture(listOf(changed), favorites, listOf(proof), now))
            assertTrue(personalHockeyFeed(listOf(changed), favorites, listOf(proof), now).isEmpty())
        }
    }

    @Test fun profileSelectionAndConcurrentLeaguesStayIndependentWithoutInventingScores() {
        val shl = game("same-raw-id")
        val ha = SportsCatalog.fixture(SportsCatalog.allsvenskan, ShlGame("same-raw-id", "BIK Karlskoga", "MoDo Hockey", shl.faceoff, null))
        val haFavorites = SportPreferences(listOf(ha.homeTeam.id))
        val rows = listOf(shl, ha)
        val proofs = rows.map { evidence(it) }
        assertEquals(listOf(shl), personalHockeyFeed(rows, favorites, proofs, now))
        assertEquals(listOf(ha), personalHockeyFeed(rows, haFavorites, proofs, now))
        assertEquals(2, personalHockeyFeed(rows, SportPreferences(favorites.teamIds + haFavorites.teamIds), proofs, now).size)
        assertTrue(personalHockeyFeed(rows, SportPreferences(), proofs, now).isEmpty())
        assertNull(shl.score); assertNull(ha.score)
        val result = shl.copy(status = MatchStatus.RESULT_SNAPSHOT, score = Score(2, 1))
        assertNull(nextPersonalHockeyFixture(listOf(result), favorites, proofs, now))
        assertEquals(listOf(result), personalHockeyFeed(listOf(result), favorites, proofs, now))
    }

    @Test fun oldScanExpiresPerChannelWithoutChangingStoredEvidenceOrVariantIds() {
        val fixture = game("variants")
        val old = evidence(fixture).copy(channelIds = listOf(10, 11),
            channelStops = mapOf(10L to now, 11L to now + 60_000),
            channelStarts = mapOf(10L to now - 60_000, 11L to now - 60_000), truncated = true)
        val usable = unexpiredHockeyMatches(listOf(old), now).single()
        assertEquals(listOf(11L), usable.channelIds)
        assertEquals(mapOf(11L to now + 60_000), usable.channelStops)
        assertEquals(mapOf(11L to now - 60_000), usable.channelStarts)
        assertTrue(usable.truncated); assertTrue(usable.timeFromEpg)
        assertEquals(listOf(10L, 11L), old.channelIds)
        assertTrue(unexpiredHockeyMatches(listOf(old), now + 60_000).isEmpty())
        assertTrue(unexpiredHockeyMatches(listOf(old.copy(channelStops = emptyMap())), now).isEmpty())
    }

    @Test fun playbackRevalidationRejectsReusedIdEvenWhenProgrammeStillMatchesOldTeams() = runTest {
        val original = game("reused")
        val row = ChannelEntity(id = 10, sourceId = 101, remoteId = "synthetic", name = "TV4 Sport Live 2 HD", streamUrl = "https://example.invalid")
        val programme = EpgProgrammeEntity(sourceId = 101, epgChannelId = "synthetic", title = "SHL LIVE: FBK – LHF",
            startMs = original.faceoff, stopMs = now + 60 * 60_000)
        var reads = 0
        val changed = original.copy(kickoff = original.kickoff.plusSeconds(60))
        assertTrue(matchHockeyChannels(listOf(row), changed, favorites, listOf(original), null, now) {
            reads++; listOf(programme)
        }.isEmpty())
        assertEquals(1, reads)
        assertEquals(listOf(row), matchHockeyChannels(listOf(row), original, favorites, listOf(original), null, now) { listOf(programme) })
    }

    @Test fun playbackRevalidationCannotConfirmAnExpiredProgrammeAfterCachedProofDisappears() = runTest {
        val fixture = game("expired")
        val row = ChannelEntity(id = 10, sourceId = 101, remoteId = "synthetic", name = "TV4 Sport Live 2 HD", streamUrl = "https://example.invalid")
        val ended = EpgProgrammeEntity(sourceId = 101, epgChannelId = "synthetic", title = "SHL LIVE: FBK – LHF",
            startMs = now - 60 * 60_000, stopMs = now)
        assertTrue(matchHockeyChannels(listOf(row), fixture, favorites, listOf(fixture), null, now) { listOf(ended) }.isEmpty())
    }
}
