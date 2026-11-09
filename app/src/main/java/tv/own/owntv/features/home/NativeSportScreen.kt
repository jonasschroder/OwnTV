package tv.own.owntv.features.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.features.sporthub.SportHubScreen
import tv.own.owntv.features.sporthub.*

/** A new destination composed from the existing singleton-backed companions, not new repositories. */
@Composable
internal fun NativeSportScreen(liveVm: LiveViewModel, homeVm: HomeViewModel, profileId: Long?, active: Boolean,
    onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onSources: () -> Unit, onSettings: (MinTvSettingsDestination) -> Unit,
    onEntryHook: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit = {}, onChildFocused: () -> Unit, modifier: Modifier = Modifier,
    restoreAfterPlayback: Boolean = false, onPlaybackReturnFinished: () -> Unit = {}) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val usable = active && lifecycle == Lifecycle.State.RESUMED
    val home by liveVm.nativeHomeFavorites.collectAsStateWithLifecycle()
    val library by liveVm.homeLibraryState.collectAsStateWithLifecycle()
    val favorites = home.channels.takeIf { home.profileId == profileId && library.profileId == profileId }.orEmpty()
        .filter { it.sourceId in library.sourceIds && library.visibleFavoriteIds?.contains(it.id) != false }
    var returnEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var baseEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var hubReturnEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var hubEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var restoreBase by remember(profileId) { mutableStateOf(false) }
    var hubOpen by rememberSaveable(profileId) { mutableStateOf(false) }
    var hubRoute by rememberSaveable(profileId) { mutableStateOf(HubRoute(HubPage.DISCOVER).key) }
    val hubReads by HubSessionSnapshots.reads.collectAsStateWithLifecycle()
    fun openHub(route: HubRoute) { restoreBase = false; hubRoute = route.key; hubOpen = true }
    LaunchedEffect(restoreBase, usable, hubOpen, returnEntry) {
        if (restoreBase && usable && !hubOpen && returnEntry != null) {
            repeat(2) { withFrameNanos { } }
            if (returnEntry?.invoke() == true) restoreBase = false
        }
    }
    DisposableEffect(profileId, hubOpen, baseEntry, hubEntry) {
        onEntryHook(if (hubOpen) hubEntry else baseEntry)
        onDispose { onEntryHook(null) }
    }
    tv.own.owntv.features.shell.NativePlaybackContentEntry(restoreAfterPlayback, profileId.toString(),
        if (hubOpen) hubReturnEntry else returnEntry, onHeader, onPlaybackReturnFinished)
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup()) {
        MinSportCompanion(true, usable && !hubOpen, liveVm, profileId, favorites, onPlay, 64.mpx, onSources,
            reservedTop = 32.mpx, compact = false, onSettings = { onSettings(MinTvSettingsDestination.SPORT) },
            dashboard = { data, actions, first, entry -> NativeSportDashboard(data, actions, first, entry, onHeader,
                onExplore = if (profileId != null) ({ openHub(HubRoute(HubPage.DISCOVER)) }) else null,
                hubReads = hubReads, onHubTeam = { openHub(HubRoute(HubPage.TEAM,it.league,it.id)) },
                onHubMatch = { openHub(HubRoute(HubPage.MATCH,it.league,it.id)) }, onHubMine = { openHub(HubRoute(HubPage.MINE)) }) },
            onMatchcenterEntry = { baseEntry = it }, onPlaybackReturnEntry = { returnEntry = it }) { hockey, _ -> hockey() }
        if (hubOpen && profileId != null) SportHubScreen(profileId, usable, liveVm, favorites, onPlay,
            { onSettings(MinTvSettingsDestination.SPORT) }, {
                restoreBase = true
                hubOpen = false
            }, { hubEntry = it }, onHeader, HubRoute.decode(hubRoute) ?: HubRoute(HubPage.DISCOVER),
            restoreAfterPlayback = restoreAfterPlayback, onPlaybackEntry = { hubReturnEntry = it })
    }
}
