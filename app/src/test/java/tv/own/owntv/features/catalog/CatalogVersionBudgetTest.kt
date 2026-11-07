package tv.own.owntv.features.catalog

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogVersionBudgetTest {
    @Test fun availableVersionAddsNoDelay() = runTest {
        assertEquals(7, catalogVersionWithinBudget { 7 })
        assertEquals(0L, currentTime)
    }
    @Test fun busyWriterTimesOutAndReleasesWaitWithoutGrantingCacheReuse() = runTest {
        var released = false
        val answer = catalogVersionWithinBudget<Int> { try { awaitCancellation() } finally { released = true } }
        assertNull(answer)
        assertTrue(released)
        assertEquals(CATALOG_VERSION_TIMEOUT_MS, currentTime)
    }
    @Test fun pageCancellationIsNotConvertedToAnAvailableVersion() = runTest {
        var returned = false; var released = false
        val task = launch {
            catalogVersionWithinBudget<Int> { try { awaitCancellation() } finally { released = true } }
            returned = true
        }
        runCurrent(); task.cancelAndJoin()
        assertFalse(returned); assertTrue(released)
    }
}
