package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.ui.components.OwnTVIcon
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.discovery.*
import tv.own.owntv.ui.theme.mpx

internal data class NativeVodSnapshot(val profile: Long?, val movies: List<DiscoveryCard>, val series: List<DiscoveryCard>,
    val resumes: List<SmartResume>, val loading: Boolean = false)
internal fun nativeVodPreview(shelf: DiscoveryShelf): List<DiscoveryCard> = shelf.rows.flatMap { it.cards }.distinctBy { it.stableKey }.take(3)

/** Both catalogue entry points are real focus targets, even before any source has loaded. */
@Composable
internal fun NativeVodLanding(data: NativeVodSnapshot, first: FocusRequester, onEntry: ((() -> Boolean)?) -> Unit,
    onHeader: () -> Unit, onLibrary: (MetadataType) -> Unit, onTitle: (DiscoveryCard) -> Unit,
    onResume: (SmartResume) -> Unit, modifier: Modifier = Modifier) {
    val rows = buildList {
        add(NativeRow("vod-movies", listOf("vod-movies-entry") + data.movies.map { "vod-movie-${it.stableKey}" }))
        add(NativeRow("vod-series", listOf("vod-series-entry") + data.series.map { "vod-series-${it.stableKey}" }))
        if (data.resumes.isNotEmpty()) add(NativeRow("vod-continue", data.resumes.take(3).map { "vod-resume-${it.key}" }))
    }
    val nav = rememberNativeRowsNavigation(rows, data.profile, first, onEntry, onHeader)
    BoxWithConstraints(modifier.fillMaxSize().background(DesignLabBackground)) {
        val entryWidth = (maxWidth - 128.mpx) * .185f
        val resumeWidth = (maxWidth - 164.mpx) / 3
        val cardWidth = (maxWidth - 128.mpx - entryWidth - 54.mpx) / 3
        LazyColumn(state = nav.list, contentPadding = PaddingValues(horizontal = 64.mpx, vertical = 28.mpx),
            verticalArrangement = Arrangement.spacedBy(24.mpx), modifier = Modifier.fillMaxSize().then(nav.modifier).focusGroup()) {
            item(key = "vod-movies") {
                Column(verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                    TvText(stringResource(R.string.mintv_nav_vod), size = 34, bold = true)
                    TvText(stringResource(R.string.mintv_v011_vod_hint), size = 20, color = MinTvMuted, lines = 2)
                    if (data.loading || data.movies.isEmpty() && data.series.isEmpty()) TvText(
                        stringResource(if (data.loading) R.string.mintv_companion_loading else R.string.mintv_discovery_empty),
                        size = 20, color = MinTvMuted, lines = 2)
                    VodLandingRail(MetadataType.MOVIE, data.movies, nav, entryWidth, cardWidth, onLibrary, onTitle)
                }
            }
            item(key = "vod-series") { VodLandingRail(MetadataType.TV, data.series, nav, entryWidth, cardWidth, onLibrary, onTitle) }
            if (data.resumes.isNotEmpty()) item(key = "vod-continue") {
                Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    TvText(stringResource(R.string.search_continue), size = 27, bold = true)
                    LazyRow(state = nav.rails.getValue("vod-continue"), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
                        items(data.resumes.take(3), key = { it.key }) { item ->
                            SmartResumeCard(item, { onResume(item) }, nav.item("vod-resume-${item.key}").width(resumeWidth))
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun VodLandingRail(type: MetadataType, cards: List<DiscoveryCard>, nav: NativeRowsNavigation,
    entryWidth: androidx.compose.ui.unit.Dp, cardWidth: androidx.compose.ui.unit.Dp,
    onLibrary: (MetadataType) -> Unit, onTitle: (DiscoveryCard) -> Unit) {
    val movie = type == MetadataType.MOVIE
    val row = if (movie) "vod-movies" else "vod-series"
    LazyRow(state = nav.rails.getValue(row), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
        item {
            HomeSurface({ onLibrary(type) }, nav.item("$row-entry").testTag("$row-entry").width(if (cards.isEmpty()) entryWidth + cardWidth * 3 + 54.mpx else entryWidth).heightIn(min = (if (cards.isEmpty()) 150 else 205).mpx), focusedScale = 1f) {
                Row(Modifier.fillMaxWidth().heightIn(min = (if (cards.isEmpty()) 122 else 177).mpx).neutralArtwork().padding(16.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                    TvText(stringResource(if (movie) R.string.common_nav_movies else R.string.common_nav_series), size = 37, bold = true)
                    TvText(stringResource(if (movie) R.string.mintv_all_movies else R.string.mintv_all_series), size = 21, color = MinTvTeal, lines = 2)
                }
                if (cards.isEmpty()) OwnTVIcon(if (movie) OwnTVIcon.MOVIES else OwnTVIcon.SERIES, MinTvTeal.copy(alpha = .45f), Modifier.padding(24.mpx).size(64.mpx))
                }
            }
        }
        items(cards, key = { it.stableKey }) { card ->
            HomeSurface({ onTitle(card) }, nav.item(if (movie) "vod-movie-${card.stableKey}" else "vod-series-${card.stableKey}")
                .testTag(if (movie) "vod-movie-${card.stableKey}" else "vod-series-${card.stableKey}").width(cardWidth)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 6f).neutralArtwork(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        OwnTVIcon(if (movie) OwnTVIcon.MOVIES else OwnTVIcon.SERIES, MinTvTeal.copy(alpha = .6f), Modifier.size(44.mpx))
                        card.poster?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    TvText(card.name, size = 24, bold = true, lines = 2)
                    card.year?.let { TvText(it.toString(), size = 18, color = MinTvMuted, lines = 1) }
                }
            }
        }

    }
}
