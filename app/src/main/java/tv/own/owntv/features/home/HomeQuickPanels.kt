package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalConfiguration

/** Supporting strips, not independent dashboard panels. Evidence/actions keep their existing owners. */
@Composable
internal fun HockeyQuickCard(game: SportFixture?, now: Long, channel: String?, watch: Boolean,
    teams: String, onOpen: () -> Unit, modifier: Modifier = Modifier, cachedAt: Long? = null, manual: Boolean = false, epgTime: Boolean = false, onDetails: () -> Unit = onOpen, hero: Boolean = false) {
    if (hero) {
        Column(Modifier.fillMaxWidth().heightIn(min = 252.mpx)
            .background(Brush.horizontalGradient(listOf(Color(0xFF17352E), Color(0xFF15282C))), RoundedCornerShape(20.mpx)).padding(24.mpx),
            verticalArrangement = Arrangement.spacedBy(6.mpx)) {
            TvText(game?.competition?.name ?: stringResource(R.string.mintv_my_hockey), size = 18, color = MinTvTeal, bold = true)
            TvText(game?.let { pairLabel(it) } ?: teams.ifBlank { stringResource(R.string.mintv_choose_teams) }, size = 52, bold = true, lines = 2)
            game?.let { TvText(fixtureWhen(it, now), size = 21, color = MinTvMuted, lines = 1) }
            TvText(channel ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 20, color = MinTvMuted, lines = 1)
            if (manual && channel != null) TvText(stringResource(R.string.mintv_hockey_manual_evidence), size = 16, color = MinTvMuted, lines = 1)
            if (epgTime) TvText(stringResource(R.string.mintv_hockey_epg_time), size = 16, color = MinTvMuted, lines = 1)
            SmartAction(stringResource(if (watch) R.string.mintv_watch_match else R.string.mintv_v011_browse_hockey), onOpen, modifier, primary = true)
        }
        return
    }
    val imminent = game != null && game.faceoff - now in -3 * 60 * 60_000L..12 * 60 * 60_000L
    Row(Modifier.fillMaxWidth().background(if (imminent) Color(0xFF1B3034) else HomeSurfaceColor, RoundedCornerShape(HomeRadius))
        .padding(end = if (watch) 14.mpx else 0.mpx),
        horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
    HomeSurface(onDetails, modifier.weight(1f).heightIn(min = (if (hero) 252 else 128).mpx), highlighted = imminent) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                TvText(if (hero && game != null) game.competition.name else stringResource(R.string.mintv_my_hockey), Modifier.weight(1f), size = 15, color = MinTvTeal, bold = true, lines = 1)
                game?.let { TvText(fixtureWhen(it, now), size = 15, color = MinTvMuted, lines = 1) }
            }
            TvText(game?.let { pairLabel(it) } ?: teams.ifBlank { stringResource(R.string.mintv_choose_teams) }, size = if (hero) 52 else 22, bold = true, lines = if (hero) 2 else 1)
            TvText(channel ?: stringResource(if (game == null) R.string.mintv_v08_no_fixture else R.string.mintv_v08_channel_unconfirmed),
                size = 18, color = if (channel == null) MinTvMuted else MinTvTeal, lines = 1)
            val evidence = buildList {
                if (epgTime) add(stringResource(R.string.mintv_hockey_epg_time))
                if (manual && channel != null) add(stringResource(R.string.mintv_hockey_manual_evidence))
                cachedAt?.let { add(stringResource(R.string.mintv_cached_updated, updateLabel(it, now))) }
            }
            if (evidence.isNotEmpty()) TvText(evidence.joinToString(stringResource(R.string.mintv_v08_separator)), size = 14, color = MinTvMuted, lines = 1)
        }
    }
    }
    if (watch) DashboardAction(stringResource(R.string.mintv_watch_match), onOpen, compact = true)
    }
}

@Composable
internal fun TwitchQuickCard(state: TwitchState, onOpen: () -> Unit, modifier: Modifier = Modifier, authenticated: Boolean = true) {
    HomeSurface(onOpen, modifier.fillMaxWidth().heightIn(min = 190.mpx), surfaceColor = Color(0xFF221D32)) {
        tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.HEADPHONES, Color(0xFFC1A8EE), Modifier.size(36.mpx))
        Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
                TvText(stringResource(R.string.mintv_ohnepixel), Modifier.weight(1f), size = 22, bold = true, lines = 1)
                if (state is TwitchState.Live) TvText(stringResource(R.string.mintv_v08_live), size = 13, color = MinTvTeal, lines = 1)
            }
            if (!authenticated && state == TwitchState.Unavailable) TvText(stringResource(R.string.mintv_v011_signed_out), size = 17, color = MinTvMuted, lines = 2)
            else when (state) {
                TwitchState.Offline -> TvText(stringResource(R.string.mintv_twitch_offline), size = 17, color = MinTvMuted, lines = 1)
                TwitchState.Unavailable -> TvText(stringResource(R.string.mintv_companion_unavailable), size = 17, color = MinTvMuted, lines = 1)
                is TwitchState.Live -> {
                    TvText(state.title, size = 19, lines = 1)
                    val viewers = stringResource(R.string.mintv_v08_viewers, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(state.viewers))
                    TvText(state.category?.let { stringResource(R.string.mintv_v08_variant, it, viewers) } ?: viewers, size = 15, color = MinTvMuted, lines = 1)
                }
            }
        }
        TvText(stringResource(R.string.mintv_twitch_open_app), size = 16, color = MinTvTeal, bold = true, lines = 1)
    }
}
