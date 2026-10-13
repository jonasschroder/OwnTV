package tv.own.owntv.features.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.launcher.LauncherContinuationKind
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.home.*
import tv.own.owntv.features.shell.components.VodCinematicBackdrop
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.features.shell.components.vodRating
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/** Only one bounded metadata record and artwork request per discovery screen. */
internal data class DiscoveryHero(val card: DiscoveryCard, val backdrop: String? = null,
    val overview: String? = null, val rating: Double? = card.rating, val ratingFromTmdb: Boolean = false)

internal object DiscoveryHeroSelection {
    private val imagePath = Regex("/[A-Za-z0-9_-]+\\.(?:jpg|png|webp)")
    fun backdrop(path: String?): String? = path?.takeIf { it.length <= 256 && imagePath.matches(it) }
        ?.let { "https://image.tmdb.org/t/p/w780$it" }
    fun synopsis(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }?.take(1600)
    fun rating(value: Double?): Double? = value?.takeIf { it.isFinite() && it in 0.1..10.0 }
    fun continuation(card: DiscoveryCard, items: List<LauncherContinuationItem>): LauncherContinuationItem? =
        items.firstOrNull { item ->
            if (card.type == MetadataType.MOVIE) item.kind == LauncherContinuationKind.MOVIE && item.targetItemId == card.id &&
                (LauncherDeepLink.parse(item.playbackUri) as? LauncherDeepLink.Movie)?.let {
                    it.itemId == card.id && it.sourceId == card.sourceId && "${it.sourceId}:${it.remoteId ?: it.name}" == card.stableKey
                } == true
            else item.kind == LauncherContinuationKind.EPISODE && (LauncherDeepLink.parse(item.playbackUri) as? LauncherDeepLink.Episode)
                ?.let { it.seriesItemId == card.id && it.seriesSourceId == card.sourceId &&
                    "${it.seriesSourceId}:${it.seriesRemoteId ?: it.seriesName}" == card.stableKey } == true
        }
}

@Composable
internal fun DiscoveryFeaturedHero(hero: DiscoveryHero, primaryLabel: String, onPrimary: () -> Unit,
    onInformation: () -> Unit, modifier: Modifier = Modifier, primaryModifier: Modifier = Modifier,
    informationModifier: Modifier = Modifier) {
    val card = hero.card
    Box(modifier.fillMaxWidth().height(480.mpx).clipToBounds().background(MinTvNavy).testTag("discovery-featured")) {
        if (hero.backdrop != null) VodCinematicBackdrop(hero.backdrop, card.type == MetadataType.TV, Modifier.align(Alignment.TopEnd))
        else Box(Modifier.align(Alignment.CenterEnd).width(220.mpx).height(330.mpx).padding(16.mpx)) {
            VodPosterArt(card.poster, if (card.type == MetadataType.MOVIE) OwnTVIcon.MOVIES else OwnTVIcon.SERIES)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(MinTvNavy, MinTvNavy.copy(alpha = 0.7f), MinTvNavy.copy(alpha = 0.05f)))))
        Column(Modifier.align(Alignment.CenterStart).width(830.mpx).fillMaxHeight().padding(24.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            TvText(stringResource(R.string.mintv_featured_available), color = MinTvTeal, size = 18, bold = true, lines = 1)
            TvText(card.name, size = 44, bold = true, lines = 2)
            val meta = listOfNotNull(card.year?.toString(), card.genres.take(3).takeIf { it.isNotEmpty() }
                ?.joinToString(stringResource(R.string.mintv_team_separator)), hero.rating?.let { vodRating(it) })
            if (meta.isNotEmpty()) TvText(meta.joinToString(stringResource(R.string.mintv_team_separator)), size = 21, color = MinTvMuted, lines = 1)
            hero.rating?.let { TvText(stringResource(if (hero.ratingFromTmdb) R.string.mintv_tmdb_rating else R.string.mintv_provider_rating), size = 16, color = MinTvMuted, lines = 1) }
            if (hero.overview != null) TvText(hero.overview, Modifier.weight(1f), size = 22, lines = 3)
            else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                HomeButton(primaryLabel, onPrimary, primaryModifier.testTag("discovery-featured-primary"), primary = true)
                HomeButton(stringResource(R.string.mintv_featured_information), onInformation, informationModifier.testTag("discovery-featured-information"))
            }
        }
    }
}
