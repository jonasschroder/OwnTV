package tv.own.owntv.features.discovery

import tv.own.owntv.features.catalog.readCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.trending.TrendingScheduleStore

/** Shared existing candidate cache and Room metadata/catalog index. No history leaves the device. */
internal class DiscoveryRepository(private val db: OwnTVDatabase, private val settings: SettingsRepository,
    private val customize: CustomizationStore, private val provider: MetadataProvider, private val schedule: TrendingScheduleStore) {
    private val mutex = Mutex()
    private var retryAt = 0L
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        if (!settings.metadataConfig().enabled) return@withLock true
        val now = System.currentTimeMillis()
        val stored = schedule.candidates()
        if (stored != null && now - stored.fetchedAt in 0 until 24 * 60 * 60_000L) return@withLock true
        if (now < retryAt) return@withLock false
        try {
            withTimeout(25_000) {
                val movies = provider.trendingPage(MetadataType.MOVIE, 1)
                val series = provider.trendingPage(MetadataType.TV, 1)
                if (movies == null || series == null) { retryAt = now + 15 * 60_000; return@withTimeout false }
                schedule.storeCandidates(TrendingScheduleStore.Candidates(settings.metadataConfig().resolvedLanguage, now,
                    movies.candidates.take(20), series.candidates.take(20), movies.totalPages, series.totalPages, 1, 1))
                true
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive(); retryAt = now + 15 * 60_000; false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { retryAt = now + 15 * 60_000; false }
    } }
    suspend fun shelf(profile: Long, sources: List<Long>, type: MetadataType): DiscoveryShelf = withContext(Dispatchers.IO) {
        if (profile < 0 || sources.isEmpty()) return@withContext DiscoveryShelf()
        val access = activeProfileSources(settings, db.sourceDao()).first()
        if (access.profileId != profile) return@withContext DiscoveryShelf()
        val allowed = if (type == MetadataType.MOVIE) access.movieSourceIds else access.seriesSourceIds
        val scopedSources = sources.filter { it in allowed }
        if (scopedSources.isEmpty()) return@withContext DiscoveryShelf()
        require(type in listOf(MetadataType.MOVIE, MetadataType.TV))
        val media = if (type == MetadataType.MOVIE) MediaType.MOVIE else MediaType.SERIES
        val table = if (type == MetadataType.MOVIE) "movies" else "series"
        val metadataType = if (type == MetadataType.MOVIE) "movie" else "tv"
        val owner = db.profileDao().getById(profile) ?: return@withContext DiscoveryShelf()
        val cust = customize.observe(profile, media).first()
        val cats = db.categoryDao().observe(scopedSources, media).first()
        val forbidden = cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories ||
            !tv.own.owntv.core.content.AdultCategoryClassifier.allows(profile, it.id, db.profileDao(), db.categoryDao()) }.map { it.id }.toSet()
        val config = settings.metadataConfig()
        val metadataEnabled = config.enabled
        val stored = schedule.candidates().takeIf { metadataEnabled }
        val candidates = (if (type == MetadataType.MOVIE) stored?.movies else stored?.series).orEmpty().take(20)
        val lang = config.resolvedLanguage
        val matchTail = if (owner.isKids) ":kids" else ""
        val cachePrefix = if (lang.isBlank()) "$metadataType:" else "$metadataType:$lang:"
        val marks = scopedSources.joinToString(",") { "?" }
        // Primary-key metadata lookup per catalog row; source/id keyset pages and no network enrichment.
        val join = " LEFT JOIN metadata_match m ON m.localKey = ('$metadataType:' || c.sourceId || ':' || COALESCE(c.remoteId,c.name) || '$matchTail') AND m.type = ? AND m.confidence >= 0.92 LEFT JOIN metadata_cache mc ON mc.key = (? || m.tmdbId)"
        val seedGenres = linkedSetOf<String>()
        db.readCatalog(
            "SELECT mc.genresJson FROM $table c$join WHERE c.sourceId IN ($marks) AND (EXISTS (SELECT 1 FROM watch_history h WHERE h.profileId=? AND h.mediaType=? AND h.itemId=c.id) OR EXISTS (SELECT 1 FROM favorites f WHERE f.profileId=? AND f.mediaType=? AND f.itemId=c.id)) ORDER BY c.id DESC LIMIT 24",
            (listOf<Any>(metadataType, cachePrefix) + scopedSources + listOf<Any>(profile, media.name, profile, media.name)).toTypedArray()) { cursor ->
            while (cursor.moveToNext()) if (metadataEnabled) seedGenres += genres(if (cursor.isNull(0)) null else cursor.getString(0))
        }
        val collector = DiscoveryAccumulator(candidates, seedGenres.take(8).toSet())
        var last = 0L; var scanned = 0; var ended = false
        while (scanned < 100_000) {
            currentCoroutineContext().ensureActive()
            var count = 0
            db.readCatalog(
                "SELECT c.id,c.sourceId,c.remoteId,c.name,c.posterUrl,c.year,c.addedAt,c.rating,c.categoryId,m.tmdbId,mc.genresJson,EXISTS (SELECT 1 FROM playback_progress p WHERE p.profileId=? AND p.mediaType=? AND p.itemId=c.id AND p.durationMs>0 AND p.positionMs >= p.durationMs * 0.9),mc.posterPath FROM $table c$join WHERE c.sourceId IN ($marks) AND c.id > ? ORDER BY c.id LIMIT 400",
                (listOf<Any>(profile, media.name, metadataType, cachePrefix) + scopedSources + last).toTypedArray()) { cursor ->
                while (cursor.moveToNext()) {
                    last = cursor.getLong(0); scanned++; count++
                    val name = cursor.getString(3)
                    val key = "${cursor.getLong(1)}:${if (cursor.isNull(2)) name else cursor.getString(2)}"
                    if (key in cust.hiddenItems || !cursor.isNull(8) && cursor.getLong(8) in forbidden) continue
                    val providerPoster = if (cursor.isNull(4)) null else cursor.getString(4).takeIf { it.isNotBlank() }
                    val cachedPoster = if (!metadataEnabled || cursor.isNull(12)) null else cursor.getString(12)
                        .takeIf { it.length <= 1024 && it.matches(Regex("/[A-Za-z0-9_./-]+")) && !it.contains("..") }
                        ?.let { "https://image.tmdb.org/t/p/w342$it" }
                    val poster = if (config.mode.tmdbWins) cachedPoster ?: providerPoster else providerPoster ?: cachedPoster
                    collector.add(DiscoveryCard(last, cursor.getLong(1), key, type, name,
                        poster, if (cursor.isNull(5)) null else cursor.getInt(5),
                        if (cursor.isNull(6)) null else cursor.getLong(6), if (cursor.isNull(7)) null else cursor.getDouble(7),
                        if (metadataEnabled) genres(if (cursor.isNull(10)) null else cursor.getString(10)) else emptySet(), if (cursor.isNull(9)) null else cursor.getInt(9), cursor.getInt(11) == 1))
                }
            }
            if (count < 400) { ended = true; break }
        }
        DiscoveryShelf(collector.rows(), stored?.fetchedAt, bounded = !ended)
    }
    suspend fun available(card: DiscoveryCard, profile: Long): Boolean = withContext(Dispatchers.IO) {
        val access = activeProfileSources(settings, db.sourceDao()).first()
        if (access.profileId != profile) return@withContext false
        val movie = card.type == MetadataType.MOVIE
        val media = if (movie) MediaType.MOVIE else MediaType.SERIES
        val allowed = if (movie) access.movieSourceIds else access.seriesSourceIds
        if (card.sourceId !in allowed) return@withContext false
        val entity = if (movie) db.movieDao().getById(card.id) else null
        val series = if (movie) null else db.seriesDao().getSeriesById(card.id)
        val source = entity?.sourceId ?: series?.sourceId ?: return@withContext false
        val key = entity?.let(CustomizeKeys::movie) ?: series?.let(CustomizeKeys::series)
        val category = entity?.categoryId ?: series?.categoryId
        if (source != card.sourceId || key != card.stableKey) return@withContext false
        if (!tv.own.owntv.core.content.AdultCategoryClassifier.allows(profile, category, db.profileDao(), db.categoryDao())) return@withContext false
        val cust = customize.observe(profile, media).first()
        if (key in cust.hiddenItems) return@withContext false
        category?.let { db.categoryDao().getById(it) }?.let { if (CustomizeKeys.category(it) in cust.hiddenCategories) return@withContext false }
        settings.activeProfileIdNow() == profile
    }
    private fun genres(raw: String?): Set<String> = runCatching {
        if (raw == null || raw.length > 4096) return emptySet()
        val rows = JSONArray(raw); (0 until minOf(rows.length(), 8)).map { rows.getString(it).take(80) }.toSet()
    }.getOrDefault(emptySet())
}
