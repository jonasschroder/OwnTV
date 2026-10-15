package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import java.text.NumberFormat

/** Small static summaries; all evidence and actions are supplied by existing companions. */
@Composable
internal fun HockeyQuickCard(game: SportFixture?, now: Long, channel: String?, watch: Boolean,
    teams: String, onOpen: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier, cachedAt: Long? = null, manual: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(MinTvPanel, RoundedCornerShape(18.mpx)).padding(20.mpx),
        verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        TvText(stringResource(R.string.mintv_my_hockey), size = 18, color = MinTvTeal, bold = true, lines = 1)
        TvText(game?.let { pairLabel(it) } ?: teams.ifBlank { stringResource(R.string.mintv_choose_teams) }, size = 25, bold = true, lines = 1)
        TvText(game?.let { fixtureWhen(it, now) } ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 18, color = MinTvMuted, lines = 1)
        TvText(channel ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 19,
            color = if (channel == null) MinTvMuted else MinTvTeal, lines = 1)
        if (manual && channel != null) TvText(stringResource(R.string.mintv_hockey_manual_evidence), size = 16, color = MinTvMuted, lines = 1)
        cachedAt?.let { TvText(stringResource(R.string.mintv_cached_updated, updateLabel(it, now)), size = 16, color = MinTvMuted, lines = 1) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            DashboardAction(stringResource(if (watch) R.string.mintv_watch_match else R.string.mintv_my_hockey), onOpen, modifier)
            DashboardAction(stringResource(R.string.mintv_hockey_other_channels), onBrowse)
        }
    }
}

@Composable
internal fun TwitchQuickCard(state: TwitchState, connected: Boolean, onSettings: () -> Unit,
    onWatch: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().background(MinTvPanel, RoundedCornerShape(18.mpx)).padding(20.mpx),
        verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        TvText(stringResource(R.string.mintv_ohnepixel), size = 25, bold = true, lines = 1)
        TvText(when (state) {
            TwitchState.Offline -> stringResource(R.string.mintv_twitch_offline)
            TwitchState.Unavailable -> stringResource(R.string.mintv_companion_unavailable)
            is TwitchState.Live -> stringResource(R.string.mintv_v08_live)
        }, color = if (state is TwitchState.Live) MinTvTeal else MinTvMuted, size = 18, lines = 1)
        if (state is TwitchState.Live) {
            TvText(state.title, size = 21, lines = 1)
            state.category?.let { TvText(it, size = 18, color = MinTvMuted, lines = 1) }
            TvText(NumberFormat.getIntegerInstance().format(state.viewers), size = 17, color = MinTvMuted, lines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            DashboardAction(stringResource(if (connected) R.string.mintv_companion_settings else R.string.mintv_twitch_connect), onSettings, modifier)
            if (state is TwitchState.Live) DashboardAction(stringResource(R.string.mintv_twitch_watch), onWatch)
        }
    }
}
