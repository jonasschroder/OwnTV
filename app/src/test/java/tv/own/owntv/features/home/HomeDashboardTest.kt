package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class HomeDashboardTest {
    private val watched = ChannelEntity(id = 1, sourceId = 101, name = "TV4 HD", streamUrl = "https://example.invalid/watched")
    @Test fun heroUsesActualHistoryInsteadOfFavoriteOrFocusedChannel() {
        val favorite = watched.copy(id = 2, name = "SVT1")
        assertEquals(watched, homeHistoryCandidate(HomeUiState(profileId = 7, recentLive = listOf(watched), favoriteLive = listOf(favorite)), 7, listOf(101)))
        assertNull(homeHistoryCandidate(HomeUiState(profileId = 7, favoriteLive = listOf(favorite)), 7, listOf(101)))
    }
    @Test fun heroKeepsActualWatchUnlessItsOwnPickerChoosesAnAccessibleAlternative() {
        val other = watched.copy(id = 3, sourceId = 102, name = "TV4 SD")
        assertEquals(watched, homeHeroChannel(watched, listOf(other), null))
        assertEquals(other, homeHeroChannel(watched, listOf(other), other.homeEpgKey().stableKey))
        assertEquals(watched, homeHeroChannel(watched, emptyList(), other.homeEpgKey().stableKey))
        assertNull(homeHeroChannel(null, listOf(other), other.homeEpgKey().stableKey))
    }
    @Test fun historyFailsClosedForOtherProfileSourceAndEmptyStream() {
        val state = HomeUiState(profileId = 7, recentLive = listOf(watched))
        assertNull(homeHistoryCandidate(state, 8, listOf(101)))
        assertNull(homeHistoryCandidate(state, 7, listOf(102)))
        assertNull(homeHistoryCandidate(state.copy(recentLive = listOf(watched.copy(streamUrl = ""))), 7, listOf(101)))
        assertNotEquals(watched.homeEpgKey(), watched.copy(sourceId = 102).homeEpgKey())
        assertNotEquals(watched.homeEpgKey(), watched.copy(remoteId = "other").homeEpgKey())
    }
    @Test fun reorderedSectionsFollowActualLayoutAndSkipSharedCompanionRow() {
        val rows = listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.NOW, HomeSection.CONTINUE, HomeSection.FAVORITES)
        val indices = dashboardIndices(rows, true, true)
        assertEquals(9, nextHomeSection(0, -1, indices))
        assertEquals(3, nextHomeSection(0, 1, indices))
        assertEquals(indices[3], indices[6])
        assertEquals(8, nextHomeSection(6, 1, indices))
        assertEquals(2, nextHomeSection(8, 1, indices))
        assertEquals(1, nextHomeSection(2, 1, indices))
        assertNull(nextHomeSection(1, 1, indices))
        assertEquals(6, nextHomeSection(0, 1, dashboardIndices(rows, false, true)))
    }
    @Test fun legacyOrderAddsNewSectionsWithoutResettingExistingOrder() {
        val order = normalizedHomeOrder(listOf(HomeSection.MOVIES, HomeSection.CONTINUE, HomeSection.MOVIES))
        assertEquals(listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.TWITCH), order.take(3))
        assertEquals(listOf(HomeSection.MOVIES, HomeSection.CONTINUE), order.drop(3).take(2))
        assertEquals(HomeSection.entries.toSet(), order.toSet())
        assertEquals(HomeSection.entries.size, order.size)
        assertEquals(defaultHomeOrder, normalizedHomeOrder(emptyList()))
    }
}
