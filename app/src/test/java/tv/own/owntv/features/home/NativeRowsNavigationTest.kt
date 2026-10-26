package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class NativeRowsNavigationTest {
    private val rows = listOf(NativeRow("films", listOf("film-entry", "film-1", "film-2", "film-3")),
        NativeRow("series", listOf("series-entry", "series-1")), NativeRow("resume", listOf("episode-8", "movie-2", "episode-9")))
    @Test fun firstDownReachesSeriesEntryAndUnevenRowsKeepPreferredColumn() {
        assertEquals("series-entry", nativeRowTarget(rows, "film-entry", 1, 0))
        assertEquals("series-1", nativeRowTarget(rows, "film-3", 1, 3))
        assertEquals("film-3", nativeRowTarget(rows, "series-1", -1, 3))
        assertEquals("episode-9", nativeRowTarget(rows, "series-1", 1, 3))
    }
    @Test fun nonInteractiveEmptyRowsDoNotTrapDirectionalNavigation() {
        val sparse = listOf(rows.first(), NativeRow("empty", emptyList()), rows.last())
        assertEquals("episode-8", nativeRowTarget(sparse, "film-entry", 1, 0))
        assertEquals("film-2", nativeRowTarget(sparse, "episode-9", -1, 2))
    }
    @Test fun removedRowsAndUnavailableContentDoNotInventTargets() {
        assertNull(nativeRowTarget(rows, "removed", 1, 0))
        assertNull(nativeRowTarget(emptyList(), "film-entry", 1, 0))
        assertNull(nativeRowTarget(rows, "film-entry", -1, 0))
        assertNull(nativeRowTarget(rows, "episode-9", 1, 0))
        assertNull(nativeRowTarget(listOf(rows.first(), NativeRow("empty", emptyList())), "film-entry", 1, 1))
    }
}
