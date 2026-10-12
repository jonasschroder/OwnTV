package tv.own.owntv.features.discovery

import tv.own.owntv.core.metadata.MetadataType

/** Actual provider category identity. Names never assert a streaming subscription or ranking. */
internal data class ProviderFolder(val id: Long, val sourceId: Long, val stableKey: String,
    val name: String, val sourceName: String, val priority: Int)

internal object ProviderFolderOrder {
    fun priority(name: String, type: MetadataType): Int {
        val key = name.trim().replace(Regex("^(?:MOVIE|MOVIES|SERIES)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
        val preferred = if (type == MetadataType.MOVIE)
            listOf("imdb top 250", "oscar movies", "disney", "swedish", "swedish (kids)", "christmas", "english", "multi subtitles")
        else listOf("netflix", "disney", "amazon prime")
        return preferred.indexOf(key).takeIf { it >= 0 } ?: 100
    }
}

/** Bounded previews only. All original rows and omitted variants stay in the complete category. */
internal class ProviderFolderCollector(private val folders: Map<Long, ProviderFolder>) {
    private val groups = linkedMapOf<Long, MutableList<DiscoveryCard>>()
    private val order = compareBy<DiscoveryCard> { it.providerOrder }.thenBy { it.name }.thenBy { it.id }
    private fun identity(card: DiscoveryCard): String = if (card.tmdbId != null && card.year != null)
        "${card.type}:${card.tmdbId}:${card.year}" else card.stableKey

    fun add(input: DiscoveryCard) {
        val folder = folders[input.categoryId] ?: return
        if (folder.sourceId != input.sourceId) return
        val card = input.copy(folderKey = folder.stableKey)
        if (folder.id !in groups && groups.size >= 12) {
            val worst = groups.keys.maxWithOrNull(compareBy<Long> { folders.getValue(it).priority }.thenBy { it }) ?: return
            val previous = folders.getValue(worst)
            if (compareValuesBy(folder, previous, { it.priority }, { it.id }) >= 0) return
            groups.remove(worst)
        }
        val rows = groups.getOrPut(folder.id) { mutableListOf() }
        val index = rows.indexOfFirst { identity(it) == identity(card) }
        if (index >= 0) {
            val previous = rows[index]
            val variants = (listOf(previous.copy(variants = emptyList())) + previous.variants + card)
                .distinctBy { it.stableKey }.sortedWith(order).take(4)
            rows[index] = variants.first().copy(variants = variants.drop(1))
        } else rows += card
        rows.sortWith(order)
        if (rows.size > 12) rows.removeAt(rows.lastIndex)
    }
    fun rows(): List<DiscoveryRow> = groups.entries.sortedWith(compareBy({ folders.getValue(it.key).priority }, { it.key }))
        .map { (id, cards) -> DiscoveryRow(DiscoveryKind.PROVIDER, cards.toList(), folder = folders.getValue(id)) }
}
