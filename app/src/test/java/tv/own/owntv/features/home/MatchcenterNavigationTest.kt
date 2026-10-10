package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class MatchcenterNavigationTest {
    @Test fun backFromPickerReturnsToSameGameAndSection() {
        assertEquals(MatchcenterNavigation(MatchcenterSection.FARJESTAD, "fixture-1", false),
            MatchcenterNavigation(MatchcenterSection.FARJESTAD, "fixture-1", true).back())
    }
    @Test fun backFromDetailReturnsToPreviousSection() {
        assertEquals(MatchcenterNavigation(MatchcenterSection.TABLE),
            MatchcenterNavigation(MatchcenterSection.TABLE, "fixture-1").back())
    }
    @Test fun backFromRootExitsToHome() {
        MatchcenterSection.entries.forEach { assertNull(MatchcenterNavigation(it).back()) }
    }
    @Test fun backSequenceDoesNotSkipTheDetailOrTrapFocus() {
        var state: MatchcenterNavigation? = MatchcenterNavigation(MatchcenterSection.MATCHES, "fixture-1", true)
        state = state!!.back(); assertEquals("fixture-1", state!!.gameId); assertFalse(state.channelPicker)
        state = state.back(); assertNull(state!!.gameId)
        assertNull(state.back())
    }
    @Test fun primaryTeamCopyIsCompactWithoutLosingIdentity() {
        assertEquals("Färjestad", shortTeam("Färjestad BK"))
        assertEquals("Malmö", shortTeam("IF Malmö Redhawks"))
        assertEquals("HV71", shortTeam("HV71"))
    }
    @Test fun backFromExplicitSearchKeepsPickerAndFixtureBeforeReturningToDetails() {
        val picker = MatchcenterNavigation(MatchcenterSection.FARJESTAD, "fixture-1", true, true).back()!!
        assertFalse(picker.searchOpen); assertTrue(picker.channelPicker); assertEquals("fixture-1", picker.gameId)
        assertEquals(MatchcenterNavigation(MatchcenterSection.FARJESTAD, "fixture-1"), picker.back())
    }
    @Test fun manualChoiceIsExactFixtureProfileChannelAndSourceOnly() {
        val game = ShlGame("1", "Färjestad BK", "Malmö Redhawks", java.time.Instant.parse("2026-10-10T13:15:00Z").toEpochMilli(), null)
        val fixture = BroadcastFixture.from(game)
        val choice = FixtureChannelChoice(1, fixtureKey(fixture), 10, 20, "TV4 Sport Live 2", game.faceoff + 14400000)
        assertTrue(choice.usable(1, fixture, game.faceoff, 10, 20, choice.name))
        assertFalse(choice.usable(2, fixture, game.faceoff, 10, 20, choice.name))
        assertFalse(choice.usable(1, fixture.copy(faceoff = fixture.faceoff.plusSeconds(86400)), game.faceoff, 10, 20, choice.name))
        assertFalse(choice.usable(1, fixture, choice.expiresAt, 10, 20, choice.name))
        assertFalse(choice.usable(1, fixture, game.faceoff, 10, 21, choice.name))
        assertFalse(choice.usable(1, fixture, game.faceoff, 11, 20, choice.name))
        assertFalse(choice.usable(1, fixture, game.faceoff, 10, 20, "TV4 Sport Live 3"))
    }
    @Test fun hiddenHomeTableAndSettingsDoNotTriggerFixtureDiscovery() {
        assertFalse(broadcastContentVisible(false, false, MatchcenterSection.MATCHES, false, false))
        assertFalse(broadcastContentVisible(true, true, MatchcenterSection.TABLE, false, false))
        assertFalse(broadcastContentVisible(true, true, MatchcenterSection.SETTINGS, false, false))
        assertFalse(broadcastContentVisible(true, true, MatchcenterSection.MATCHES, false, true))
    }
    @Test fun visibleHomeMatchdayOrSelectedFixtureAllowsDiscovery() {
        assertTrue(broadcastContentVisible(true, false, MatchcenterSection.MATCHES, false, false))
        assertTrue(broadcastContentVisible(false, true, MatchcenterSection.FARJESTAD, false, false))
        assertTrue(broadcastContentVisible(false, true, MatchcenterSection.MATCHES, true, true))
    }
}
