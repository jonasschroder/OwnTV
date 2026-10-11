package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class HomeConditionalNavigationTest {
    @Test fun emptyRealHistorySkipsResumeInBothDirections() {
        val rows = listOf(0, 1, 2, -1, 3, 4)
        assertEquals(4, nextHomeSection(2, 1, rows))
        assertEquals(2, nextHomeSection(4, -1, rows))
        assertNull(nextHomeSection(5, 1, rows))
        assertNull(nextHomeSection(0, -1, rows))
    }
    @Test fun realHistoryBecomesReachableWithoutChangingOtherSectionIdentities() {
        val rows = listOf(0, 1, 2, 3, 4, 5)
        assertEquals(3, nextHomeSection(2, 1, rows))
        assertEquals(3, nextHomeSection(4, -1, rows))
        assertNull(nextHomeSection(2, 0, rows))
    }
}
