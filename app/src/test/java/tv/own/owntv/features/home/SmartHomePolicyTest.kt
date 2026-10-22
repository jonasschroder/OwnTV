package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class SmartHomePolicyTest {
    private val now = 10 * SMART_SERIES_WINDOW_MS
    private fun item(key: String = "episode:1", at: Long = now, position: Long = 1, duration: Long = 100,
        kind: SmartResumeKind = SmartResumeKind.EPISODE) = SmartResume(key, kind, 1, title = "Actual title",
        playedAt = at, position = position, duration = duration)
    @Test fun positiveActualProgressIsRequiredAndUnknownDurationIsNotInvented() {
        assertTrue(item().eligible())
        assertFalse(item(position = 0).eligible()); assertFalse(item(position = -1).eligible())
        assertFalse(item(duration = 0).eligible()); assertFalse(item(duration = -1).eligible())
    }
    @Test fun ninetyPercentBoundaryAndExplicitCompletionAreExcluded() {
        assertTrue(item(position = 89).eligible()); assertFalse(item(position = 90).eligible())
        assertFalse(item(position = 95).eligible()); assertFalse(item(position = 100).eligible())
        assertFalse(item().copy(completed = true).eligible())
        assertFalse(item(position = Long.MAX_VALUE, duration = Long.MAX_VALUE).eligible())
    }
    @Test fun exactIdentityDeduplicatesAndTimestampsUseDeterministicTies() {
        val a = item("a", at = now - 1); val b = item("b"); val c = item("c")
        assertEquals(listOf(b, c, a), smartResumeOrder(listOf(a, c, b, b.copy(playedAt = now - 10))))
        assertEquals(2, smartResumeOrder(listOf(a, a.copy(key = "other-provider-same-title"))).size)
    }
    @Test fun activeSeriesHasInclusiveSevenDayBoundaryAndMustBeNewerThanLive() {
        val boundary = item(at = now - SMART_SERIES_WINDOW_MS)
        assertEquals(boundary, smartHeroResume(listOf(boundary), boundary.playedAt - 1, now))
        assertNull(smartHeroResume(listOf(boundary.copy(playedAt = boundary.playedAt - 1)), 0, now))
        assertNull(smartHeroResume(listOf(boundary), boundary.playedAt, now))
        assertNull(smartHeroResume(listOf(item(at = now + 1)), 0, now))
    }
    @Test fun moreRecentMovieDoesNotPromoteAnOlderSeriesOverLive() {
        assertNull(smartHeroResume(listOf(item(at = now - 1), item("movie", kind = SmartResumeKind.MOVIE)), 0, now))
    }
    @Test fun noLiveHistoryUsesLatestEligibleResumeWithoutRequiringRecentSeries() {
        val movie = item(kind = SmartResumeKind.MOVIE, at = 1)
        assertEquals(movie, smartHeroResume(listOf(movie), null, now))
        assertNull(smartHeroResume(emptyList(), null, now))
    }
    @Test fun midnightDoesNotIntroduceCalendarDayAgeRounding() {
        val midnight = java.time.Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val episode = item(at = midnight - SMART_SERIES_WINDOW_MS)
        assertEquals(episode, smartHeroResume(listOf(episode), episode.playedAt - 1, midnight - 1))
        assertEquals(episode, smartHeroResume(listOf(episode), episode.playedAt - 1, midnight))
        assertNull(smartHeroResume(listOf(episode), episode.playedAt - 1, midnight + 1))
    }
    @Test fun logicalFocusSurvivesMetadataRefreshAndRemovedSourceChoosesAdjacent() {
        val before = listOf("svt1", "svt2", "tv3", "tv4")
        assertEquals("tv3", smartFocusKey("tv3", before, before))
        assertEquals("tv4", smartFocusKey("tv3", before, listOf("svt1", "svt2", "tv4")))
        assertEquals("svt2", smartFocusKey("tv3", before, listOf("svt1", "svt2")))
        assertEquals("svt1", smartFocusKey("missing", before, listOf("svt1")))
        assertNull(smartFocusKey("tv3", before, emptyList()))
    }
}
