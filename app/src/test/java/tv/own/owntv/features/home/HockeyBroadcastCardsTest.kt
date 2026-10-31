package tv.own.owntv.features.home

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.live.SwedishChoices

class HockeyBroadcastCardsTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z").toEpochMilli()
    private val game = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("synthetic-fbk", "Brynäs IF", "Färjestad BK", now + 3 * 86_400_000L, null))
    private val channel = ChannelEntity(id = 1, sourceId = 101, name = "TV Hockey Live 1 HD", streamUrl = "http://127.0.0.1:9/unavailable")
    private val local = EpgHockeyMatch(game, listOf(1), mapOf(1L to game.faceoff + 3 * 3_600_000L), false,
        channelStarts = mapOf(1L to game.faceoff))
    private val assignment = BroadcastAssignment(game.broadcastFixture(), listOf(BroadcastChannel("TV4 Sport Live 3", true), BroadcastChannel("TV4 Play Hockey", false)),
        Instant.ofEpochMilli(now), Instant.ofEpochMilli(now + 60_000), "Synthetic licensed fixture")
    private fun card(proof: EpgHockeyMatch? = local, rows: Map<Long, ChannelEntity> = mapOf(1L to channel),
        external: BroadcastAssignment? = assignment, resolved: List<ChannelEntity> = emptyList(), complete: Boolean = false) =
        hockeyCardBroadcast(game, proof, rows, external, resolved, complete, SwedishChoices(), now)
    @Test fun exactLocalProofWinsConflictingExternalAndMakesUniqueSourcePlayable() {
        assertEquals(channel.name, card().label); assertEquals(channel, card().playable)
        assertEquals(HockeyChannelAvailability.PLAYABLE, card().availability)
        assertEquals(HockeyChannelAvailability.CHECKING, card(local.copy(truncated = true)).availability)
        assertNull(card(local.copy(truncated = true)).playable)
        assertEquals(channel.name, card(local.copy(truncated = true)).label)
    }
    @Test fun verifiedNameRemainsVisibleWithoutLibraryOrCompleteSearchAndPlayIsNeverLinear() {
        assertEquals("TV4 Sport Live 3", card(null).label); assertNull(card(null).playable)
        assertEquals(HockeyChannelAvailability.CHECKING, card(null).availability)
        assertEquals(HockeyChannelAvailability.MISSING, card(null, complete = true).availability)
        val row = channel.copy(name = "TV4 Sport Live 3")
        assertNull(card(null, resolved = listOf(row)).playable)
        assertEquals(row, card(null, resolved = listOf(row), complete = true).playable)
        for (name in listOf("TV4 Play Hockey", "TV4Play Hockey", "SE | TV4PLAYHOCKEY FHD")) {
            assertNull(card(rows = mapOf(1L to channel.copy(name = name))).label)
            assertNull(card(rows = mapOf(1L to channel.copy(name = name))).playable)
        }
        assertNull(card(rows = mapOf(1L to channel.copy(name = "TV4 Play Hockey"))).playable)
    }
    @Test fun ambiguityRevocationExpiryRescheduleAndResultsCannotBecomeDirectPlayback() {
        val second = channel.copy(id = 2, sourceId = 102)
        val multiple = local.copy(channelIds = listOf(1, 2), channelStops = local.channelStops + (2L to game.faceoff + 3_600_000), channelStarts = local.channelStarts + (2L to game.faceoff))
        assertNull(card(multiple, mapOf(1L to channel, 2L to second)).playable)
        assertEquals(HockeyChannelAvailability.CHOICE_REQUIRED, card(multiple, mapOf(1L to channel, 2L to second)).availability)
        assertNull(card(rows = emptyMap()).playable)
        assertNull(card(local.copy(channelStops = mapOf(1L to now))).playable)
        assertNull(card(local.copy(fixture = game.copy(kickoff = game.kickoff.plusSeconds(3600))), external = null).playable)
        assertNull(hockeyCardBroadcast(game.copy(status = MatchStatus.RESULT_SNAPSHOT), null, emptyMap(), assignment, listOf(channel), true, SwedishChoices(), now).playable)
        assertNull(card(null, external = assignment.copy(expiresAt = Instant.ofEpochMilli(now))).label)
    }
    @Test fun exactNonFavouriteFixtureCanBeRevalidatedWithoutChangingTeamPreferences() = runTest {
        val other = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("other", "Brynäs IF", "Luleå HF", game.faceoff, null))
        val preferences = SportPreferences(listOf(SportsCatalog.farjestadId))
        val row = programme(999, other.faceoff, "SHL LIVE: BIF – LHF").copy(stopMs = other.faceoff + 3_600_000)
        assertEquals(listOf(channel), matchHockeyChannels(listOf(channel), other, preferences, listOf(other), null, now) { listOf(row) })
        assertEquals(listOf(SportsCatalog.farjestadId), preferences.teamIds)
    }
    @Test fun automaticWindowMatchingNeverPromotesPlayWhileManualSelectionRemainsSeparate() = runTest {
        val play = channel.copy(name = "TV4PLAYHOCKEY")
        assertTrue(matchHockeyChannels(listOf(play), game, SportPreferences(), listOf(game), null, now) { error("Streaming brands are not linear candidates") }.isEmpty())
    }
    @Test fun researchedUpcomingFbkFixturesDoNotSwapNumberedChannelsOrSelectPlay() {
        // Public research evidence checked 27 Oct 2026; fixtures only, never a production mapping.
        val examples = listOf(
            Triple("Brynäs IF", "2026-10-29T18:00:00Z", 1),
            Triple("Växjö Lakers", "2026-10-31T14:15:00Z", 3))
        for ((opponent, kickoff, number) in examples) {
            val fixture = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("research-$number", opponent, "Färjestad BK", Instant.parse(kickoff).toEpochMilli(), null))
            val external = assignment.copy(fixture = fixture.broadcastFixture(), channels = listOf(BroadcastChannel("TV4 Sport Live $number", true), BroadcastChannel("TV4 Play Hockey", false)))
            val library = (1..3).map { channel.copy(id = it.toLong(), name = "TV Hockey Live $it HD") } + channel.copy(id = 4, name = "TV4 Play Hockey")
            val card = hockeyCardBroadcast(fixture, null, emptyMap(), external, library, true, SwedishChoices(), now)
            assertEquals("TV4 Sport Live $number", card.label); assertEquals(number.toLong(), card.playable?.id)
            assertNull(hockeyCardBroadcast(fixture.copy(kickoff = fixture.kickoff.plusSeconds(3600)), null, emptyMap(), external, library, true, SwedishChoices(), now).playable)
        }
    }
    @Test fun foregroundEvidenceExpiresInsteadOfSurvivingOfflineUntilProgrammeEnds() {
        assertEquals(listOf(local), freshHockeyGuide(listOf(local), now, now + 180_000))
        assertTrue(freshHockeyGuide(listOf(local), now, now + 180_001).isEmpty())
        assertTrue(freshHockeyGuide(listOf(local), now + 1, now).isEmpty())
        assertTrue(freshHockeyGuide(listOf(local), 0, now).isEmpty())
    }
    private fun programme(id: Long, start: Long, title: String = "Synthetic programme") = EpgProgrammeEntity(id = id, sourceId = 101,
        epgChannelId = "synthetic-guide", title = title, startMs = start, stopMs = start + 1_800_000)
    @Test fun exactFixtureBeyondFirst128ProgrammesIsReadWithoutNetwork() = runTest {
        val initial = (0L..127L).map { programme(it + 1, now + it * 1_800_000) }
        val target = programme(999, game.faceoff, "SHL LIVE: BIF – FBK").copy(stopMs = game.faceoff + 3_600_000)
        var reads = 0
        val rows = hockeyEpgWithLookahead(initial, hockeyEpgLookahead(listOf(game), now)) { from, to ->
            reads++; assertTrue(game.faceoff in from..to); listOf(target)
        }
        assertEquals(1, reads); assertFalse(rows.truncated)
        val collector = HockeyEpgCollector(SportPreferences(listOf(SportsCatalog.farjestadId)), now, listOf(game))
        initial.forEach { collector.add(1, it) }; assertTrue(collector.result().isEmpty())
        rows.rows.forEach { collector.add(1, it) }; assertEquals(game, collector.result().single().fixture)
    }
    @Test fun lookaheadMergesSimultaneousFixturesBoundsQueriesAndFlagsIncompleteReads() = runTest {
        val windows = hockeyEpgLookahead(listOf(game, game.copy(id = "same-slot"), game.copy(kickoff = game.kickoff.plusSeconds(20 * 86_400))), now)
        assertEquals(1, windows.size)
        val initial = (0L..127L).map { programme(it + 1, now + it * 60_000) }
        var reads = 0
        val capped = hockeyEpgWithLookahead(initial, List(20) { windows.single() }) { _, _ -> reads++; initial }
        assertEquals(12, reads); assertTrue(capped.truncated)
        assertEquals(initial, capped.rows)
        hockeyEpgWithLookahead(initial.take(127), windows) { _, _ -> error("Complete guide needs no duplicate reads") }
    }
    @Test fun expiredLocalEvidenceDoesNotBlockFreshExactExternalAssignment() {
        val row = channel.copy(id = 3, name = "TV4 Sport Live 3")
        val expired = local.copy(channelStops = mapOf(1L to now))
        val result = card(expired, resolved = listOf(row), complete = true)
        assertEquals(row, result.playable)
        assertEquals("TV4 Sport Live 3", result.label)
        assertNull(card(expired, external = assignment.copy(expiresAt = Instant.ofEpochMilli(now)),
            resolved = listOf(row), complete = true).playable)
    }
}
