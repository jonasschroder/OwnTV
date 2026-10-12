package tv.own.owntv.features.live

import org.junit.Assert.*
import org.junit.Test

class SwedishChannelsTest {
    private fun variant(id: Long, name: String, source: Long = 1, remote: String = "$id") =
        SwedishVariant(id, source, "$source:$remote", name, SwedishChannels.recognize(name)!!, id.toInt())
    @Test fun namingDecorationsDoNotChangeIdentity() {
        assertEquals(setOf("svt1"), listOf("SE | SVT1 HD", "SVT 1 FHD", "SVT1 Sverige").map { SwedishChannels.recognize(it)!!.identity }.toSet())
        assertEquals(setOf("tv4"), listOf("TV4 HD SE", "SE: TV4 FHD").map { SwedishChannels.recognize(it)!!.identity }.toSet())
    }
    @Test fun broadcasterNumbersAndSportsChannelsRemainDistinct() {
        val names = listOf("SVT1", "SVT2", "TV4", "TV4 Hockey FHD SE", "TV4 Sport Live 1", "TV4 Sport Live 2")
        assertEquals(names.size, names.map { SwedishChannels.recognize(it)!!.identity }.distinct().size)
        assertNull(SwedishChannels.recognize("SE | TV4 Play Hockey"))
    }
    @Test fun defaultOrderIsSwedishTelevisionRatherThanAlphabetical() {
        val names = listOf("TV12", "SVT2", "TV4", "SVT1", "Sjuan", "Kanal 5")
        assertEquals(listOf("SVT1", "SVT2", "TV4", "Kanal 5", "Sjuan", "TV12"), SwedishChannels.group(names.mapIndexed { i, n -> variant(i.toLong(), n) }).map { it.label })
    }
    @Test fun explicitUhdLabelsAreFilteredButVagueNamesAreNot() {
        for (label in listOf("4K", "UHD", "Ultra HD", "2160p", "3840x2160")) {
            val group = SwedishChannels.group(listOf(variant(1, "SE: TV4 $label"))).single()
            assertNull(group.preferred(null, false)); assertNotNull(group.preferred(null, true))
        }
        assertEquals(ChannelQuality.UNKNOWN, SwedishChannels.recognize("SE: TV4 Ultra")!!.quality)
    }
    @Test fun groupingKeepsAllOriginalSourcesAndExplicitChoiceWins() {
        val rows = listOf(variant(1, "TV4 SD"), variant(2, "TV4 HD"), variant(3, "TV4 FHD"), variant(4, "TV4 UHD", 2))
        val group = SwedishChannels.group(rows).single()
        assertEquals(4, group.variants.size)
        assertEquals(3L, group.preferred(null, false)!!.id)
        assertEquals(4L, group.preferred("2:4", false)!!.id)
        assertEquals(2, group.variants.map { it.sourceId }.distinct().size)
    }
    @Test fun regionsTimeshiftAndAudioAreNotMerged() {
        val rows = listOf("SE: SVT1 Stockholm HD", "SE: SVT1 Skåne HD", "SE: SVT1 +1 HD", "SE: SVT1 English HD", "SVT1 HD")
        assertEquals(5, SwedishChannels.group(rows.mapIndexed { i, n -> variant(i.toLong(), n) }).size)
        assertNull(SwedishChannels.recognize("NO | TV4 HD"))
        assertNull(SwedishChannels.recognize("Uncertain unnamed channel"))
    }
    @Test fun stableProviderChoiceSurvivesResyncAndMissingChoiceFallsBack() {
        val group = SwedishChannels.group(listOf(variant(900, "TV4 HD", remote = "same"), variant(901, "TV4 FHD"))).single()
        assertEquals(900L, group.preferred("1:same", false)!!.id)
        assertEquals(901L, group.preferred("removed:source", false)!!.id)
    }
    @Test fun manualOrderAndHideOnlyChangeDerivedView() {
        val rows = listOf(variant(1, "SVT1"), variant(2, "SVT2"), variant(3, "TV4"))
        assertEquals(listOf("TV4", "SVT1"), SwedishChannels.group(rows, listOf("tv4"), setOf("svt2")).map { it.label })
        assertEquals(3, rows.size)
        assertEquals(3, SwedishChannels.group(rows).size)
    }
}
