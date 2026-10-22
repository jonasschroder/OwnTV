package tv.own.owntv.features.live.source

import org.junit.Assert.*
import org.junit.Test

class RemoteOkPressTest {
    @Test fun shortOkPlaysOnlyOnMatchingReleaseOnce() {
        val press = RemoteOkPress()
        assertTrue(press.down(1, 0)); assertFalse(press.up(2)); assertTrue(press.up(1)); assertFalse(press.up(1))
    }
    @Test fun heldOkOpensOnceAndRepeatAndReleaseCannotPlay() {
        val press = RemoteOkPress()
        assertTrue(press.down(1, 0)); assertTrue(press.held(1))
        for (repeat in 1..20) { assertFalse(press.down(1, repeat)); assertFalse(press.held(1)) }
        assertFalse(press.up(1)); assertTrue(press.down(2, 0)); assertTrue(press.up(2))
    }
    @Test fun focusNavigationLifecycleAndDismissCancellationCannotPlayOrOpenLater() {
        for (cancelReason in 1..4) {
            val press = RemoteOkPress(); press.down(cancelReason.toLong(), 0); press.cancel()
            assertFalse(press.held(cancelReason.toLong())); assertFalse(press.down(cancelReason.toLong(), 1))
            assertFalse(press.up(cancelReason.toLong()))
        }
    }
    @Test fun staleTimerCannotAffectNextPhysicalPress() {
        val press = RemoteOkPress(); press.down(1, 0); press.cancel(); press.down(2, 0)
        assertFalse(press.held(1)); assertTrue(press.up(2))
    }
    @Test fun repeatWithoutInitialDownAndDuplicateInitialDownAreIgnored() {
        val press = RemoteOkPress()
        assertFalse(press.down(1, 5)); assertFalse(press.up(1)); assertTrue(press.down(2, 0))
        assertFalse(press.down(2, 0)); assertTrue(press.up(2))
    }
}
