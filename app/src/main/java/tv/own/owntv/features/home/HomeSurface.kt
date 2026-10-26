package tv.own.owntv.features.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import coil3.compose.AsyncImage
import tv.own.owntv.ui.theme.mpx

/** Home-only surfaces; no blur, per-frame bitmap work or shared player/settings restyling.
 * Native TV focus animations follow Compose's system motion-duration scale (including zero).
 */
internal val HomeSurfaceColor = Color(0xFF101C2A)
internal val HomeRadius = 18.mpx

@Composable
internal fun HomeSurface(onClick: () -> Unit, modifier: Modifier = Modifier, highlighted: Boolean = false, surfaceColor: Color = HomeSurfaceColor, focusedScale: Float = 1.025f,
    content: @Composable ColumnScope.() -> Unit) {
    Button(onClick, modifier, contentPadding = PaddingValues(horizontal = 18.mpx, vertical = 14.mpx),
        shape = ButtonDefaults.shape(RoundedCornerShape(HomeRadius)),
        colors = ButtonDefaults.colors(
            containerColor = if (highlighted) Color(0xFF152A32) else surfaceColor,
            contentColor = Color.White, focusedContainerColor = Color(0xFF243B4B), focusedContentColor = Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = focusedScale)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.mpx), content = content)
    }
}

@Composable
internal fun HomeChannelLogo(name: String, logo: String?, modifier: Modifier = Modifier) {
    var artworkLoaded by remember(logo) { mutableStateOf(false) }
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF223449), HomeSurfaceColor)), RoundedCornerShape(12.mpx)),
        contentAlignment = Alignment.Center) {
        if (!artworkLoaded) TvText(name.take(2), size = 23, color = MinTvTeal, lines = 1, bold = true)
        if (logo != null) AsyncImage(logo, null, contentScale = ContentScale.Fit,
            onSuccess = { artworkLoaded = true }, onError = { artworkLoaded = false },
            modifier = Modifier.fillMaxSize().padding(7.mpx))
    }
}

@Composable
internal fun HomeChannelTile(name: String, logo: String?, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    HomeSurface(onPlay, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            HomeChannelLogo(name, logo, Modifier.size(54.mpx))
            TvText(name, Modifier.weight(1f), size = 22, bold = true, lines = 2)
        }
    }
}
