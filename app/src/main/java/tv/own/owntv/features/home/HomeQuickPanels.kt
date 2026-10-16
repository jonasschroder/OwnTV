package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration

/** Supporting strips, not independent dashboard panels. Evidence/actions keep their existing owners. */
@Composable
internal fun HockeyQuickCard(game: SportFixture?, now: Long, channel: String?, watch: Boolean,
    teams: String, onOpen: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier, cachedAt: Long? = null, manual: Boolean = false, epgTime: Boolean = false) {
    val imminent = game != null && game.faceoff - now in -3 * 60 * 60_000L..12 * 60 * 60_000L
    Row(Modifier.fillMaxWidth().heightIn(min = 112.mpx)
        .background(if (imminent) Color(0xFF193037) else Color(0xFF17252D), RoundedCornerShape(16.mpx)).padding(14.mpx),
        horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                TvText(stringResource(R.string.mintv_my_hockey), Modifier.weight(1f), size = 15, color = MinTvTeal, bold = true, lines = 1)
                game?.let { TvText(fixtureWhen(it, now), size = 15, color = MinTvMuted, lines = 1) }
            }
            TvText(game?.let { pairLabel(it) } ?: teams.ifBlank { stringResource(R.string.mintv_choose_teams) }, size = 23, bold = true, lines = 1)
            TvText(channel ?: stringResource(if (game == null) R.string.mintv_v08_no_fixture else R.string.mintv_v08_channel_unconfirmed),
                size = 18, color = if (channel == null) MinTvMuted else MinTvTeal, lines = 1)
            val evidence = buildList {
                if (epgTime) add(stringResource(R.string.mintv_hockey_epg_time))
                if (manual && channel != null) add(stringResource(R.string.mintv_hockey_manual_evidence))
                cachedAt?.let { add(stringResource(R.string.mintv_cached_updated, updateLabel(it, now))) }
            }
            if (evidence.isNotEmpty()) TvText(evidence.joinToString(stringResource(R.string.mintv_v08_separator)), size = 14, color = MinTvMuted, lines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            DashboardAction(stringResource(if (watch) R.string.mintv_watch_match else R.string.mintv_my_hockey), onOpen, modifier, compact = true)
            DashboardAction(stringResource(R.string.mintv_hockey_other_channels), onBrowse, compact = true)
        }
    }
}

@Composable
internal fun TwitchQuickCard(state: TwitchState, connected: Boolean, onSettings: () -> Unit,
    onWatch: () -> Unit, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().heightIn(min = 112.mpx).background(Color(0xFF151E2C), RoundedCornerShape(16.mpx)).padding(14.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.mpx)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                TvText(stringResource(R.string.mintv_ohnepixel), Modifier.weight(1f), size = 22, bold = true, lines = 1)
                if (state is TwitchState.Live) TvText(stringResource(R.string.mintv_v08_live), size = 13, color = MinTvTeal, lines = 1)
            }
            when (state) {
                TwitchState.Offline -> TvText(stringResource(R.string.mintv_twitch_offline), size = 17, color = MinTvMuted, lines = 1)
                TwitchState.Unavailable -> TvText(stringResource(R.string.mintv_companion_unavailable), size = 17, color = MinTvMuted, lines = 1)
                is TwitchState.Live -> {
                    TvText(state.title, size = 19, lines = 1)
                    val viewers = stringResource(R.string.mintv_v08_viewers, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(state.viewers))
                    TvText(state.category?.let { stringResource(R.string.mintv_v08_variant, it, viewers) } ?: viewers, size = 15, color = MinTvMuted, lines = 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            DashboardAction(stringResource(if (connected) R.string.mintv_companion_settings else R.string.mintv_twitch_connect), onSettings, modifier, compact = true)
            if (state is TwitchState.Live) DashboardAction(stringResource(R.string.mintv_twitch_watch), onWatch, compact = true)
        }
    }
}
