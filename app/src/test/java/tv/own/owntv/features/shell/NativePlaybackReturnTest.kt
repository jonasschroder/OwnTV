package tv.own.owntv.features.shell

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.nav.MainSection

class NativePlaybackReturnTest {
    private val sport = NativePlaybackOwner(7, MainSection.HOME, TopDestination.SPORT)
    @Test fun fullscreenReturnsOnlyToItsOwnSportPageAndProfile() {
        assertTrue(restoreSportPlaybackFocus(true, sport, sport))
        assertFalse(restoreSportPlaybackFocus(true, sport, sport.copy(profile = 8)))
        assertFalse(restoreSportPlaybackFocus(true, sport, sport.copy(section = MainSection.SETTINGS)))
        assertFalse(restoreSportPlaybackFocus(true, sport, sport.copy(presentation = null)))
    }
    @Test fun ordinaryBrowsingMiniPlayerAndMissingOwnersNeverRequestRestoration() {
        assertFalse(restoreSportPlaybackFocus(false, sport, sport))
        assertFalse(restoreSportPlaybackFocus(true, null, sport))
        assertFalse(restoreSportPlaybackFocus(true, sport.copy(profile = null), sport.copy(profile = null)))
        val tv = NativePlaybackOwner(7, MainSection.LIVE_TV, null)
        assertFalse(restoreSportPlaybackFocus(true, tv, tv))
    }
    @Test fun recompositionDoesNotCancelPendingReturnForTheSameOwner() {
        val controller = NativePlaybackReturnController(sport)
        controller.opened(); controller.closed(true); assertTrue(controller.pending)
        controller.update(sport.copy()); assertTrue(controller.pending)
    }
    @Test fun pageOrProfileChangeInvalidatesBothOriginAndPendingRequest() {
        for (other in listOf(sport.copy(profile = 8), sport.copy(presentation = null), sport.copy(section = MainSection.SETTINGS))) {
            val controller = NativePlaybackReturnController(sport)
            controller.opened(); controller.update(other); controller.closed(true); assertFalse(controller.pending)
            controller.update(sport); controller.opened(); controller.closed(true); assertTrue(controller.pending)
            controller.update(other); assertFalse(controller.pending)
        }
    }
    @Test fun explicitInputCancelsAndDoesNotRestoreOnLaterPublication() {
        val controller = NativePlaybackReturnController(sport)
        controller.opened(); controller.closed(true); controller.cancel(); controller.update(sport)
        assertFalse(controller.pending)
        controller.opened(); controller.closed(false); assertFalse(controller.pending)
    }

}
