package tv.own.owntv.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

internal val MinTvNavy = tv.own.owntv.ui.theme.StageSurfaces.Background
internal val MinTvTeal = tv.own.owntv.ui.theme.StageSurfaces.Cyan
internal val MinTvMuted = tv.own.owntv.ui.theme.StageColors.Muted
internal val MinTvPanel = tv.own.owntv.ui.theme.StageSurfaces.Panel

@Composable
internal fun TvText(value: String, modifier: Modifier = Modifier, size: Int = 22, color: Color = Color.White,
    lines: Int = 2, bold: Boolean = false, minLines: Int = 1) {
    Text(value, modifier = modifier, style = stageText(size, if (bold) 700 else 500), color = color,
        minLines = minLines, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** Dark, inexpensive TV cards: focus changes outline/colour without relayout or new surfaces. */
@Composable
internal fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier,
        contentPadding = PaddingValues(20.mpx),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.mpx)),
        colors = ButtonDefaults.colors(containerColor = if (selected) Color(0xFF1B3034) else MinTvPanel,
            contentColor = Color.White, focusedContainerColor = Color(0xFF253542), focusedContentColor = Color.White),
        border = ButtonDefaults.border(border = Border(BorderStroke(1.mpx, if (selected) MinTvTeal else Color.Transparent)),
            focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1.015f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.mpx), content = content)
    }
}

@Composable
internal fun ChannelCard(name: String, logo: String?, detail: String?, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, progress: Float? = null) {
    TvCard(onClick, modifier, selected) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
            Box(Modifier.size(68.mpx).background(Color(0xFF0D141E), RoundedCornerShape(12.mpx)), contentAlignment = Alignment.Center) {
                if (logo != null) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.mpx))
                else TvText(name.take(2), size = 22, color = MinTvTeal, lines = 1, bold = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                TvText(name, size = 24, bold = true, lines = 2)
                detail?.let { TvText(it, size = 19, color = MinTvMuted, lines = 1) }
            }
        }
        progress?.let { ProgrammeProgress(it) }
    }
}

@Composable
internal fun ProgrammeProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.mpx).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.mpx))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MinTvTeal, RoundedCornerShape(2.mpx)))
    }
}

/** Readable table/disclosure rows can receive D-pad focus and scroll into view without fake actions. */
@Composable
internal fun FocusPanel(highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(if (highlighted) Color(0xFF1B3034) else MinTvPanel, RoundedCornerShape(12.mpx))
        .onFocusChanged { focused = it.isFocused }.border(if (focused) 3.mpx else 1.mpx, if (focused) MinTvTeal else Color.Transparent,
            RoundedCornerShape(12.mpx)).focusable().padding(14.mpx), content = content)
}
