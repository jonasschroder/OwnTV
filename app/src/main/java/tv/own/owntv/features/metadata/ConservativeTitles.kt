package tv.own.owntv.features.metadata

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
    if (ConservativeTitles.safeForAutoMatch(movie.name) || movieOverride(movie) != null) resolveMovie(movie) else null

internal suspend fun MetadataRepository.resolveSafeSeries(series: SeriesEntity) =
    if (ConservativeTitles.safeForAutoMatch(series.name) || seriesOverride(series) != null) resolveSeries(series) else null

internal suspend fun MetadataRepository.cachedSafeMoviePosters(movies: List<MovieEntity>) =
    cachedMoviePosters(movies.filter { ConservativeTitles.safeForAutoMatch(it.name) || movieOverride(it) != null })

internal suspend fun MetadataRepository.cachedSafeSeriesPosters(series: List<SeriesEntity>) =
    cachedSeriesPosters(series.filter { ConservativeTitles.safeForAutoMatch(it.name) || seriesOverride(it) != null })

internal suspend fun MetadataRepository.resolveSafeEpisode(series: SeriesEntity, episode: EpisodeEntity) =
    if (ConservativeTitles.safeForAutoMatch(series.name) || seriesOverride(series) != null) resolveEpisode(series, episode) else null

internal suspend fun MetadataRepository.cachedSafeSeasonEpisodes(series: SeriesEntity, episodes: List<EpisodeEntity>) =
    if (ConservativeTitles.safeForAutoMatch(series.name) || seriesOverride(series) != null) cachedSeasonEpisodes(series, episodes) else emptyMap()

internal suspend fun MetadataRepository.resolveSafeSeasonEpisodes(series: SeriesEntity, episodes: List<EpisodeEntity>) =
    if (ConservativeTitles.safeForAutoMatch(series.name) || seriesOverride(series) != null) resolveSeasonEpisodes(series, episodes) else emptyMap()
