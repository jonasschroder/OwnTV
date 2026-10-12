package tv.own.owntv.features.discovery

import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.metadata.TrendingCandidate
import tv.own.owntv.core.metadata.TitleNormalizer
import tv.own.owntv.features.metadata.ConservativeTitles

internal data class DiscoveryCard(val id: Long, val sourceId: Long, val stableKey: String, val type: MetadataType,
    val name: String, val poster: String?, val year: Int?, val addedAt: Long?, val rating: Double?,
    val genres: Set<String>, val tmdbId: Int?, val completed: Boolean = false,
    val categoryId: Long? = null, val providerOrder: Int = 0, val variants: List<DiscoveryCard> = emptyList(),
    val folderKey: String? = null)
internal enum class DiscoveryKind { PROVIDER, TOP, TRENDING, RECENT, RATED, RECOMMENDED, GENRE }
internal data class DiscoveryRow(val kind: DiscoveryKind, val cards: List<DiscoveryCard>, val genre: String? = null,
    val folder: ProviderFolder? = null) {
    val key: String get() = folder?.let { "provider:${it.stableKey}" } ?: "$kind:$genre"
}
internal data class DiscoveryShelf(val rows: List<DiscoveryRow> = emptyList(), val rankedAt: Long? = null,
    val failed: Boolean = false, val bounded: Boolean = false)

/** Pure local resolver. A time/rating/popularity/name resemblance alone is never identity. */
internal object DiscoverySelection {
    fun candidate(card: DiscoveryCard, candidates: List<TrendingCandidate>): TrendingCandidate? {
        if (candidates.isEmpty()) return null
        if (card.tmdbId != null) return candidates.singleOrNull { it.type == card.type && it.tmdbId == card.tmdbId }
        if (!ConservativeTitles.safeForAutoMatch(card.name)) return null
        val name = TitleNormalizer.normalize(card.name)
        val year = card.year ?: name.year ?: return null // Remakes/undated titles require a verified ID.
        return candidates.filter { it.type == card.type && it.year == year &&
            listOfNotNull(it.localizedTitle, it.originalTitle).any { title -> name.query.equals(title, ignoreCase = true) }
        }.distinctBy { it.tmdbId }.singleOrNull()
    }
    fun ranking(cards: List<DiscoveryCard>, candidates: List<TrendingCandidate>): List<DiscoveryCard> =
        cards.mapNotNull { card -> candidate(card, candidates)?.let { it to card } }
            .sortedWith(compareBy<Pair<TrendingCandidate, DiscoveryCard>> { it.first.trendingRank }.thenBy { it.second.sourceId }.thenBy { it.second.id })
            .distinctBy { it.first.tmdbId }.take(20).map { it.second }
    fun recommend(card: DiscoveryCard, genres: Set<String>): Int =
        if (card.completed) 0 else card.genres.intersect(genres).size
}

/** Fixed-size collector: never retain an entire catalog in Compose merely to sort it. */
internal class DiscoveryAccumulator(private val candidates: List<TrendingCandidate>, private val seedGenres: Set<String>) {
    private val ranked = mutableListOf<DiscoveryCard>()
    private val recent = mutableListOf<DiscoveryCard>()
    private val rated = mutableListOf<DiscoveryCard>()
    private val recommended = mutableListOf<DiscoveryCard>()
    private val genres = linkedMapOf<String, MutableList<DiscoveryCard>>()
    private fun keep(rows: MutableList<DiscoveryCard>, card: DiscoveryCard, compare: Comparator<DiscoveryCard>) {
        rows += card; rows.sortWith(compare); if (rows.size > 12) rows.removeAt(rows.lastIndex)
    }
    fun add(card: DiscoveryCard) {
        if (DiscoverySelection.candidate(card, candidates) != null) {
            val current = ranked.indexOfFirst { DiscoverySelection.candidate(it, candidates)?.tmdbId == DiscoverySelection.candidate(card, candidates)?.tmdbId }
            if (current < 0 && ranked.size < 20) ranked += card
        }
        if (card.addedAt != null && card.addedAt > 0) keep(recent, card, compareByDescending { it.addedAt })
        if (card.rating != null && card.rating in 7.0..10.0) keep(rated, card, compareByDescending { it.rating })
        if (DiscoverySelection.recommend(card, seedGenres) > 0) keep(recommended, card,
            compareByDescending<DiscoveryCard> { DiscoverySelection.recommend(it, seedGenres) }.thenByDescending { it.rating ?: 0.0 })
        for (genre in card.genres.sorted().take(6)) {
            if (genre !in genres && genres.size >= 6) continue
            keep(genres.getOrPut(genre) { mutableListOf() }, card, compareByDescending { it.addedAt ?: 0L })
        }
    }
    fun rows(): List<DiscoveryRow> = buildList {
        val ranking = DiscoverySelection.ranking(ranked, candidates)
        if (ranking.isNotEmpty()) add(DiscoveryRow(DiscoveryKind.TOP, ranking.take(10)))
        if (ranking.size > 10) add(DiscoveryRow(DiscoveryKind.TRENDING, ranking.drop(10).take(12)))
        if (recent.isNotEmpty()) add(DiscoveryRow(DiscoveryKind.RECENT, recent.toList()))
        if (rated.isNotEmpty()) add(DiscoveryRow(DiscoveryKind.RATED, rated.toList()))
        if (recommended.isNotEmpty()) add(DiscoveryRow(DiscoveryKind.RECOMMENDED, recommended.toList()))
        genres.forEach { (genre, cards) -> if (cards.isNotEmpty()) add(DiscoveryRow(DiscoveryKind.GENRE, cards.toList(), genre)) }
    }
}
