package tv.own.owntv.features.epg
import org.junit.Assert.*
import org.junit.Test
class ManualEpgThrottleTest {
    @Test fun repeatClicksAndRunningJobCannotStartParallelImports() {
        val guard = ManualEpgThrottle()
        assertTrue(guard.begin(0)); assertFalse(guard.begin(120_000))
        guard.finish(); assertFalse(guard.begin(1000)); assertTrue(guard.begin(60_000))
        guard.finish(); assertTrue(guard.begin(120_000))
    }
}
