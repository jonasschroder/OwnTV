package tv.own.owntv.features.shell

import tv.own.owntv.core.nav.MainSection

/** TV presentation IDs only. MainSection names remain the persisted/deep-link contract. */
internal enum class TopDestination { HOME, TV, SPORT, VOD, PROFILE }

/** Stable installs can retain the v0.9 LiveView.GUIDE preference. The native TV
 * destination is always the channel grid, while EPG is a separately reachable
 * section. Never use that legacy preference to choose the native screen.
 */
internal enum class NativeTvSurface { CHANNEL_GRID, PROGRAMME_GUIDE }
internal fun nativeTvSurface(section: MainSection): NativeTvSurface? = when (section) {
    MainSection.LIVE_TV -> NativeTvSurface.CHANNEL_GRID
    MainSection.EPG -> NativeTvSurface.PROGRAMME_GUIDE
    else -> null
}

internal fun topDestination(section: MainSection, sport: Boolean = false): TopDestination = when (section) {
    MainSection.HOME -> if (sport) TopDestination.SPORT else TopDestination.HOME
    MainSection.LIVE_TV, MainSection.EPG -> TopDestination.TV
    MainSection.MOVIES, MainSection.SERIES -> TopDestination.VOD
    MainSection.DOWNLOADS, MainSection.SETTINGS, MainSection.MORE -> TopDestination.PROFILE
    MainSection.SEARCH -> TopDestination.TV // caller keeps its contextual parent while search is open
}

/** Empty capability is deliberately different from an explicit hidden navigation preference. */
internal fun nativeRoutes(destination: TopDestination, visible: Set<MainSection>): List<MainSection> = when (destination) {
    TopDestination.HOME -> listOf(MainSection.HOME).filter { it in visible }
    TopDestination.TV -> listOf(MainSection.LIVE_TV, MainSection.EPG).filter { it in visible }
    TopDestination.SPORT -> emptyList() // the existing companion owns this presentation-only route
    TopDestination.VOD -> listOf(MainSection.MOVIES, MainSection.SERIES).filter { it in visible }
    TopDestination.PROFILE -> listOf(MainSection.SETTINGS, MainSection.MORE, MainSection.DOWNLOADS)
}

internal fun nativeEntry(destination: TopDestination, visible: Set<MainSection>, lastVod: MainSection, lastTv: MainSection = MainSection.LIVE_TV): MainSection? =
    when (destination) {
        TopDestination.SPORT -> MainSection.HOME
        // A native TV tab must not strand an upgraded user in the old guide just
        // because LIVE_TV was hidden in legacy navigation preferences.
        TopDestination.TV -> if (MainSection.LIVE_TV in visible || MainSection.EPG in visible) MainSection.LIVE_TV else null
        TopDestination.VOD -> nativeRoutes(destination, visible).let { lastVod.takeIf(it::contains) ?: it.firstOrNull() }
        else -> nativeRoutes(destination, visible).firstOrNull()
    }

/** One finite shell level. Child dialogs/details/player handlers run before the shell fallback. */
internal enum class NativeBack { HEADER, HOME, EXIT }
internal fun nativeBack(headerFocused: Boolean, destination: TopDestination): NativeBack = when {
    !headerFocused -> NativeBack.HEADER
    destination != TopDestination.HOME -> NativeBack.HOME
    else -> NativeBack.EXIT
}
