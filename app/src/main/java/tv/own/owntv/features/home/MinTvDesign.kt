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

internal val MinTvNavy = Color(0xFF101820)
internal val MinTvTeal = Color(0xFF69DCC5)
internal val MinTvMuted = Color(0xFFAFBDC8)

@Composable
internal fun TvText(value: String, modifier: Modifier = Modifier, size: Int = 22, color: Color = Color.White,
    lines: Int = 2, bold: Boolean = false) {
    Text(value, modifier = modifier, style = stageText(size, if (bold) 700 else 500), color = color,
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** Low-cost rectangular TV card. Focus changes border/colour without layout or decoder changes. */
@Composable
internal fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier,
        contentPadding = PaddingValues(20.mpx),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.mpx)),
        colors = ButtonDefaults.colors(containerColor = if (selected) Color(0xFF223D3D) else Color(0xFF1B2732),
            contentColor = Color.White, focusedContainerColor = Color(0xFF294348), focusedContentColor = Color.White),
        border = ButtonDefaults.border(border = Border(BorderStroke(1.mpx, if (selected) MinTvTeal else Color.Transparent)),
            focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1.02f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.mpx), content = content)
    }
}

@Composable
internal fun ChannelCard(name: String, logo: String?, detail: String?, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false) {
    TvCard(onClick, modifier, selected) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
            Box(Modifier.size(58.mpx).background(Color(0xFF111C25), RoundedCornerShape(8.mpx)), contentAlignment = Alignment.Center) {
                if (logo != null) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(5.mpx))
                else TvText(name.take(2), size = 22, color = MinTvTeal, lines = 1, bold = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                TvText(name, size = 23, bold = true)
                detail?.let { TvText(it, size = 18, color = MinTvMuted, lines = 1) }
            }
        }
    }
}

/** Readable table/disclosure rows can receive D-pad focus and scroll into view without fake actions. */
@Composable
internal fun FocusPanel(highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(if (highlighted) Color(0xFF223D3D) else Color(0xFF1B2732), RoundedCornerShape(10.mpx))
        .onFocusChanged { focused = it.isFocused }.border(if (focused) 3.mpx else 1.mpx, if (focused) MinTvTeal else Color.Transparent,
            RoundedCornerShape(10.mpx)).focusable().padding(18.mpx), content = content)
}
