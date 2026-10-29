package tv.own.owntv.features.live

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.features.home.hockeyChannelNumber

class OwnerNavigationPolicyTest {
    private fun group(id: Long, raw: String, source: Long = 1): SwedishGroup {
        val name = SwedishChannels.recognize(raw)!!
        return SwedishGroup(name.identity, name.label, listOf(SwedishVariant(id, source, "$source:$id", raw, name, null)))
    }
    @Test fun numericSportsOrderAndStrictAliases() {
        val names = listOf("Eurosport 2", "TV4 Sport Live 10", "TV4 Sport Live 2", "TV Hockey", "TV4 Sportkanalen", "TV4 Sport Live 1")
        val rows = names.mapIndexed { i, n -> group(i.toLong(), n).variants.single() }
        assertEquals(listOf("TV4 Hockey", "TV4 Sport Live 1", "TV4 Sport Live 2", "TV4 Sport Live 10", "TV4 Sportkanalen", "Eurosport 2"), SwedishChannels.group(rows).map { it.label })
        for (i in 1..32) {
            assertEquals(i, hockeyChannelNumber("SE: TV4 Sport Live $i HD"))
            assertEquals(i, hockeyChannelNumber("TV Hockey Live $i"))
        }
        for (raw in listOf("TV4 Sport Live 0", "TV4 Sport Live 10 Highlights", "TV4 Sport Live 2 Replay", "NO: TV4 Sport Live 2", "TV4 Play Hockey", "TV4 Sport Live 01")) assertNull(hockeyChannelNumber(raw))
    }
    @Test fun nationalAndRegionalServicesAreIndependentAndOnlyExplicitHidingRemovesOne() {
        val national = group(1, "SVT1 HD")
        val region = group(2, "SVT1 Värmland HD")
        val rows = national.variants + region.variants + group(3, "SVT1 Värmland FHD", 2).variants
        val groups = SwedishChannels.group(rows)
        assertEquals(listOf("svt1", "svt1varmland"), groups.map { it.identity })
        assertEquals(2, groups.last().variants.size)
        assertEquals(listOf("svt1varmland"), SwedishChannels.group(rows, hidden = setOf("svt1")).map { it.identity })
        assertEquals(listOf("svt1"), SwedishChannels.group(rows, hidden = setOf("svt1varmland")).map { it.identity })
        assertEquals("SVT1", national.label)
        assertNull(SwedishChannels.recognize("SVT1 Värmland Highlights"))
        assertEquals(listOf(national), SwedishChannels.group(national.variants))
    }
    @Test fun cachedGuideRanksValidNowNextThenStaleButFreezesFocusPositions() {
        val a = group(1, "SE: A"); val b = group(2, "SE: B"); val c = group(3, "SE: C")
        val now = 1000L
        val info = mapOf(b.identity to SwedishRowInfo(null, null, SwedishProgramme(1,"Next",2000,3000)),
            c.identity to SwedishRowInfo(null, SwedishProgramme(2,"Now",900,1500), null),
            a.identity to SwedishRowInfo(null, SwedishProgramme(3,"Yesterday",100,200), null))
        assertEquals(listOf(c.identity,b.identity,a.identity), stableGuideOrder(listOf(a,b,c), emptyList(), false, info, now))
        assertEquals(listOf(a.identity,b.identity,c.identity), stableGuideOrder(listOf(a,b,c), listOf(a.identity,b.identity,c.identity), true, info, now))
        assertEquals(2, guideBucket(info[c.identity], 4000))
        assertEquals(listOf(a.identity), stableGuideOrder(listOf(a), emptyList(), false, emptyMap(), now))
    }
}
