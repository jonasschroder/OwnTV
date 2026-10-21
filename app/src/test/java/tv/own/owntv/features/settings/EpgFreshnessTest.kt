package tv.own.owntv.features.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EpgFreshnessTest {
    @Test fun failedRefreshPreservesLastSuccessAndReportsFailure() = runTest {
        var lastSuccess = 42L
        var failure: String? = null
        val expected = java.io.IOException("Synthetic failure")
        try {
            refreshEpgFeed({ throw expected }, { lastSuccess = it }, { failure = it }, { 99 })
            fail("Expected failure")
        } catch (actual: java.io.IOException) { assertSame(expected, actual) }
        assertEquals(42L, lastSuccess)
        assertEquals("Synthetic failure", failure)
    }

    @Test fun successfulRefreshUsesCompletionTimeAndClearsFailure() = runTest {
        var lastSuccess = 42L
        var failure: String? = "Previous failure"
        var clock = 50L
        val count = refreshEpgFeed({ clock = 99; 123 }, { lastSuccess = it; failure = null }, { failure = it }, { clock })
        assertEquals(123, count)
        assertEquals(99L, lastSuccess)
        assertNull(failure)
    }

    @Test fun cancellationDoesNotInventSuccessOrFailure() = runTest {
        var writes = 0
        try {
            refreshEpgFeed({ throw CancellationException() }, { writes++ }, { writes++ }, { 99 })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, writes)
    }
}
