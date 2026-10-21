package tv.own.owntv.features.epg

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class GuideTimeStateTest {
    private val midnight = Instant.parse("2026-10-21T22:00:00Z").toEpochMilli()
    private fun programme(id: Long, start: Long, stop: Long, title: String) =
        EpgProgrammeEntity(id, 1, "synthetic", start, stop, title, description = title)

    @Test fun liveCursorAndDetailsAdvanceTogetherAtMidnightWithoutFocusChange() {
        val before = programme(1, midnight - 60_000, midnight, "Before")
        val after = programme(2, midnight, midnight + 60_000, "After")
        val state = GuideTimeState(midnight - 1, lastInteractionElapsed = 0)
        assertEquals(before, programmeAt(listOf(before, after), state.cursor))
        val tick = state.refreshed(midnight, 1)
        assertEquals(midnight, tick.cursor)
        assertEquals(after, programmeAt(listOf(before, after), tick.cursor))
    }

    @Test fun longResumeReturnsManualWindowAndExpiredProgrammeToNow() {
        val old = GuideTimeState(midnight - 3_600_000, lastInteractionElapsed = 0).browse(midnight - 3_600_000, 10)
        val resumed = old.refreshed(midnight + 10_000, 10 + GuideTimeState.IDLE_RESET_MS)
        assertTrue(resumed.followsNow)
        assertEquals(midnight + 10_000, resumed.cursor)
        assertNull(programmeAt(listOf(programme(1, midnight - 3_600_000, midnight, "Expired")), resumed.cursor))
    }

    @Test fun activeHistoricalAndFutureBrowsingSurvivesMidnightAndShortResume() {
        for (target in listOf(midnight - 86_400_000, midnight + 86_400_000)) {
            val manual = GuideTimeState(midnight, lastInteractionElapsed = 0).browse(target, 0)
            val active = manual.interacted(GuideTimeState.IDLE_RESET_MS - 1)
            assertEquals(target, active.refreshed(midnight + 10_000, GuideTimeState.IDLE_RESET_MS + 10_000).cursor)
            assertFalse(active.refreshed(midnight + 10_000, GuideTimeState.IDLE_RESET_MS + 10_000).followsNow)
        }
    }

    @Test fun nowImmediatelyResynchronizesDetailsWhileToolbarOwnsFocus() {
        val manual = GuideTimeState(midnight, lastInteractionElapsed = 0).browse(midnight - 10_000, 1)
        val current = programme(2, midnight, midnight + 60_000, "Current")
        val reset = manual.now(midnight + 1, 2)
        assertEquals(current, programmeAt(listOf(current), reset.cursor))
        assertTrue(reset.followsNow)
    }

    @Test fun refreshedRowsAndGapsNeverKeepStaleProgrammeObjects() {
        val old = programme(1, midnight, midnight + 60_000, "Old data")
        val updated = old.copy(title = "Fresh data", description = "Fresh description")
        assertEquals(updated, programmeAt(listOf(updated), midnight))
        assertNull(programmeAt(emptyList(), midnight))
        assertNull(programmeAt(null, midnight))
        assertNull(programmeAt(listOf(old), old.stopMs))
    }

    @Test fun wallClockChangesDoNotCountAsManualBrowseInactivity() {
        val manual = GuideTimeState(midnight, lastInteractionElapsed = 100).browse(midnight - 60_000, 100)
        assertEquals(manual, manual.refreshed(midnight + 86_400_000, 200))
        assertEquals(manual, manual.refreshed(midnight - 86_400_000, 200))
        assertEquals(midnight - 60_000, manual.refreshed(midnight, 100 + GuideTimeState.IDLE_RESET_MS - 1).cursor)
    }

    @Test fun fullDescriptionPreservesShiftedRowIntervalAndRejectsAnotherProgramme() {
        val row = programme(1, midnight, midnight + 60_000, "Current")
        val stored = row.copy(startMs = row.startMs - 3_600_000, stopMs = row.stopMs - 3_600_000, description = "Full details")
        val shown = guideProgrammeDetails(row, stored)!!
        assertEquals(row.startMs, shown.startMs)
        assertEquals(row.stopMs, shown.stopMs)
        assertEquals("Full details", shown.description)
        assertEquals(row, guideProgrammeDetails(row, stored.copy(id = 2)))
        assertNull(guideProgrammeDetails(null, stored))
    }

    @Test fun reorderedRowsKeepTheFocusedChannelAndRemovedRowsClampSafely() {
        val a = tv.own.owntv.core.database.entity.ChannelEntity(id = 1, sourceId = 1, name = "A", streamUrl = "")
        val b = a.copy(id = 2, name = "B")
        assertEquals(1, guideRowIndex(listOf(b, a), a.id, 0))
        assertEquals(0, guideRowIndex(listOf(b), a.id, 10))
        assertEquals(0, guideRowIndex(emptyList(), a.id, 10))
    }
}
