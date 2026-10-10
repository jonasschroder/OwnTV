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
}
