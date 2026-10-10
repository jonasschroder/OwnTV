package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class HockeyRequestBudgetTest {
    @Test fun recreationAndRepeatedOpeningsDoNotResetTwoLeagueBudget() {
        var budget = HockeyRequestBudget()
        repeat(12) { budget = budget.reserve(1000L) }
        assertEquals(12, budget.count)
        val recreated = HockeyRequestBudget(budget.windowAt, budget.count)
        assertThrows(IllegalStateException::class.java) { recreated.reserve(1001L) }
        assertEquals(HockeyRequestBudget(7_201_000L, 1), recreated.reserve(7_201_000L))
    }
    @Test fun corruptJournalOrBackwardsClockFailClosed() {
        assertThrows(IllegalStateException::class.java) { HockeyRequestBudget(2000, 1).reserve(1999) }
        assertThrows(IllegalStateException::class.java) { HockeyRequestBudget(1000, -1).reserve(2000) }
        assertThrows(IllegalStateException::class.java) { HockeyRequestBudget(1000, 13).reserve(2000) }
    }
}
