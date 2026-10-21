package tv.own.owntv.features.shell

import tv.own.owntv.core.nav.MainSection

/** TV presentation IDs only. MainSection names remain the persisted/deep-link contract. */
internal enum class TopDestination { HOME, TV, SPORT, VOD, PROFILE }

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
    TopDestination.PROFILE -> listOf(MainSection.MORE, MainSection.DOWNLOADS, MainSection.SETTINGS)
}

internal fun nativeEntry(destination: TopDestination, visible: Set<MainSection>, lastVod: MainSection): MainSection? =
    when (destination) {
        TopDestination.SPORT -> MainSection.HOME
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
