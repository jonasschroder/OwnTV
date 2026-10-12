package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.MetadataType

class ProviderFoldersTest {
    private fun card(id: Long, folder: Long, metadata: Int? = null, year: Int? = 2025, order: Int = id.toInt()) =
        DiscoveryCard(id, 1, "1:$id", MetadataType.MOVIE, "Real item $id", null, year, null, null,
            emptySet(), metadata, categoryId = folder, providerOrder = order)
    private fun folder(id: Long, priority: Int = id.toInt()) = ProviderFolder(id, 1, "folder:$id", "MOVIE: Folder $id", "Source", priority)

    @Test fun onlyExplicitFoldersReceiveSuggestedPriorityAndServiceNamesAreNotRankings() {
        assertEquals(0, ProviderFolderOrder.priority(" MOVIE: IMDB Top 250 ", MetadataType.MOVIE))
        assertEquals(0, ProviderFolderOrder.priority("SERIES: Netflix", MetadataType.TV))
        assertEquals(100, ProviderFolderOrder.priority("Netflix Top 10 Official", MetadataType.TV))
        assertEquals(100, ProviderFolderOrder.priority("Disney Documentary", MetadataType.MOVIE))
    }
    @Test fun sameVerifiedTitleKeepsMembershipInBothFoldersAndOffersDistinctVersions() {
        val collector = ProviderFolderCollector(mapOf(1L to folder(1), 2L to folder(2)))
        collector.add(card(1, 1, 77)); collector.add(card(2, 1, 77)); collector.add(card(3, 2, 77))
        val rows = collector.rows()
        assertEquals(listOf(1L, 2L), rows.map { it.folder!!.id })
        assertEquals(listOf(1L, 2L), listOf(rows[0].cards.single().id) + rows[0].cards.single().variants.map { it.id })
        assertEquals(3L, rows[1].cards.single().id)
        assertEquals(2, rows.map { it.key }.distinct().size)
    }
    @Test fun unknownIdentityDifferentYearsAndForeignSourcesNeverCollapseOrEnterFolder() {
        val collector = ProviderFolderCollector(mapOf(1L to folder(1)))
        collector.add(card(1, 1)); collector.add(card(2, 1))
        collector.add(card(3, 1, 88, 1999)); collector.add(card(4, 1, 88, 2025))
        collector.add(card(5, 1, 88, null)); collector.add(card(6, 1).copy(sourceId = 2))
        collector.add(card(7, 99))
        collector.add(card(8, 1, 0)); collector.add(card(9, 1, 0))
        collector.add(card(10, 1, 88, 0)); collector.add(card(11, 1, 88, 0))
        collector.add(card(12, 1, -1)); collector.add(card(13, 1, -1))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 8L, 9L, 10L, 11L, 12L, 13L), collector.rows().single().cards.map { it.id })
    }
    @Test fun previewsStayBoundedAndLatePreferredFoldersReplaceEarlierUnpreferredOnes() {
        val folders = (1L..100L).associateWith { folder(it, if (it == 100L) 0 else it.toInt()) }
        val collector = ProviderFolderCollector(folders)
        for (folder in 1L..100L) for (item in 1L..100L) collector.add(card(folder * 100 + item, folder))
        assertEquals(12, collector.rows().size)
        assertEquals(100L, collector.rows().first().folder!!.id)
        assertTrue(collector.rows().all { it.cards.size == 12 })
    }
}
