package tv.own.owntv.features.live.source

import org.junit.Assert.*
import org.junit.Test

class RemoteOkPressTest {
    @Test fun ancestorNavigationCancelsOnlyTheCurrentOwnerAndDisposalCannotCancelItsSuccessor() {
        val group = SourcePressCancellation(); val first = Any(); val second = Any()
        val old = RemoteOkPress(); val next = RemoteOkPress()
        old.down(1, 0); group.register(first) { old.cancel() }
        group.cancel(); assertFalse(old.held(1)); assertFalse(old.up(1))
        old.down(2, 0); group.register(first) { old.cancel() }
        next.down(3, 0); group.register(second) { next.cancel() }
        assertFalse(old.up(2)); group.clear(first)
        assertTrue(next.up(3))
        next.down(4, 0); group.cancel(); assertFalse(next.held(4))
    }
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
