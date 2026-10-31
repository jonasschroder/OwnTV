package tv.own.owntv.features.home

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.features.live.SwedishProgramme
import tv.own.owntv.features.live.SwedishRowInfo

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeForegroundClockTest {
    @Test fun midnightAndSlowReadersCannotKeepExpiredProgrammeProgress() = runTest {
        val midnight = 86_400_000L
        var wall = midnight - 1000
        var displayed = 0L
        val row = SwedishRowInfo(null, SwedishProgramme(1,"Yesterday",midnight-60000,midnight),
            SwedishProgramme(2,"Today",midnight,midnight+60000))
        val clock = launch { foregroundHomeClock({wall}) {displayed=it} }; runCurrent()
        assertEquals("Yesterday",row.at(displayed).now?.title)
        wall = midnight+1000; advanceTimeBy(5000);runCurrent()
        assertEquals("Today",row.at(displayed).now?.title)
        clock.cancel()
    }
    @Test fun cancellationStopsTicksAndResumePublishesWallTimeImmediately() = runTest {
        var wall=10L;var displayed=0L;var updates=0
        var clock=launch { foregroundHomeClock({wall}) {displayed=it;updates++} };runCurrent()
        clock.cancel();runCurrent();wall=100000;advanceTimeBy(60000);runCurrent();assertEquals(1,updates)
        clock=launch { foregroundHomeClock({wall}) {displayed=it;updates++} };runCurrent()
        assertEquals(wall,displayed);assertEquals(2,updates);clock.cancel()
    }
}
