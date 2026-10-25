package tv.own.owntv.features.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.*
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.mpx

/** The stored guide has no programme artwork contract. Never substitute an unrelated photo. */
@Composable
internal fun SwedishChannelCard(label: String, quality: ChannelQuality, info: SwedishRowInfo?, at: Long,
    onPlay: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val current = info?.at(at)
    val time = rememberSystemTimeFormatter()
    Button(onClick = onPlay, modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(8.mpx),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.mpx)),
        colors = ButtonDefaults.colors(containerColor = MinTvPanel, contentColor = Color.White,
            focusedContainerColor = Color(0xFF203B38), focusedContentColor = Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.mpx)) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (compact) 16f / 7f else 16f / 9f).background(
                Brush.verticalGradient(listOf(Color(0xFF203D43), Color(0xFF101E29))), RoundedCornerShape(8.mpx)),
                contentAlignment = Alignment.Center) {
                ChannelLogoTile(current?.logo, Modifier.size(84.mpx), fill = Color.Transparent) {
                    TvText(label, size = 25, bold = true, lines = 2)
                }
                current?.now?.let { programme ->
                    ProgrammeProgress(programme.progress(at), Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(8.mpx))
                }
            }
            TvText(current?.now?.title ?: label, size = 23, bold = true, lines = if (compact) 1 else 2)
            if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                TvText(label, modifier = Modifier.weight(1f), size = 18, color = MinTvMuted, lines = 1)
                if (quality != ChannelQuality.UNKNOWN) TvText(quality.name, size = 18, color = MinTvMuted, lines = 1)
            }
            if (!compact) TvText(current?.now?.let { stringResource(R.string.content_live_time_range_plain, time(it.startMs), time(it.stopMs)) }
                ?: stringResource(R.string.mintv_live_no_programme), size = 17, color = MinTvMuted, lines = 1)
            TvText(current?.next?.let { stringResource(R.string.mintv_live_next, it.title) }
                ?: stringResource(R.string.mintv_live_no_programme), size = 17, color = MinTvMuted, lines = if (compact) 1 else 2)
        }
    }
}
