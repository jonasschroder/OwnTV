package tv.own.owntv.features.home

import androidx.compose.foundation.background
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

/** Presentation only. The supplied video slot mounts the ONE existing Core preview surface. */
@Composable
internal fun HomeLiveHero(channelName: String?, logo: String?, programme: XtEpgEntry?, nextTitle: String?,
    now: Long, status: String?, onWatch: () -> Unit, onLive: () -> Unit, onGuide: () -> Unit,
    onSettings: () -> Unit, modifier: Modifier = Modifier, firstAction: Modifier = Modifier,
    video: @Composable BoxScope.() -> Unit) {
    val current = programme?.takeIf { now in it.startMs until it.stopMs }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.mpx))
        .background(Brush.horizontalGradient(listOf(Color(0xFF1C2B35), MinTvPanel))).padding(24.mpx),
        verticalArrangement = Arrangement.spacedBy(18.mpx)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(30.mpx), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.mpx))
                .background(Brush.verticalGradient(listOf(MinTvPanel, MinTvNavy))),
                contentAlignment = Alignment.Center) {
                video()
                status?.let { TvText(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(MinTvNavy.copy(alpha = 0.88f)).padding(16.mpx), size = 18, lines = 3) }
            }
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.mpx)) }
                    TvText(channelName ?: stringResource(R.string.mintv_just_now), size = 27, color = MinTvTeal, bold = true, lines = 1)
                }
                TvText(current?.title ?: stringResource(R.string.mintv_epg_unavailable), size = 34, bold = true, lines = 2)
                current?.let { ProgrammeProgress(((now - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1))) }
                current?.description?.takeIf(String::isNotBlank)?.let { HomeProgrammeDescription(it) }
                TvText(stringResource(R.string.mintv_next, nextTitle ?: stringResource(R.string.mintv_epg_unavailable)),
                    size = 20, color = MinTvMuted, lines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
            HomeButton(stringResource(if (channelName != null) R.string.mintv_watch_channel else R.string.mintv_home_live), onWatch, firstAction, primary = true)
            if (channelName != null) HomeButton(stringResource(R.string.mintv_home_live), onLive)
            HomeButton(stringResource(R.string.mintv_guide), onGuide)
            HomeButton(stringResource(R.string.mintv_companion_settings), onSettings)
            if (channelName != null) TvText(stringResource(R.string.mintv_ok_hint), Modifier.weight(1f), size = 17, color = MinTvMuted, lines = 2)
        }
    }
}
