package tv.own.owntv.features.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
    tv.own.owntv.features.shell.NativeContentEntry(if (restoreAfterPlayback) 1 else 0, profileId.toString(),
        { returnEntry?.invoke() == true }, onHeader, onPlaybackReturnFinished)
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup()) {
        MinSportCompanion(true, usable, liveVm, profileId, favorites, onPlay, 64.mpx, onSources,
            reservedTop = 32.mpx, compact = false, onSettings = { onSettings(MinTvSettingsDestination.SPORT) },
            dashboard = { data, actions, first, entry -> NativeSportDashboard(data, actions, first, entry, onHeader) },
            onMatchcenterEntry = onEntryHook, onPlaybackReturnEntry = { returnEntry = it }) { hockey, _ -> hockey() }

    }
}
