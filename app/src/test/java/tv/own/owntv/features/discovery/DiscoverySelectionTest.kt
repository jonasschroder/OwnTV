package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.metadata.TrendingCandidate

class DiscoverySelectionTest {
    private fun candidate(id: Int, name: String, year: Int = 2025, rank: Int = id, type: MetadataType = MetadataType.MOVIE) =
        TrendingCandidate(id, type, name, null, year, null, null, null, 8.0, 100.0, rank)
    private fun card(id: Long, name: String, year: Int? = 2025, tmdb: Int? = null, type: MetadataType = MetadataType.MOVIE,
        added: Long? = null, completed: Boolean = false) = DiscoveryCard(id, 1, "1:$id", type, name, null, year, added, 8.0, setOf("Drama"), tmdb, completed)
    @Test fun rankingContainsOnlyRealAvailableEntriesAndKeepsExternalRank() {
        val candidates = listOf(candidate(10, "A", rank = 1), candidate(20, "B", rank = 2), candidate(30, "C", rank = 3))
        assertEquals(listOf(2L, 1L), DiscoverySelection.ranking(listOf(card(1,"C",tmdb=30),card(2,"A",tmdb=10)),candidates).map { it.id })
    }
    @Test fun ambiguousRemakesMissingYearWrongTypeAndSimilarNamesAbstain() {
        val feed = listOf(candidate(1,"The Show"),candidate(2,"The Show"),candidate(3,"The Show",type=MetadataType.TV))
        assertNull(DiscoverySelection.candidate(card(1,"The Show"),feed))
        assertNull(DiscoverySelection.candidate(card(1,"The Show",year=null),feed))
        assertNull(DiscoverySelection.candidate(card(1,"The Shows"),listOf(feed.first())))
        assertNull(DiscoverySelection.candidate(card(1,"The Show",year=1999),listOf(feed.first())))
        assertNull(DiscoverySelection.candidate(card(1,"The Show",tmdb=3),feed))
    }
    @Test fun verifiedIdDoesNotRewriteMeaningfulProviderNameAndVariantsDeduplicate() {
        val feed=listOf(candidate(1,"1917",year=2019))
        assertEquals(1,DiscoverySelection.ranking(listOf(card(1,"1917",year=2019,tmdb=1),card(2,"1917",year=2019,tmdb=1)),feed).size)
        assertNull(DiscoverySelection.candidate(card(1,"1917",year=2019),feed))
    }
    @Test fun largeLocalLibraryRetainsOnlyBoundedRealRowsWithoutInventingTrendsOrAddedDates() {
        val rows = DiscoveryAccumulator(listOf(candidate(10,"Available")),setOf("Drama"))
        repeat(7000) { rows.add(card(it.toLong(),"Local $it",added=it.toLong())) }
        rows.add(card(9000,"Available",tmdb=10))
        val result=rows.rows()
        assertEquals(1,result.single { it.kind==DiscoveryKind.TOP }.cards.size)
        assertTrue(result.all { it.cards.size<=12 })
        assertFalse(result.single { it.kind==DiscoveryKind.RECENT }.cards.any { it.addedAt==null })
    }
    @Test fun localRecommendationsAreProfileSeededAndCompletedContentIsExcluded() {
        assertEquals(1,DiscoverySelection.recommend(card(1,"Title"),setOf("Drama")))
        assertEquals(0,DiscoverySelection.recommend(card(1,"Title"),setOf("Comedy")))
        assertEquals(0,DiscoverySelection.recommend(card(1,"Title",completed=true),setOf("Drama")))
    }
    @Test fun streamedRankingsChooseTheSameVersionAsBatchRankingsRegardlessOfScanOrder() {
        val feed = listOf(candidate(10, "Available"))
        val olderId = card(1, "Available", tmdb = 10).copy(sourceId = 9, stableKey = "9:1")
        val preferred = card(2, "Available", tmdb = 10)
        for (input in listOf(listOf(olderId, preferred), listOf(preferred, olderId))) {
            val collector = DiscoveryAccumulator(feed, emptySet())
            input.forEach(collector::add)
            assertEquals(DiscoverySelection.ranking(input, feed), collector.rows().single { it.kind == DiscoveryKind.TOP }.cards)
            assertEquals(listOf(2L), collector.rows().single { it.kind == DiscoveryKind.TOP }.cards.map { it.id })
        }
    }
}
