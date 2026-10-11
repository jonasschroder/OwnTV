package tv.own.owntv.features.metadata

import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.metadata.TitleNormalizer

/** Guard the pinned normalizer when it would discard meaningful title content.
 * No provider title is rewritten and no override is inserted/deleted automatically. */
internal object ConservativeTitles {
    private val prefix = Regex("(?i)^\\s*(?:SE|EN|SV|NO|DK|FI|DE|FR|4K|FHD|UHD|HD)\\s*[|:]\\s*")
    private val tags = Regex("(?i)[\\[(](?:4K|FHD|UHD|HD|SD|HDR|HDR10|HEVC|H264|H265|X264|X265|MULTI-SUB|DUAL-AUDIO|1080p|720p)[\\])]")
    private val yearTag = Regex("[\\[(](19[0-9]{2}|20[0-9]{2})[\\])]\\s*$")
    fun safeForAutoMatch(raw: String): Boolean {
        var safe = raw.trim()
        do { val previous = safe; safe = safe.replace(prefix, ""); if (safe == previous) break } while (true)
        safe = safe.replace(tags, " ").replace(yearTag, " ").replace(Regex("\\s+"), " ").trim()
        val legacy = TitleNormalizer.normalize(raw).query
        return safe.isNotBlank() && safe.equals(legacy, ignoreCase = true)
    }
}

internal suspend fun MetadataRepository.resolveSafeMovie(movie: MovieEntity) =
    withSafeMetadataSearch(movie.name, { movieOverride(movie) != null }) { resolveMovie(movie) }

internal suspend fun MetadataRepository.resolveSafeSeries(series: SeriesEntity) =
    withSafeMetadataSearch(series.name, { seriesOverride(series) != null }) { resolveSeries(series) }

internal suspend fun MetadataRepository.cachedSafeMoviePosters(movies: List<MovieEntity>) =
    cachedMoviePosters(movies)

internal suspend fun MetadataRepository.cachedSafeSeriesPosters(series: List<SeriesEntity>) =
    cachedSeriesPosters(series)

internal suspend fun MetadataRepository.resolveSafeEpisode(series: SeriesEntity, episode: EpisodeEntity) =
    withSafeMetadataSearch(series.name, { seriesOverride(series) != null }) { resolveEpisode(series, episode) }

internal suspend fun MetadataRepository.cachedSafeSeasonEpisodes(series: SeriesEntity, episodes: List<EpisodeEntity>) =
    cachedSeasonEpisodes(series, episodes)

internal suspend fun MetadataRepository.resolveSafeSeasonEpisodes(series: SeriesEntity, episodes: List<EpisodeEntity>) =
    withSafeMetadataSearch(series.name, { seriesOverride(series) != null }) { resolveSeasonEpisodes(series, episodes) }

/** Block only a NEW automatic search, not existing matches, ID details or cache-only lookups.
 * Coroutine context follows Core's IO work without leaking into another profile/request. */
internal class MetadataSearchAbstention : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<MetadataSearchAbstention>
}

internal suspend fun <T> withSafeMetadataSearch(raw: String, hasOverride: suspend () -> Boolean,
    resolve: suspend () -> T): T =
    if (ConservativeTitles.safeForAutoMatch(raw) || hasOverride()) resolve()
    else withContext(MetadataSearchAbstention()) { resolve() }
