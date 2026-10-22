package tv.own.owntv.features.shell

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.nav.MainSection

class NativeNavigationTest {
    private val all = MainSection.entries.toSet()
    @Test fun everyLegacyOwnerHasACanonicalHome() {
        assertEquals(TopDestination.HOME, topDestination(MainSection.HOME))
        assertEquals(TopDestination.TV, topDestination(MainSection.LIVE_TV))
        assertEquals(TopDestination.TV, topDestination(MainSection.EPG))
        assertEquals(TopDestination.VOD, topDestination(MainSection.MOVIES))
        assertEquals(TopDestination.VOD, topDestination(MainSection.SERIES))
        for (section in listOf(MainSection.MORE, MainSection.SETTINGS, MainSection.DOWNLOADS))
            assertEquals(TopDestination.PROFILE, topDestination(section))
        val aliases = TopDestination.entries.flatMap { nativeRoutes(it, all) }.toSet() + MainSection.SEARCH
        assertEquals(all, aliases)
    }
    @Test fun sportUsesPresentationOnlyAndHomeDeepLinkStillMapsHome() {
        assertEquals(MainSection.HOME, nativeEntry(TopDestination.SPORT, all, MainSection.MOVIES))
        assertEquals(TopDestination.SPORT, topDestination(MainSection.HOME, sport = true))
        assertEquals(TopDestination.HOME, topDestination(MainSection.HOME))
    }
    @Test fun hiddenMovieDoesNotReappearThroughCombinedVodTab() {
        assertEquals(listOf(MainSection.SERIES), nativeRoutes(TopDestination.VOD, all - MainSection.MOVIES))
        assertEquals(MainSection.SERIES, nativeEntry(TopDestination.VOD, all - MainSection.MOVIES, MainSection.MOVIES))
    }
    @Test fun lastVodOwnerIsRestoredOnlyWhileAvailable() {
        assertEquals(MainSection.SERIES, nativeEntry(TopDestination.VOD, all, MainSection.SERIES))
        assertEquals(MainSection.MOVIES, nativeEntry(TopDestination.VOD, all - MainSection.SERIES, MainSection.SERIES))
    }
    @Test fun guideSubviewIsRestoredWithoutReplacingLiveGuidePreference() {
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all, MainSection.MOVIES))
        assertEquals(MainSection.EPG, nativeEntry(TopDestination.TV, all, MainSection.MOVIES, MainSection.EPG))
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all - MainSection.EPG, MainSection.MOVIES, MainSection.EPG))
    }
    @Test fun guideOnlyAndEmptyTvDoNotUnhideLive() {
        assertEquals(MainSection.EPG, nativeEntry(TopDestination.TV, setOf(MainSection.EPG), MainSection.MOVIES))
        assertNull(nativeEntry(TopDestination.TV, emptySet(), MainSection.MOVIES))
        assertNull(nativeEntry(TopDestination.VOD, emptySet(), MainSection.MOVIES))
    }
    @Test fun profileAndSettingsRemainReachableWithNoContentOrNetwork() {
        assertEquals(listOf(MainSection.MORE, MainSection.DOWNLOADS, MainSection.SETTINGS),
            nativeRoutes(TopDestination.PROFILE, emptySet()))
        assertEquals(MainSection.MORE, nativeEntry(TopDestination.PROFILE, emptySet(), MainSection.MOVIES))
    }
    @Test fun backHierarchyTerminatesAndDoesNotSkipContentLevel() {
        for (destination in TopDestination.entries) assertEquals(NativeBack.HEADER, nativeBack(false, destination))
        for (destination in TopDestination.entries - TopDestination.HOME)
            assertEquals(NativeBack.HOME, nativeBack(true, destination))
        assertEquals(NativeBack.EXIT, nativeBack(true, TopDestination.HOME))
    }
    @Test fun adapterDoesNotMutateLegacyVisibilityOrPersistNewIds() {
        val hidden = mutableSetOf(MainSection.EPG, MainSection.MOVIES)
        val visible = all - hidden
        TopDestination.entries.forEach { nativeRoutes(it, visible); nativeEntry(it, visible, MainSection.SERIES) }
        assertEquals(setOf(MainSection.EPG, MainSection.MOVIES), hidden)
        assertFalse(nativeRoutes(TopDestination.TV, visible).contains(MainSection.EPG))
    }
}
