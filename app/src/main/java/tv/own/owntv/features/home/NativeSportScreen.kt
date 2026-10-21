package tv.own.owntv.features.home

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
internal fun NativeSportScreen(liveVm: LiveViewModel, profileId: Long?, active: Boolean,
    favorites: List<ChannelEntity>, onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onSources: () -> Unit, onSettings: (MinTvSettingsDestination) -> Unit,
    onEntryHook: ((() -> Boolean)?) -> Unit, onChildFocused: () -> Unit, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val usable = active && lifecycle == Lifecycle.State.RESUMED
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup()) {
        MinSportCompanion(true, usable, liveVm, profileId, favorites, onPlay, 64.mpx, onSources,
            reservedTop = 32.mpx, compact = false, onSettings = { onSettings(MinTvSettingsDestination.SPORT) },
            onMatchcenterEntry = onEntryHook) { hockey, details ->
            if (!details) Column(Modifier.fillMaxSize().padding(horizontal = 64.mpx, vertical = 32.mpx),
                verticalArrangement = Arrangement.spacedBy(24.mpx)) {
                TvText(stringResource(R.string.mintv_nav_sport), size = 36, bold = true)
                hockey()
                Box(Modifier.heightIn(max = 300.mpx)) {
                    TwitchCompanion(true, usable, compact = true,
                        onOpenSettings = { onSettings(MinTvSettingsDestination.TWITCH) })
                }
                HomeButton(stringResource(R.string.settings_title), { onSettings(MinTvSettingsDestination.SPORT) })
            }
        }
    }
}
