package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.theme.mpx
import java.text.DateFormat
import java.util.Date

/** Two-column presentation; the right slot mounts only the existing preview surface. */
@Composable
internal fun HomeLiveHero(channelName: String?, logo: String?, programme: XtEpgEntry?, nextTitle: String?,
    now: Long, status: String?, onWatch: () -> Unit, onLive: () -> Unit, onGuide: () -> Unit,
    onSettings: () -> Unit, modifier: Modifier = Modifier, firstAction: Modifier = Modifier,
    onVariants: (() -> Unit)? = null, video: @Composable BoxScope.() -> Unit) {
    val current = programme?.takeIf { now in it.startMs until it.stopMs }
    Row(modifier.fillMaxWidth().focusGroup().clip(RoundedCornerShape(22.mpx))
        .background(Brush.horizontalGradient(listOf(Color(0xFF172C3D), MinTvPanel))),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(.95f).padding(28.mpx), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            TvText(stringResource(R.string.mintv_v08_last_watched), size = 17, color = MinTvTeal, bold = true, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(46.mpx)) }
                TvText(channelName ?: stringResource(R.string.mintv_v08_no_history), size = 28, bold = true, lines = 1)
                if (channelName != null) TvText(stringResource(R.string.mintv_v08_live), size = 15, color = MinTvTeal, bold = true, lines = 1)
            }
            TvText(current?.title ?: stringResource(if (channelName == null) R.string.mintv_v08_start_live else R.string.mintv_epg_unavailable),
                size = 36, bold = true, lines = 2)
            current?.let {
                TvText(homeProgrammeTime(it.startMs, it.stopMs), size = 18, color = MinTvMuted, lines = 1)
                ProgrammeProgress(((now.toDouble() - it.startMs) / (it.stopMs.toDouble() - it.startMs)).toFloat())
                it.description?.takeIf(String::isNotBlank)?.let { description -> HomeProgrammeDescription(description) }
            }
            nextTitle?.let { TvText(stringResource(R.string.mintv_next, it), size = 18, color = MinTvMuted, lines = 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                DashboardAction(stringResource(if (channelName == null) R.string.mintv_home_live else R.string.mintv_v08_continue_live),
                    onWatch, firstAction, primary = true)
                DashboardAction(stringResource(R.string.mintv_home_live), onLive)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                DashboardAction(stringResource(R.string.mintv_guide), onGuide)
                onVariants?.let { DashboardAction(stringResource(R.string.mintv_v08_source_quality), it) }
            }
        }
        Box(Modifier.weight(1f).padding(16.mpx).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.mpx))
            .background(MinTvNavy), contentAlignment = Alignment.Center) {
            video()
            status?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MinTvNavy))).padding(16.mpx)) {
                    TvText(it, size = 17, color = MinTvMuted, lines = 2)
                }
            }
        }
    }
}

@Composable
internal fun homeProgrammeTime(start: Long, stop: Long): String {
    val format = DateFormat.getTimeInstance(DateFormat.SHORT, LocalConfiguration.current.locales[0])
    return stringResource(R.string.mintv_v08_time_range, format.format(Date(start)), format.format(Date(stop)))
}
