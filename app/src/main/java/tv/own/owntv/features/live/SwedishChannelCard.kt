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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.tv.material3.*
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.components.ChannelLogoTile
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.mpx

/** The stored guide has no programme artwork contract. Never substitute an unrelated photo. */
@Composable
internal fun SwedishChannelCard(label: String, quality: ChannelQuality, info: SwedishRowInfo?, at: Long,
    onPlay: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, favorite: Boolean = false) {
    val current = info?.at(at)
    val time = rememberSystemTimeFormatter()
    Button(onClick = onPlay, modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(6.mpx),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.mpx)),
        colors = ButtonDefaults.colors(containerColor = MinTvPanel, contentColor = Color.White,
            focusedContainerColor = Color(0xFF203B38), focusedContentColor = Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
            Box(Modifier.fillMaxWidth().aspectRatio(if (compact) 16f / 7f else 16f / 9f)
                .neutralArtwork(), contentAlignment = Alignment.Center) {
                ChannelLogoTile(current?.logo, Modifier.fillMaxWidth(.65f).height(if (compact) 92.mpx else 108.mpx), fill = Color.Transparent) {
                    TvText(label, size = 30, bold = true, lines = 2)
                }
                if (favorite) {
                val favoriteLabel = stringResource(R.string.mintv_favorites_title)
                tv.own.owntv.ui.components.OwnTVIcon(tv.own.owntv.ui.components.OwnTVIcon.STAR,
                    MinTvTeal, Modifier.align(Alignment.TopStart).padding(8.mpx).size(20.mpx).semantics { contentDescription = favoriteLabel }, filled = true)
                }
                if (quality != ChannelQuality.UNKNOWN && !compact) TvText(quality.name,
                    Modifier.align(Alignment.TopEnd).padding(8.mpx).background(MinTvPanel.copy(alpha = .9f), RoundedCornerShape(6.mpx)).padding(horizontal = 7.mpx, vertical = 3.mpx),
                    size = 15, color = MinTvMuted, lines = 1)
                current?.now?.let { programme ->
                    ProgrammeProgress(programme.progress(at), Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(8.mpx))
                }
            }
            TvText(current?.now?.title ?: label, size = 23, bold = true, lines = if (compact) 1 else 2)
            if (!compact && current?.now != null) TvText(
                label + stringResource(R.string.mintv_v08_separator) + stringResource(R.string.content_live_time_range_plain, time(current.now.startMs), time(current.now.stopMs)),
                size = 17, color = MinTvMuted, lines = 1)
            if (current?.next != null) TvText(stringResource(R.string.mintv_live_next, time(current.next.startMs) + stringResource(R.string.mintv_v08_separator) + current.next.title),
                size = 17, color = MinTvMuted, lines = if (compact) 1 else 2)
            else TvText(stringResource(if (current?.now == null) R.string.mintv_live_no_programme else R.string.mintv_next_not_available), size = 17, color = MinTvMuted, lines = 1)

        }
    }
}
