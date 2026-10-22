package tv.own.owntv.features.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.catalog.readCatalog

/** Same Room connection and exact-item DAOs. At most 96 progress candidates; no provider/metadata API.
 * Core's launcher feed applies a 10-second floor and manufactures NEXT items after completion.
 * Home's approved policy needs positive actual progress and exact episodes, so it reads those rows
 * directly without changing the shared launcher's semantics or persisted completion data.
 */
internal suspend fun readSmartResumes(db: OwnTVDatabase, settings: SettingsRepository,
    custom: CustomizationStore, profile: Long, exact: SmartResume? = null): List<SmartResume> = withContext(Dispatchers.IO) {
    val movies = activeSourceIds(settings, db.sourceDao(), profile, MediaType.MOVIE).toSet()
    val series = activeSourceIds(settings, db.sourceDao(), profile, MediaType.SERIES).toSet()
    if (movies.isEmpty() && series.isEmpty()) return@withContext emptyList()
    readSmartResumesForSources(db, custom, profile, movies, series, exact)
}

internal suspend fun readSmartResumesForSources(db: OwnTVDatabase, custom: CustomizationStore,
    profile: Long, movieSources: Set<Long>, seriesSources: Set<Long>, exact: SmartResume? = null): List<SmartResume> = withContext(Dispatchers.IO) {
    val linked = db.sourceDao().observeForProfile(profile).first()
    val movies = movieSources.intersect(linked.filter { it.syncMovies }.map { it.id }.toSet())
    val series = seriesSources.intersect(linked.filter { it.syncSeries }.map { it.id }.toSet())
    if (movies.isEmpty() && series.isEmpty()) return@withContext emptyList()
    val customMovie = custom.observe(profile, MediaType.MOVIE).first()
    val customSeries = custom.observe(profile, MediaType.SERIES).first()
    data class Progress(val type: String, val id: Long, val position: Long, val duration: Long, val at: Long)
    val movieSlots = movies.ifEmpty { setOf(-1L) }.joinToString(",") { "?" }
    val seriesSlots = series.ifEmpty { setOf(-1L) }.joinToString(",") { "?" }
    val candidates = if (exact == null) db.readCatalog(
        "SELECT p.mediaType,p.itemId,p.positionMs,p.durationMs,p.updatedAt FROM playback_progress p LEFT JOIN movies m ON p.mediaType='MOVIE' AND p.itemId=m.id LEFT JOIN episodes e ON p.mediaType='EPISODE' AND p.itemId=e.id LEFT JOIN series s ON e.seriesId=s.id WHERE p.profileId=? AND p.positionMs>0 AND p.durationMs>0 AND CAST(p.positionMs AS REAL)/p.durationMs<0.9 AND (m.sourceId IN ($movieSlots) OR s.sourceId IN ($seriesSlots)) ORDER BY p.updatedAt DESC,p.mediaType,p.itemId LIMIT 96",
        (listOf(profile) + movies.ifEmpty { setOf(-1L) } + series.ifEmpty { setOf(-1L) }).toTypedArray()) { cursor -> buildList {
            while (cursor.moveToNext()) add(Progress(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3), cursor.getLong(4)))
        } } else {
        val type = if (exact.kind == SmartResumeKind.MOVIE) MediaType.MOVIE else MediaType.EPISODE
        db.progressDao().get(profile, type, exact.itemId)?.let {
            listOf(Progress(type.name, it.itemId, it.positionMs, it.durationMs, it.updatedAt))
        }.orEmpty()
    }
    val out = ArrayList<SmartResume>()
    for (p in candidates) {
        if (p.type == MediaType.MOVIE.name) {
            val movie = db.movieDao().getById(p.id) ?: continue
            if (exact != null && movie.remoteId == null && movie.name != exact.title) continue
            if (movie.sourceId !in movies || movie.streamUrl.isBlank() || CustomizeKeys.movie(movie) in customMovie.hiddenItems) continue
            val category = movie.categoryId?.let { db.categoryDao().getById(it) }
            if (category?.let { CustomizeKeys.category(it) in customMovie.hiddenCategories } == true ||
                !AdultCategoryClassifier.allows(profile, movie.categoryId, db.profileDao(), db.categoryDao())) continue
            out += SmartResume("movie:${movie.sourceId}:${movie.remoteId ?: movie.id}", SmartResumeKind.MOVIE, movie.id,
                title = movie.name, poster = movie.posterUrl, playedAt = p.at, position = p.position, duration = p.duration)
        } else {
            val episode = db.seriesDao().getEpisodeById(p.id) ?: continue
            val show = db.seriesDao().getSeriesById(episode.seriesId) ?: continue
            if (exact != null && (episode.seriesId != exact.seriesId ||
                episode.remoteId == null && episode.name.ifBlank { show.name } != exact.title ||
                show.remoteId == null && show.name != exact.seriesTitle)) continue
            if (show.sourceId !in series || episode.streamUrl.isBlank() || CustomizeKeys.series(show) in customSeries.hiddenItems) continue
            val category = show.categoryId?.let { db.categoryDao().getById(it) }
            if (category?.let { CustomizeKeys.category(it) in customSeries.hiddenCategories } == true ||
                !AdultCategoryClassifier.allows(profile, show.categoryId, db.profileDao(), db.categoryDao())) continue
            out += SmartResume("episode:${show.sourceId}:${show.remoteId ?: show.id}:${episode.remoteId ?: episode.id}", SmartResumeKind.EPISODE,
                episode.id, show.id, episode.name.ifBlank { show.name }, show.name, episode.seasonNumber, episode.episodeNumber,
                show.posterUrl, p.at, p.position, p.duration)
        }
    }
    smartResumeOrder(out).filter { exact == null || it.key == exact.key }
}
