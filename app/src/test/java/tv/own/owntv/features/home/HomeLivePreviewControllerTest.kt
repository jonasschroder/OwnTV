package tv.own.owntv.features.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLivePreviewControllerTest {
    private class Harness(scope: CoroutineScope) {
        val played = mutableListOf<Long>()
        var stops = 0
        val controller = HomeLivePreviewController(scope, { id: Long -> id }, { played.add(it) }, { stops++ })
    }

    @Test fun initialAndRestoredFocusNeverAutoplay() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList<Long>(), h.played)
    }

    @Test fun importingAnInitialFavoriteRequiresFreshNavigationAfterLoadingFocus() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.onRemoteNavigation() // previous key press while the loading card had focus
        h.controller.setActive(false)
        h.controller.setActive(true)
        h.controller.focus(4)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(emptyList<Long>(), h.played)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800); runCurrent()
        assertEquals(listOf(4L), h.played)
    }

    @Test fun rapidNavigationTunesOnlyTheFinalChannelAfter800ms() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(500)
        h.controller.focus(2)
        advanceTimeBy(500)
        h.controller.focus(3)
        advanceTimeBy(799)
        runCurrent()
        assertEquals(emptyList<Long>(), h.played)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(3L), h.played)
        assertEquals(0, h.stops)
    }

    @Test fun refocusingTheSameChannelReusesItsSession() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800)
        runCurrent()
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(1L), h.played)
        assertEquals(0, h.stops)
    }

    @Test fun changedFocusCancelsAnInFlightCoreRequestBeforeStartingAnother() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800)
        runCurrent() // play can still be resolving a Stalker link in Core
        h.controller.focus(2)
        assertEquals(1, h.stops)
        h.controller.focus(3)
        advanceTimeBy(800)
        runCurrent()
        assertEquals(listOf(1L, 3L), h.played)
        assertEquals(1, h.stops)
    }

    @Test fun disablingOrBackgroundingStopsPlaybackAndResumeDoesNotAutoplay() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800)
        runCurrent()
        h.controller.setActive(false)
        assertEquals(1, h.stops)
        h.controller.setActive(true)
        h.controller.focus(1)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(1L), h.played)
        h.controller.onRemoteNavigation()
        h.controller.setActive(false)
        advanceTimeBy(800)
        runCurrent()
        assertEquals(listOf(1L), h.played)
    }

    @Test fun promotionKeepsTheStreamAcrossSurfaceDisposalAndStopsItOnReturn() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800)
        runCurrent()
        h.controller.beginPromotion()
        h.controller.setActive(false) // Home surface is replaced by fullscreen
        h.controller.focus(null)
        advanceTimeBy(800)
        runCurrent()
        assertEquals(0, h.stops)
        assertEquals(listOf(1L), h.played)
        h.controller.endPromotion()
        assertEquals(1, h.stops)
    }

    @Test fun pressingOkBeforeDebounceNeverLetsALatePreviewRemuteFullscreen() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(300)
        h.controller.beginPromotion()
        advanceTimeBy(800)
        runCurrent()
        assertEquals(emptyList<Long>(), h.played)
        assertEquals(0, h.stops)
        h.controller.endPromotion() // also stops a fullscreen session even without prior preview
        assertEquals(1, h.stops)
    }

    @Test fun removingTheSelectedFavoriteStopsThePreview() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        advanceTimeBy(800)
        runCurrent()
        h.controller.focus(null)
        assertEquals(1, h.stops)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1L), h.played)
    }

    @Test fun leavingHomeDuringAChannelLookupCancelsPromotionAndRemainsSilent() = runTest {
        val h = Harness(this)
        h.controller.setActive(true)
        h.controller.focus(1)
        h.controller.onRemoteNavigation()
        h.controller.beginPromotion()
        h.controller.cancelPendingPromotion()
        h.controller.setActive(false)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, h.stops)
        assertEquals(emptyList<Long>(), h.played)
    }
}
