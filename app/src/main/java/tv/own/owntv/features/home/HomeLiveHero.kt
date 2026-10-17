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
    Row(modifier.fillMaxWidth().focusGroup().clip(RoundedCornerShape(20.mpx))
        .background(Brush.horizontalGradient(listOf(Color(0xFF203743), Color(0xFF16252F))))
        .padding(horizontal = 26.mpx, vertical = 20.mpx).heightIn(min = 254.mpx),
        horizontalArrangement = Arrangement.spacedBy(28.mpx), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                logo?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.mpx)) }
                TvText(stringResource(if (channelName == null) R.string.mintv_home_live else R.string.mintv_v08_last_watched), size = 14, color = MinTvMuted, lines = 1)
                TvText(detailsLabel, Modifier.weight(1f, fill = false), size = 23, bold = true, lines = 1)
                if (channelName != null) TvText(stringResource(R.string.mintv_v08_live), size = 13, color = MinTvTeal, lines = 1)
            }
            TvText(current?.title ?: stringResource(if (channelName == null) R.string.mintv_v08_start_live else R.string.mintv_v08_empty_now),
                size = 34, bold = true, lines = 2)
            current?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    TvText(homeProgrammeTime(it.startMs, it.stopMs), size = 18, color = MinTvMuted, lines = 1)
                    val progress = ((now.toDouble() - it.startMs) / (it.stopMs.toDouble() - it.startMs)).toFloat()
                    ProgrammeProgress(progress, Modifier.weight(1f, fill = false).widthIn(max = 460.mpx).progressSemantics(progress))
                }
            }
            if (showVideo) nextTitle?.let { TvText(stringResource(R.string.mintv_next, it), size = 18, color = MinTvMuted, lines = 1) }
            Row(Modifier.padding(top = 8.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                DashboardAction(stringResource(if (channelName == null) R.string.mintv_home_live else R.string.mintv_v08_continue_live),
                    onWatch, firstAction, primary = true)
                DashboardAction(if (channelName == null) guideLabel else stringResource(R.string.mintv_home_live),
                    if (channelName == null) onGuide else onLive, compact = true)
                DashboardAction(moreLabel, { menu = true }, Modifier.focusRequester(more), compact = true)
            }
        }
        if (showVideo) Box(Modifier.width(436.mpx).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.mpx))
            .background(MinTvNavy), contentAlignment = Alignment.Center) {
            video()
            status?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MinTvNavy))).padding(12.mpx)) {
                    TvText(it, size = 15, color = MinTvMuted, lines = 1)
                }
            }
        }
        else nextTitle?.let { next ->
            // Actual stored programme metadata replaces the disabled player area. No new artwork read.
            Row(Modifier.width(360.mpx), horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(1.mpx).height(112.mpx).background(MinTvMuted.copy(alpha = .18f)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    TvText(stringResource(R.string.mintv_v08_up_next), size = 16, color = MinTvMuted, lines = 1)
                    TvText(next, size = 25, bold = true, lines = 3)
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
