package tv.own.owntv.features.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler

internal enum class HomeLibraryStatus { LOADING, NO_SOURCE, NO_CHANNELS, NO_FAVORITES, READY }
internal data class HomeLibraryState(val profileId: Long = -1, val status: HomeLibraryStatus = HomeLibraryStatus.LOADING,
    val sourceIds: List<Long> = emptyList(), val visibleFavoriteIds: Set<Long>? = null)

internal fun homeLibraryStatus(hasSources: Boolean, loading: Boolean, hasChannels: Boolean, hasFavorites: Boolean): HomeLibraryStatus = when {
    loading -> HomeLibraryStatus.LOADING
    !hasSources -> HomeLibraryStatus.NO_SOURCE
    !hasChannels -> HomeLibraryStatus.NO_CHANNELS
    !hasFavorites -> HomeLibraryStatus.NO_FAVORITES
    else -> HomeLibraryStatus.READY
}

internal fun initialHockeyChannel(channels: List<ChannelEntity>): ChannelEntity? = channels
    .filter { BroadcastResolver.channelKey(it.name) == "TV4 HOCKEY" && it.streamUrl.isNotBlank() && !BroadcastResolver.eventPlaceholder(it.name) }
    .sortedWith(compareBy<ChannelEntity> { channel ->
        // Only prefer quality that the provider actually names; do not probe or open streams.
        val tokens = channel.name.uppercase(java.util.Locale.ROOT).split(Regex("[^A-Z0-9]+"))
        when { "HD" in tokens -> 0; "FHD" in tokens -> 1; else -> 2 }
    }.thenBy { it.sourceId }.thenBy { it.sortOrder }.thenBy { it.id }).firstOrNull()

/** Favorites stay in Core. This small durable claim records only whether the initial offer was consumed. */
class HomeChannelDefaults(context: Context, private val db: OwnTVDatabase, private val settings: SettingsRepository,
    private val scheduler: CatalogSyncScheduler) {
    private val claims = context.getSharedPreferences("mintv-initial-live-favorite", Context.MODE_PRIVATE)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal fun observe(visibilityChanges: Flow<Unit>, visible: suspend (ChannelEntity) -> Boolean): Flow<HomeLibraryState> = activeProfileSources(settings, db.sourceDao())
        .flatMapLatest { aps ->
            val ids = aps.liveSourceIds
            val sync = if (ids.isEmpty()) flowOf(false) else combine(ids.map(scheduler::observeSync)) { states -> states.any { it.isActive } }
            val initialGrace = flow { emit(true); kotlinx.coroutines.delay(30_000); emit(false) }
            combine(db.channelDao().countAll(ids.ifEmpty { listOf(-1L) }), db.favoriteDao().observeFavoriteIds(aps.profileId, MediaType.LIVE), sync, visibilityChanges, initialGrace) { _, favorites, syncing, _, grace -> Triple(syncing, grace, favorites.isNotEmpty()) }
                .mapLatest { (syncing, grace, existingFavorites) ->
                    val profileId = aps.profileId
                    if (profileId < 0 || settings.activeProfileId.first() != profileId) return@mapLatest HomeLibraryState()
                    // Initial imports publish lastSyncAt only after the complete channel import.
                    val incomplete = aps.sources.filter { it.syncLive }.any { it.lastSyncAt == null }
                    // Consume an existing list even while import is incomplete: removing it later is intent.
                    if (existingFavorites) {
                        try { seed(profileId, ids, null, visible) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Failed claims never mutate favorites. */ }
                    }
                    val loading = syncing || (incomplete && grace)
                    if (loading) return@mapLatest HomeLibraryState(profileId, sourceIds = ids)
                    val candidates = db.channelDao().searchListDetailedFts("TV4 AND HOCKEY", ids.ifEmpty { listOf(-1L) }, 129).map { it.channel }
                    // If truncated, leave the choice to the browser instead of claiming a preferred variant.
                    if (!incomplete) {
                        try { seed(profileId, ids, initialHockeyChannel(candidates.takeIf { it.size < 129 }.orEmpty().filter { visible(it) }), visible) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Optional default failed closed; keep the manual channel browser available. */ }
                    }
                    // Find existence with bounded pages, including hidden/adult policies. No provider requests.
                    val pages = db.channelDao().pagingAll(ids.ifEmpty { listOf(-1L) })
                    val hasChannels = try {
                        var offset = 0
                        var found = false
                        while (!found) {
                            val page = pages.load(androidx.paging.PagingSource.LoadParams.Refresh(offset, 128, false))
                            val rows = (page as? androidx.paging.PagingSource.LoadResult.Page)?.data ?: break
                            found = rows.any { it.streamUrl.isNotBlank() && visible(it) }
                            if (rows.size < 128) break
                            offset += rows.size
                        }
                        found
                    } finally { pages.invalidate() }
                    val favorites = db.channelDao().favoritesListAlpha(profileId).first().filter { it.sourceId in ids && visible(it) }.map { it.id }.toSet()
                    if (settings.activeProfileId.first() != profileId || activeSourceIds(settings, db.sourceDao(), profileId, MediaType.LIVE) != ids) HomeLibraryState()
                    else HomeLibraryState(profileId, homeLibraryStatus(ids.isNotEmpty(), false, hasChannels, favorites.isNotEmpty()), ids, favorites)
                }.onStart { emit(HomeLibraryState(aps.profileId, sourceIds = ids)) }
        }.flowOn(Dispatchers.IO)

    internal suspend fun seed(profileId: Long, sourceIds: List<Long>, candidate: ChannelEntity?, visible: suspend (ChannelEntity) -> Boolean) = withContext(Dispatchers.IO) {
        db.transaction {
            val profile = db.profileDao().getById(profileId) ?: return@transaction
            val key = "profile:$profileId:${profile.createdAt}"
            if (claims.getBoolean(key, false)) return@transaction
            if (settings.activeProfileId.first() != profileId || activeSourceIds(settings, db.sourceDao(), profileId, MediaType.LIVE) != sourceIds) return@transaction
            val existing = db.favoriteDao().getAllOnce().any { it.profileId == profileId && it.mediaType == MediaType.LIVE }
            val removed = db.tombstoneDao().getAllOnce().any {
                it.profileId == profileId && it.kind == "fav" && runCatching { org.json.JSONObject(it.identity).optString("t") == MediaType.LIVE.name }.getOrDefault(true)
            }
            val current = candidate?.let { db.channelDao().getById(it.id) }?.takeIf { it.sourceId in sourceIds && initialHockeyChannel(listOf(it)) != null && visible(it) }
            if (!existing && !removed && current == null) return@transaction
            // Claim BEFORE insertion: a crash/rollback may skip the convenience, but can never re-add
            // a favorite the user removed. Failed durable writes perform no favorite mutation.
            check(claims.edit().putBoolean(key, true).commit())
            if (!existing && !removed && current != null) db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = current.id))
        }
    }
}
