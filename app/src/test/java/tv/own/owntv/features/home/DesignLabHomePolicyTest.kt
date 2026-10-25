package tv.own.owntv.features.home

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DesignLabHomePolicyTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private val now = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
    private val preferences = SportPreferences(listOf(SportsCatalog.farjestadId))
    private fun game(at: String = "2026-10-24T17:00:00Z", home: String = "Färjestad BK") =
        SportsCatalog.fixture(SportsCatalog.shl, ShlGame("synthetic", home, "Luleå HF", Instant.parse(at).toEpochMilli(), null))

    @Test fun matchdayRequiresActualFreshFixtureAndProfileFavourite() {
        assertTrue(homeMatchdayEligible(game(), preferences, now, true, zone))
        assertFalse(homeMatchdayEligible(null, preferences, now, true, zone))
        assertFalse(homeMatchdayEligible(game(), preferences, now, false, zone))
        assertFalse(homeMatchdayEligible(game(), SportPreferences(), now, true, zone))
        assertFalse(homeMatchdayEligible(game(), preferences.copy(prominent = false), now, true, zone))
        assertFalse(homeMatchdayEligible(game(home = "Brynäs IF"), preferences, now, true, zone))
    }
    @Test fun matchdayUsesCalendarMidnightRatherThanRollingTwentyFourHours() {
        val midnight = Instant.parse("2026-10-24T22:00:00Z").toEpochMilli()
        val fixture = game("2026-10-24T17:00:00Z")
        assertTrue(homeMatchdayEligible(fixture, preferences, midnight - 1, true, zone))
        assertFalse(homeMatchdayEligible(fixture, preferences, midnight, true, zone))
        assertFalse(homeMatchdayEligible(game("2026-10-25T17:00:00Z"), preferences, now, true, zone))
    }
    @Test fun everyOptionalRowCombinationHasStableLogicalFocusSections() {
        for (channels in listOf(false, true)) for (favorites in listOf(false, true)) for (resumes in listOf(false, true)) {
            val indices = smartHomeSectionIndices(channels, favorites, resumes)
            assertEquals((0..listOf(channels, favorites, resumes).count { it }).toList(), indices.filter { it >= 0 })
            assertEquals(0, indices.first())
            val sections = indices.indices.filter { indices[it] >= 0 }
            sections.zipWithNext().forEach { (a, b) ->
                assertEquals(b, nextHomeSection(a, 1, indices))
                assertEquals(a, nextHomeSection(b, -1, indices))
            }
            assertNull(nextHomeSection(0, -1, indices))
            assertNull(nextHomeSection(sections.last(), 1, indices))
        }
    }
    @Test fun matchHeroKeepsThePromotedEpisodeAccessibleInContinueWatching() {
        val promoted = SmartResume("episode:1", SmartResumeKind.EPISODE, 1, 10, "Synthetic", playedAt = now, position = 10, duration = 100)
        val other = promoted.copy(key = "movie:2", kind = SmartResumeKind.MOVIE, itemId = 2, playedAt = now - 1)
        assertEquals(listOf(promoted, other), smartHomeResumeRows(promoted, listOf(other), true))
        assertEquals(listOf(other), smartHomeResumeRows(promoted, listOf(other), false))
        assertEquals(listOf(promoted), smartHomeResumeRows(promoted, listOf(promoted), true))
    }
    @Test fun hockeyOrderIsNumericAndNeverCreatesChannelsOrBroadcastEvidence() {
        val names = listOf("Unrelated", "TV4 Hockey Live 10 FHD", "TV Hockey Live 2", "TV4 Hockey", "TV4 Hockey Live 1", "TV4 Hockey Highlights")
        val channels = names.mapIndexed { i, name -> tv.own.owntv.core.database.entity.ChannelEntity(
            id = i + 1L, sourceId = 101, name = name, streamUrl = "https://example.invalid") }
        val sorted = sortHockeyChannels(channels)
        assertEquals(listOf(0, 1, 2, 10), sorted.mapNotNull { hockeyChannelNumber(it.name) })
        assertEquals(channels.toSet(), sorted.toSet())
        assertEquals(listOf("Unrelated", "TV4 Hockey Highlights"), sorted.takeLast(2).map { it.name })
        assertNull(hockeyChannelNumber("TV4 Play Hockey"))
        assertNull(hockeyChannelNumber("TV4 Hockey +1"))
    }
}
