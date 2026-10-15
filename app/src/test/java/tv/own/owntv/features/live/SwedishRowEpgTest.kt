package tv.own.owntv.features.live

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwedishRowEpgTest {
    private fun key(id: Long, source: Long = 1) = SwedishRowKey(id, source, "$source:$id")
    private fun info(at: Long) = SwedishRowInfo(null, SwedishProgramme(1, "Stored", at - 10_000, at + 120_000), null)

    @Test fun midnightProgressAndTransitionNeverKeepExpiredOrInventedTitles() {
        val midnight = java.time.Instant.parse("2026-10-14T22:00:00Z").toEpochMilli()
        val current = SwedishProgramme(1, "Late programme", midnight - 1_800_000, midnight + 1_800_000)
        val next = SwedishProgramme(2, "After midnight", current.stopMs, current.stopMs + 3_600_000)
        val row = SwedishRowInfo(null, current, next)
        assertEquals(.5f, current.progress(midnight), .001f)
        assertEquals(0f, current.progress(current.startMs - 1), 0f)
        assertEquals(1f, current.progress(current.stopMs + 1), 0f)
        assertEquals(next, row.at(current.stopMs).now)
        assertNull(row.at(current.stopMs).next)
        assertNull(row.at(next.stopMs).now)
        assertNull(row.copy(now = current.copy(title = " "), next = null).at(midnight).now)
        assertNull(row.copy(now = current.copy(stopMs = current.startMs), next = null).at(midnight).now)
    }

    @Test fun cacheRefreshesAtBoundaryNegativeExpiryAndClockRollback() = runTest {
        val cache = SwedishRowCache(); val id = key(1); var reads = 0
        var data = SwedishRowInfo(null, SwedishProgramme(1, "First", 0, 10_000), SwedishProgramme(2, "Second", 10_000, 20_000))
        suspend fun read(keys: List<SwedishRowKey>, at: Long): Map<SwedishRowKey, SwedishRowInfo> {
            reads++; return keys.associateWith { data.at(at) }
        }
        assertEquals(1L, cache.load(listOf(id), 5_000, ::read)[id]!!.now!!.id)
        cache.load(listOf(id), 9_999, ::read); assertEquals(1, reads)
        assertEquals(2L, cache.load(listOf(id), 10_000, ::read)[id]!!.now!!.id); assertEquals(2, reads)
        cache.load(listOf(id), 9_000, ::read); assertEquals(3, reads)
        data = SwedishRowInfo(null, null, null)
        cache.load(listOf(id), 70_000, ::read); assertEquals(4, reads)
        cache.load(listOf(id), 99_999, ::read); assertEquals(4, reads)
        cache.load(listOf(id), 100_000, ::read); assertEquals(5, reads)
    }

    @Test fun hundredsOfRowsStayBoundedDeduplicateAndKeepSourceIdentity() = runTest {
        val cache = SwedishRowCache(); var largest = 0
        suspend fun read(keys: List<SwedishRowKey>, at: Long): Map<SwedishRowKey, SwedishRowInfo> {
            largest = maxOf(largest, keys.size); return keys.associateWith { info(at) }
        }
        for (page in 0..50) {
            val result = cache.load((1L..24L).map { key(page * 10 + it) }, 1_000, ::read)
            assertEquals(16, result.size); assertTrue(cache.size <= 32)
        }
        assertEquals(16, largest)
        val a = key(900, 1); val b = key(900, 2)
        assertEquals(setOf(a, b), cache.load(listOf(a, a, b), 1_000, ::read).keys)
        assertEquals(32, cache.size)
    }

    @Test fun cancellationCannotCacheOrPublishANonCooperativeOldDatabaseRead() = runTest {
        val cache = SwedishRowCache(); var published = false
        val pending = launch {
            cache.load(listOf(key(1)), 1_000) { keys, at ->
                withContext(NonCancellable) { delay(500); keys.associateWith { info(at) } }
            }
            published = true
        }
        runCurrent(); pending.cancel(); advanceUntilIdle()
        assertFalse(published); assertEquals(0, cache.size)
    }

    @Test fun viewportPrefetchesOnlyTwoGroupsAndHonorsManualVariantWithoutReordering() {
        val variants = (0L..499L).flatMap { id ->
            listOf("HD", "FHD").mapIndexed { q, quality ->
                SwedishVariant(id * 2 + q, 1, "1:${id * 2 + q}", "TV4 $quality",
                    SwedishName("group$id", "TV4", if (q == 0) ChannelQuality.HD else ChannelQuality.FHD, SwedishCollection.GENERAL, id.toInt()), null)
            }
        }
        val groups = SwedishChannels.group(variants)
        val before = groups.toList()
        val selected = groups[202].variants.last()
        val window = swedishEpgWindow(groups, (200..207).map { groups[it].identity }, SwedishChoices(preferred = mapOf(groups[202].identity to selected.stableKey)))
        assertEquals(10, window.size); assertEquals(selected, window[2])
        assertEquals(groups[209].variants.first(), window.last())
        assertTrue(swedishEpgWindow(groups, listOf("header"), SwedishChoices()).isEmpty())
        assertEquals(before, groups)
    }
}
