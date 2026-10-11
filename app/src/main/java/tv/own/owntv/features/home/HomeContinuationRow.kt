package tv.own.owntv.features.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherContinuationKind
import tv.own.owntv.ui.stage.StageStill
import tv.own.owntv.ui.theme.mpx

internal fun homeContinuations(state: HomeUiState, profileId: Long?): List<LauncherContinuationItem> =
    if (state.profileId != profileId) emptyList() else (state.continueMovies + state.continueSeries)
        .filter { it.kind != LauncherContinuationKind.LIVE }.distinctBy { it.stableKey }
        .sortedByDescending { it.lastEngagementAt }.take(8)

/** Existing profile history and still-image cards. Focus never starts a VOD decoder. */
@Composable
internal fun HomeContinuationRow(items: List<LauncherContinuationItem>, first: Modifier,
    onPlay: (LauncherContinuationItem) -> Unit) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        TvText(stringResource(R.string.search_continue), size = 26, bold = true, lines = 1)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(6.mpx), modifier = Modifier.focusGroup()) {
            itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                StageStill(title = item.title, line = item.subtitle, onClick = { onPlay(item) },
                    width = 310.mpx, height = 174.mpx, titleSize = 22, lineSize = 18,
                    progress = item.durationMs.takeIf { it > 0 }?.let { item.positionMs.toFloat() / it },
                    modifier = if (index == 0) first else Modifier) {
                    AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
