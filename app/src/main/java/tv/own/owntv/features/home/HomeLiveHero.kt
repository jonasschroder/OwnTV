package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.mpx
import java.text.DateFormat
import java.util.Date

/** Presentation only. A video slot exists solely while the existing engine has a real preview. */
@Composable
internal fun HomeLiveHero(channelName: String?, logo: String?, programme: XtEpgEntry?, nextTitle: String?,
    now: Long, status: String?, onWatch: () -> Unit, onLive: () -> Unit, onGuide: () -> Unit,
    onSettings: () -> Unit, modifier: Modifier = Modifier, firstAction: Modifier = Modifier,
    onVariants: (() -> Unit)? = null, showVideo: Boolean = true, sourceName: String? = null,
    onMenuVisibility: (Boolean) -> Unit = {}, video: @Composable BoxScope.() -> Unit) {
    val current = programme?.takeIf { now in it.startMs until it.stopMs }
    var menu by remember { mutableStateOf(false) }
    var restoreMenu by remember { mutableStateOf(false) }
    val more = remember { FocusRequester() }
    val menuFirst = remember { FocusRequester() }
    val notifyMenu by rememberUpdatedState(onMenuVisibility)
    LaunchedEffect(menu) { notifyMenu(menu) }
    DisposableEffect(Unit) { onDispose { notifyMenu(false) } }
    LaunchedEffect(menu, restoreMenu) {
        if (!menu && restoreMenu) {
            withFrameNanos { }; withFrameNanos { }
            more.requestSafely(); restoreMenu = false
        }
    }
    val guideLabel = stringResource(R.string.mintv_guide)
    val variantsLabel = stringResource(R.string.mintv_v08_source_quality)
    val customizeLabel = stringResource(R.string.mintv_v08_customize)
    val moreLabel = stringResource(R.string.mintv_v08_more)
    val closeLabel = stringResource(R.string.mintv_close)
    val detailsLabel = channelName ?: stringResource(R.string.mintv_v08_no_history)
    BoxWithConstraints(modifier.fillMaxWidth().focusGroup().clip(RoundedCornerShape(HomeRadius))
        .background(Brush.horizontalGradient(listOf(Color(0xFF172C3D), Color(0xFF101B2A), MinTvNavy)))) {
        val compact = maxWidth < 1200.mpx
        // Reuse the channel's existing artwork. No programme artwork exists in the stored EPG
        // projection; do not manufacture a poster or fetch a new image source for the hero.
        if (!showVideo && logo != null) AsyncImage(logo, null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterEnd).width(if (compact) 220.mpx else 340.mpx)
                .height(180.mpx).padding(24.mpx).graphicsLayer { alpha = .12f })
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.mpx, vertical = if (compact) 16.mpx else 22.mpx),
            horizontalArrangement = Arrangement.spacedBy(26.mpx), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.mpx)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    if (logo != null) AsyncImage(logo, null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.mpx))
                    TvText(stringResource(if (channelName == null) R.string.mintv_home_live else R.string.mintv_v08_last_watched), size = 14, color = MinTvMuted, lines = 1)
                    TvText(detailsLabel, Modifier.weight(1f, fill = false), size = 22, bold = true, lines = 1)
                    if (channelName != null) TvText(stringResource(R.string.mintv_v08_live), size = 13, color = MinTvTeal, lines = 1)
                }
                TvText(current?.title ?: stringResource(if (channelName == null) R.string.mintv_v08_start_live else R.string.mintv_v08_empty_now),
                    size = if (compact) 29 else 36, bold = true, lines = 2)
                current?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                        TvText(homeProgrammeTime(it.startMs, it.stopMs), size = 17, color = MinTvMuted, lines = 1)
                        val progress = ((now.toDouble() - it.startMs) / (it.stopMs.toDouble() - it.startMs)).toFloat().coerceIn(0f, 1f)
                        ProgrammeProgress(progress, Modifier.weight(1f, fill = false).widthIn(max = 360.mpx).progressSemantics(progress))
                    }
                }
                nextTitle?.let { TvText(stringResource(R.string.mintv_next, it), size = 17, color = MinTvMuted, lines = 1) }
                Row(Modifier.padding(top = 5.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                    DashboardAction(stringResource(if (channelName == null) R.string.mintv_home_live else R.string.mintv_v08_continue_live),
                        onWatch, firstAction, primary = true, compact = compact)
                    DashboardAction(if (channelName == null) guideLabel else stringResource(R.string.mintv_home_live),
                        if (channelName == null) onGuide else onLive, compact = true)
                    DashboardAction(moreLabel, { menu = true }, Modifier.focusRequester(more), compact = true)
                }
            }
            // Only the existing, active preview engine may populate this slot.
            if (showVideo) Box(Modifier.width(if (compact) 320.mpx else 400.mpx).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.mpx)).background(MinTvNavy), contentAlignment = Alignment.Center) {
                video()
                status?.let {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MinTvNavy))).padding(12.mpx)) {
                        TvText(it, size = 15, color = MinTvMuted, lines = 1)
                    }
                }
            }
        }
    }
    if (menu) {
        fun choose(action: () -> Unit) { menu = false; action() }
        StagePopup(onDismiss = { menu = false; restoreMenu = true }, title = detailsLabel, width = 680.mpx,
            buttons = { DashboardAction(closeLabel, { menu = false; restoreMenu = true }, compact = true) }) {
            sourceName?.let { TvText(it, size = 17, color = MinTvMuted, lines = 1) }
            current?.description?.takeIf(String::isNotBlank)?.let { HomeProgrammeDescription(it, Modifier.padding(bottom = 14.mpx)) }
            Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                DashboardAction(guideLabel, { choose(onGuide) }, Modifier.fillMaxWidth().focusRequester(menuFirst))
                onVariants?.let { DashboardAction(variantsLabel, { choose(it) }, Modifier.fillMaxWidth()) }
                DashboardAction(customizeLabel, { choose(onSettings) }, Modifier.fillMaxWidth())
            }
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; menuFirst.requestSafely() }
        }
    }
}

@Composable
internal fun homeProgrammeTime(start: Long, stop: Long): String {
    val format = DateFormat.getTimeInstance(DateFormat.SHORT, LocalConfiguration.current.locales[0])
    return stringResource(R.string.mintv_v08_time_range, format.format(Date(start)), format.format(Date(stop)))
}
