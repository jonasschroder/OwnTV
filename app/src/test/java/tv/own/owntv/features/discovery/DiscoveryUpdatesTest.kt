@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.discovery

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryUpdatesTest {
    @Test fun metadataChangeDuringInitialReadCannotBeLostAtSubscription() = runTest {
        val revisions = MutableStateFlow(0L)
        val results = mutableListOf<DiscoveryShelf>()
        var reads = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discoveryShelves(flowOf(Unit), revisions, MutableStateFlow(false)) {
                val version = revisions.value
                reads++
                if (reads == 1) revisions.value = 1L
                DiscoveryShelf(rankedAt = version)
            }.collect(results::add)
        }
        runCurrent()
        assertEquals(2, reads)
        assertEquals(1L, results.last().rankedAt)
    }

    @Test fun oneSharedMetadataRefreshUpdatesBothMovieAndSeriesReaders() = runTest {
        val revisions = MutableStateFlow(0L)
        val failed = MutableStateFlow(false)
        val reads = intArrayOf(0, 0)
        repeat(2) { index ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                discoveryShelves(flowOf(Unit), revisions, failed) { reads[index]++; DiscoveryShelf() }.collect()
            }
        }
        runCurrent()
        assertArrayEquals(intArrayOf(1, 1), reads)
        revisions.value++ // The repository announces one network refresh to all visible shelves.
        runCurrent()
        assertArrayEquals(intArrayOf(2, 2), reads)
    }

    @Test fun openingAndCachedRefreshReadOnceAndFailureStatusDoesNotRescan() = runTest {
        val invalidations = MutableSharedFlow<Unit>()
        val revisions = MutableStateFlow(0L)
        val refreshes = MutableStateFlow(false)
        val results = mutableListOf<DiscoveryShelf>()
        var reads = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discoveryShelves(invalidations.onStart { emit(Unit) }, revisions, refreshes) { reads++; DiscoveryShelf(rankedAt = 1) }
                .collect(results::add)
        }
        runCurrent()
        assertEquals(1, reads)
        assertEquals(1L, results.last().rankedAt)
        refreshes.value = false // A fresh cache or disabled metadata needs no scan.
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, reads)
        refreshes.value = true
        runCurrent()
        assertTrue(results.last().failed)
        assertEquals(1, reads)
        refreshes.value = false
        runCurrent()
        assertFalse(results.last().failed)
        assertEquals(1, reads)
    }

    @Test fun importInvalidationsAreDebouncedButNewCandidatesAreReadImmediately() = runTest {
        val invalidations = MutableSharedFlow<Unit>()
        val revisions = MutableStateFlow(0L)
        val refreshes = MutableStateFlow(false)
        var reads = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discoveryShelves(invalidations.onStart { emit(Unit) }, revisions, refreshes) { reads++; DiscoveryShelf() }.collect()
        }
        runCurrent()
        repeat(4) { invalidations.emit(Unit); advanceTimeBy(100); runCurrent() }
        assertEquals(1, reads)
        advanceTimeBy(649); runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, reads)
        revisions.value = 1L
        runCurrent()
        assertEquals(3, reads)
    }

    @Test fun newerCatalogReadCancelsOldWorkAndCannotBeOverwrittenByOldResults() = runTest {
        val revisions = MutableStateFlow(0L)
        val refreshes = MutableStateFlow(false)
        val results = mutableListOf<DiscoveryShelf>()
        var reads = 0
        var cancelled = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discoveryShelves(flowOf(Unit), revisions, refreshes) {
                reads++
                if (reads == 1) try { awaitCancellation() } finally { cancelled = true }
                DiscoveryShelf(rankedAt = 2)
            }.collect(results::add)
        }
        runCurrent()
        assertEquals(1, reads)
        revisions.value = 1L
        runCurrent()
        assertTrue(cancelled)
        assertEquals(2, reads)
        assertEquals(listOf(2L), results.map { it.rankedAt })
    }

    @Test fun failedReadKeepsLastShelfAndRecoversOnNextChange() = runTest {
        val revisions = MutableStateFlow(0L)
        val refreshes = MutableStateFlow(false)
        val results = mutableListOf<DiscoveryShelf>()
        var reads = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discoveryShelves(flowOf(Unit), revisions, refreshes) {
                reads++
                if (reads == 2) error("Synthetic transient database failure")
                DiscoveryShelf(rankedAt = reads.toLong())
            }.collect(results::add)
        }
        runCurrent()
        revisions.value = 1L
        runCurrent()
        assertTrue(results.last().failed)
        assertEquals(1L, results.last().rankedAt)
        revisions.value = 2L
        runCurrent()
        assertFalse(results.last().failed)
        assertEquals(3L, results.last().rankedAt)
        job.cancelAndJoin()
        revisions.value = 3L
        advanceTimeBy(1000); runCurrent()
        assertEquals(3, reads)
    }
}
