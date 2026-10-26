package tv.own.owntv.features.live

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ProfileEntity

internal data class NativeHomeFavorites(val profileId: Long = -1, val channels: List<ChannelEntity> = emptyList())

/** Same favourite policy as Core HomeFeed; no movie, metadata, continuation or EPG reads. */
internal fun nativeHomeFavorites(rows: List<ChannelEntity>, sources: Set<Long>, profile: ProfileEntity?,
    custom: SectionCustomizations, categories: List<CategoryEntity>): List<ChannelEntity> {
    if (profile == null) return emptyList()
    val hidden = AdultCategoryClassifier.hiddenCategoryIds(categories, custom.hiddenCategories, profile.isKids)
    return rows.filter { it.sourceId in sources && it.categoryId !in hidden && CustomizeKeys.channel(it) !in custom.hiddenItems }
}

internal data class HomeChannelAccess(val profile: ProfileEntity, val sources: Set<Long>, val custom: SectionCustomizations)

/** Bounded Home read. Deduplicate rows/categories within this call; recheck ownership before publication.
 * The player still performs its own final access check. Never retain this snapshot across calls.
 */
internal suspend fun readHomeChannels(ids: List<Long>, access: suspend () -> HomeChannelAccess?,
    channel: suspend (Long) -> ChannelEntity?, category: suspend (Long) -> CategoryEntity?,
    customContexts: suspend (Long) -> List<String>): Map<Long, ChannelEntity> {
    val requested = ids.distinct()
    require(requested.size <= 128)
    if (requested.isEmpty()) return emptyMap()
    val before = access() ?: return emptyMap()
    val categories = mutableMapOf<Long, CategoryEntity?>()
    val result = linkedMapOf<Long, ChannelEntity>()
    for (id in requested) {
        currentCoroutineContext().ensureActive()
        val row = channel(id)?.takeIf { it.sourceId in before.sources } ?: continue
        if (CustomizeKeys.channel(row) in before.custom.hiddenItems) continue
        val folder = row.categoryId?.let { categoryId ->
            if (!categories.containsKey(categoryId)) categories[categoryId] = category(categoryId)
            categories[categoryId]
        }
        if (before.profile.isKids && AdultCategoryClassifier.isAdult(folder?.name)) continue
        if (folder != null && CustomizeKeys.category(folder) in before.custom.hiddenCategories &&
            customContexts(id).none { it !in before.custom.hiddenCategories }) continue
        result[id] = row
    }
    return if (access() == before) result else emptyMap()
}
