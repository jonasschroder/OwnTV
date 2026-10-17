package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

/** Local PackageManager discovery only; refreshes once on resume and on explicit recheck. */
@Composable
internal fun TwitchPlaybackSettings(playersProvider: () -> List<TwitchPlayer>, selectedPackage: String?, active: Boolean,
    onSelect: (String?) -> Unit, onInstall: (String) -> Boolean) {
    var players by remember { mutableStateOf<List<TwitchPlayer>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var storeFailed by remember { mutableStateOf(false) }
    val provider by rememberUpdatedState(playersProvider)
    LaunchedEffect(active, revision) {
        if (!active) return@LaunchedEffect
        loaded = false; failed = false
        players = withContext(Dispatchers.IO) { runCatching { provider() }.getOrElse { failed = true; emptyList() } }
        loaded = true
    }
    TvText(stringResource(R.string.mintv_setup_player_heading), size = 26, bold = true)
    TvText(stringResource(R.string.mintv_setup_player_help), color = MinTvMuted, lines = 5)
    val selected = players.firstOrNull { it.component.packageName == selectedPackage }
    TvText(stringResource(R.string.mintv_setup_player_selected, selected?.name ?: stringResource(
        if (selectedPackage == null) R.string.mintv_setup_player_auto else R.string.mintv_setup_player_missing)), lines = 2)
    if (loaded && players.isEmpty()) TvText(stringResource(R.string.mintv_twitch_no_player), color = MinTvMuted, lines = 4)
    if (failed) TvText(stringResource(R.string.mintv_twitch_launch_failed), color = MinTvMuted, lines = 3)
    HomeButton(stringResource(R.string.mintv_setup_player_auto), { onSelect(null) })
    players.forEach { player ->
        HomeButton(stringResource(if (player.direct) R.string.mintv_setup_player_use_direct else R.string.mintv_setup_player_use_manual, player.name),
            { onSelect(player.component.packageName) })
    }
    if (players.any { !it.direct }) TvText(stringResource(R.string.mintv_twitch_manual_help), color = MinTvMuted, lines = 3)
    HomeButton(stringResource(R.string.mintv_setup_player_recheck), { revision++ })
    supportedTwitchClients.forEach { client ->
        HomeButton(stringResource(R.string.mintv_setup_player_install, stringResource(client.nameRes)), { storeFailed = !onInstall(client.packageName) })
    }
    if (storeFailed) TvText(stringResource(R.string.mintv_setup_store_unavailable), color = MinTvMuted, lines = 4)
}
