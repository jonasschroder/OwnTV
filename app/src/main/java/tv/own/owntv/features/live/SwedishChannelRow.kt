package tv.own.owntv.features.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.*
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.mpx

internal suspend fun restoreSwedishChannel(list: androidx.compose.foundation.lazy.LazyListState,
    groups: List<String>, identity: String?, editItems: Int,
    targets: Map<String, androidx.compose.ui.focus.FocusRequester>, entry: androidx.compose.ui.focus.FocusRequester) {
    if (groups.isNotEmpty()) {
        val index = groups.indexOf(identity).coerceAtLeast(0)
        list.scrollToItem(index + editItems)
        androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { }
        if (targets[groups[index]]?.requestSafely() == true) return
    }
    entry.requestSafely()
}

/** Shared production geometry, also exercised by synthetic TV instrumentation fixtures. */
@Composable
internal fun SwedishLiveFrame(modifier: Modifier = Modifier, header: @Composable RowScope.() -> Unit,
    categories: @Composable () -> Unit, notice: @Composable () -> Unit = {},
    channels: @Composable () -> Unit, preview: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().padding(top = 122.mpx, start = 30.mpx, end = 28.mpx, bottom = 24.mpx),
        verticalArrangement = Arrangement.spacedBy(16.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), content = header)
        categories()
        notice()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.mpx)) {
            Box(Modifier.weight(1f)) { channels() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.mpx), content = preview)
        }
    }
}

/** One TV focus target. Metadata/images never tune a channel; only OK uses the existing play callback. */
@Composable
internal fun SwedishChannelRow(label: String, quality: ChannelQuality, info: SwedishRowInfo?, at: Long,
    selected: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val current = info?.at(at)
    val time = rememberSystemTimeFormatter()
    Button(onClick = onPlay, modifier = modifier.fillMaxWidth().heightIn(min = 88.mpx),
        contentPadding = PaddingValues(horizontal = 12.mpx, vertical = 6.mpx),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.mpx)),
        colors = ButtonDefaults.colors(containerColor = if (selected) Color(0xFF1B3034) else MinTvPanel,
            contentColor = Color.White, focusedContainerColor = Color(0xFF253542)),
        border = ButtonDefaults.border(border = Border(BorderStroke(1.mpx, if (selected) MinTvTeal else Color.Transparent)),
            focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            ChannelLogoTile(current?.logo, Modifier.size(46.mpx), fill = Color.Transparent) {
                TvText(label.take(2), size = 18, bold = true, color = MinTvTeal, lines = 1)
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val showNext = maxWidth >= 240.mpx
                Column(verticalArrangement = Arrangement.spacedBy(3.mpx)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                        TvText(label, Modifier.weight(1f), size = 20, bold = true, lines = 1)
                        if (quality != ChannelQuality.UNKNOWN) TvText(quality.name, size = 14, color = MinTvMuted, lines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                        TvText(current?.now?.title ?: stringResource(R.string.mintv_live_no_programme), Modifier.weight(1f),
                            size = 19, color = if (current?.now != null) Color.White else MinTvMuted, lines = 1)
                        current?.now?.let { p -> TvText(stringResource(R.string.content_live_time_range_plain, time(p.startMs), time(p.stopMs)),
                            size = 14, color = MinTvMuted, lines = 1) }
                    }
                    if (showNext) {
                        current?.next?.let { TvText(stringResource(R.string.mintv_live_next, it.title), size = 14, color = MinTvMuted, lines = 1) }
                            ?: Spacer(Modifier.height(16.mpx))
                    }
                    Box(Modifier.fillMaxWidth().height(3.mpx).background(Color.White.copy(alpha = .09f), RoundedCornerShape(2.mpx))) {
                        current?.now?.let { p -> Box(Modifier.fillMaxWidth(p.progress(at)).fillMaxHeight()
                            .background(MinTvTeal.copy(alpha = if (selected) .9f else .55f), RoundedCornerShape(2.mpx))) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SwedishLiveAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Button(onClick, modifier.height(48.mpx), contentPadding = PaddingValues(horizontal = 16.mpx, vertical = 6.mpx),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.mpx)),
        colors = ButtonDefaults.colors(containerColor = if (selected) Color(0xFF234C45) else MinTvPanel,
            contentColor = Color.White, focusedContainerColor = Color(0xFF2B3B47)),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) { TvText(label, size = 18, lines = 1, bold = selected) }
}
