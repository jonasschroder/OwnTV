package tv.own.owntv.features.metadata

import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.MetadataSearchResult
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.metadata.TitleMatchScorer

/** One existing authorized request, existing budgets/cache; abstain on ambiguous search results.
 * Known-ID details, episodes, trending and transport null semantics are delegated unchanged. */
class ConservativeMetadataProvider(private val upstream: MetadataProvider) : MetadataProvider by upstream {
    override suspend fun searchMovie(title: String, year: Int?, includeAdult: Boolean): List<MetadataSearchResult>? =
        choose(title, year, MetadataType.MOVIE, upstream.searchMovie(title, year, includeAdult))
    override suspend fun searchTv(title: String, year: Int?, includeAdult: Boolean): List<MetadataSearchResult>? =
        choose(title, year, MetadataType.TV, upstream.searchTv(title, year, includeAdult))

    companion object {
        internal fun choose(title: String, year: Int?, type: MetadataType,
            hits: List<MetadataSearchResult>?): List<MetadataSearchResult>? {
            if (hits == null) return null // Transport failure must never become a negative cache entry.
            val candidates = hits.asSequence().take(40).filter { it.type == type && it.tmdbId > 0 }
                .filter { year == null || it.year == year }
                .distinctBy { it.tmdbId }
                .map { it to TitleMatchScorer.score(title, year, it.title, it.originalTitle, it.year) }
                .filter { it.second >= 0.92 }
                .sortedByDescending { it.second }.toList()
            val best = candidates.firstOrNull() ?: return emptyList()
            if (candidates.getOrNull(1)?.second?.let { best.second - it < 0.12 } == true) return emptyList()
            return listOf(best.first)
        }
    }
}
