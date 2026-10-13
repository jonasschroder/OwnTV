package tv.own.owntv.features.home

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class HockeyEpgGuideTest {
    private val now = Instant.parse("2026-10-13T15:00:00Z").toEpochMilli()
    private val start = now + 2 * 60 * 60_000
    private val favorites = SportPreferences(listOf(SportsCatalog.farjestadId))
    private fun channel(id: Long, quality: String, source: Long = 101, name: String = "TV4 Sport Live 2") =
        tv.own.owntv.core.database.entity.ChannelEntity(id = id, sourceId = source, remoteId = "$id", name = "$name $quality", epgChannelId = "same-service", streamUrl = "https://example.invalid")
    @Test fun verifiedFixtureVariantsPreferHdSdFhdAndExplicitChoiceWins() {
        val rows = listOf(channel(1, "FHD"), channel(2, "SD"), channel(3, "HD"), channel(4, ""), channel(5, "UHD"))
        val defaults = tv.own.owntv.features.live.SwedishChoices(showUhd = true)
        assertEquals(rows[2], preferredHockeyChannel(rows, defaults))
        assertEquals(rows[1], preferredHockeyChannel(rows.filter { it.id != 3L }, defaults))
        assertEquals(rows[0], preferredHockeyChannel(listOf(rows[0], rows[3]), defaults))
        assertEquals(rows[0], preferredHockeyChannel(listOf(rows[0]), defaults))
        assertNull(preferredHockeyChannel(listOf(rows[4]), defaults))
        val identity = tv.own.owntv.features.live.SwedishChannels.recognize(rows[0].name)!!.identity
        for (row in rows) {
            val choice = defaults.copy(showUhd = false, preferred = mapOf(identity to tv.own.owntv.core.customize.CustomizeKeys.channel(row)))
            assertEquals(row, preferredHockeyChannel(rows, choice))
        }
        assertEquals(5, rows.size)
    }
    @Test fun variantPreferenceDoesNotGuessAcrossSourcesBroadcastersOrEpgServices() {
        val hd = channel(1, "HD")
        val fhd = channel(2, "FHD")
        val choices = tv.own.owntv.features.live.SwedishChoices()
        assertNull(preferredHockeyChannel(listOf(hd, fhd.copy(sourceId = 102)), choices))
        assertNull(preferredHockeyChannel(listOf(hd, channel(2, "FHD", name = "TV4 Sport Live 1")), choices))
        assertNull(preferredHockeyChannel(listOf(hd, fhd.copy(epgChannelId = "regional-service")), choices))
        assertNull(preferredHockeyChannel(listOf(hd, channel(2, "HD")), choices))
        assertNull(preferredHockeyChannel(listOf(channel(1, ""), channel(2, "")), choices))
        val identity = tv.own.owntv.features.live.SwedishChannels.recognize(hd.name)!!.identity
        assertEquals(fhd.copy(sourceId = 102), preferredHockeyChannel(listOf(hd, fhd.copy(sourceId = 102)),
            choices.copy(preferred = mapOf(identity to tv.own.owntv.core.customize.CustomizeKeys.channel(fhd.copy(sourceId = 102))))))
    }
    @Test fun automaticGuideHonorsHiddenGroupsAndDefaultUhdWithoutRemovingStoredFavorites() {
        val fhd = tv.own.owntv.core.database.entity.ChannelEntity(id = 1, sourceId = 101, name = "SE | TV4 Sport Live 2 FHD", remoteId = "fhd", streamUrl = "https://example.invalid")
        val uhd = fhd.copy(id = 2, name = "SE | TV4 Sport Live 2 UHD", remoteId = "uhd")
        val rows = listOf(fhd, uhd)
        val defaults = tv.own.owntv.features.live.SwedishChoices()
        assertEquals(listOf(fhd), hockeyGuideChannels(rows, defaults))
        assertEquals(rows, hockeyGuideChannels(rows, defaults.copy(showUhd = true)))
        val identity = tv.own.owntv.features.live.SwedishChannels.recognize(fhd.name)!!.identity
        assertTrue(hockeyGuideChannels(rows, defaults.copy(hidden = setOf(identity))).isEmpty())
        val preferred = defaults.copy(preferred = mapOf(identity to tv.own.owntv.core.customize.CustomizeKeys.channel(uhd)))
        assertEquals(rows, hockeyGuideChannels(rows, preferred))
        assertEquals(2, rows.size)
    }
    private fun programme(title: String, description: String? = null, at: Long = start) = EpgProgrammeEntity(
        id = 1, sourceId = 1, epgChannelId = "sports", startMs = at, stopMs = at + 3 * 60 * 60_000, title = title, description = description)
    @Test fun livePairLeagueAndValidatedAliasesWorkWithoutAnyExternalSchedule() {
        val parser = HockeyEpgParser(favorites, now)
        val a = parser.parse(programme("SHL LIVE: Färjestad BK – Luleå HF"))!!
        val b = parser.parse(programme("SHL", "Direktsändning: FBK vs LHF."))!!
        assertEquals(a.fixture.id, b.fixture.id)
        assertEquals(SportsCatalog.farjestadId, a.fixture.homeTeam.id)
        assertEquals("Luleå HF", a.fixture.away)
        assertTrue(a.timeFromEpg)
        assertNull(a.fixture.score)
        assertEquals(start, a.fixture.faceoff)
    }
    @Test fun genericReplaysHighlightsStudiosYouthAndAmbiguousMentionsNeverConfirm() {
        val parser = HockeyEpgParser(favorites, now)
        for (title in listOf("SHL LIVE", "SHL: FBK", "LIVE: Färjestad BK – Luleå HF", "SHL: Färjestad BK – Luleå HF",
            "SHL LIVE: Färjestad och Luleå bäst i ligan", "SHL LIVE: FBK – LHF repris", "SHL LIVE: FBK – LHF highlights",
            "SHL LIVE: FBK – LHF studio", "SHL LIVE: FBK – LHF inför", "SHL LIVE: FBK – LHF U20",
            "SHL LIVE: FBK – LHF och Frölunda HC", "SHL HockeyAllsvenskan LIVE: FBK – LHF")) {
            assertNull(title, parser.parse(programme(title)))
        }
    }
    @Test fun hockeyAllsvenskanSupportsKarlskogaAndDoesNotMistakeSkellefteaForAik() {
        val selection = SportPreferences(listOf(SportsCatalog.identity("BIK Karlskoga", Sport.ICE_HOCKEY)!!.id))
        val parser = HockeyEpgParser(selection, now)
        val found = parser.parse(programme("HockeyAllsvenskan LIVE: Karlskoga – AIK"))!!
        assertEquals(SportsCatalog.allsvenskan, found.fixture.competition)
        assertEquals("BIK Karlskoga", found.fixture.home)
        assertNull(parser.parse(programme("HockeyAllsvenskan LIVE: Karlskoga – Skellefteå AIK")))
        assertNull(HockeyEpgParser(favorites, now).parse(programme("HockeyAllsvenskan LIVE: Karlskoga – AIK")))
    }
    @Test fun confirmedKnownFixtureStillRequiresSpecificPairAndCompatibleTime() {
        val fixture = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("actual", "Färjestad BK", "Luleå HF", start + 15 * 60_000, null))
        val parser = HockeyEpgParser(favorites, now, listOf(fixture))
        val found = parser.parse(programme("SHL: Färjestad BK – Luleå HF"))!!
        assertEquals(fixture, found.fixture)
        assertFalse(found.timeFromEpg)
        assertNull(parser.parse(programme("SHL: Färjestad BK – Luleå HF", at = start + 2 * 60 * 60_000)))
        assertNull(HockeyEpgParser(favorites, now, listOf(fixture, fixture.copy(id = "other")))
            .parse(programme("SHL: Färjestad BK – Luleå HF")))
    }
    @Test fun concurrentGamesMultipleSourcesAndExpiredProgrammesStayDistinct() {
        val selection = SportPreferences(listOf(SportsCatalog.farjestadId, SportsCatalog.identity("Frölunda HC", Sport.ICE_HOCKEY)!!.id))
        val guide = HockeyEpgCollector(selection, now)
        guide.add(11, programme("SHL LIVE: FBK – LHF")); guide.add(12, programme("SHL LIVE: FBK – LHF"))
        guide.add(13, programme("SHL LIVE: Frölunda HC – Rögle BK"))
        guide.add(14, programme("SHL LIVE: FBK – LHF").copy(stopMs = now))
        val matches = guide.result(truncated = true)
        assertEquals(2, matches.size)
        assertEquals(listOf(11L, 12L), matches.single { it.fixture.homeTeam.id == SportsCatalog.farjestadId }.channelIds)
        assertTrue(matches.all { it.truncated })
        assertFalse(matches.any { 14L in it.channelIds })
    }
    @Test fun timesUseRealStockholmTimezoneAcrossSummerAndWinter() {
        for ((instant, hour) in listOf("2026-10-13T17:00:00Z" to 19, "2026-10-27T18:00:00Z" to 19)) {
            val epoch = Instant.parse(instant).toEpochMilli()
            val found = HockeyEpgParser(favorites, epoch - 60_000).parse(programme("SHL LIVE: FBK – LHF", at = epoch))!!
            assertEquals(hour, found.fixture.kickoff.atZone(Stockholm).hour)
        }
    }
    @Test fun conflictingOverlappingFeedsForOneChannelCannotConfirmTwoSimultaneousGames() {
        val selection = SportPreferences(listOf(SportsCatalog.farjestadId, SportsCatalog.identity("Frölunda HC", Sport.ICE_HOCKEY)!!.id))
        val guide = HockeyEpgCollector(selection, now)
        val a = programme("SHL LIVE: FBK – LHF")
        val b = programme("SHL LIVE: Frölunda HC – Rögle BK")
        guide.add(11, a); guide.add(12, a); guide.add(11, b); guide.add(11, a)
        val found = guide.result().single()
        assertEquals(listOf(12L), found.channelIds)
        assertEquals(SportsCatalog.farjestadId, found.fixture.homeTeam.id)
        guide.add(11, a.copy(startMs = start + 4 * 60 * 60_000, stopMs = start + 7 * 60 * 60_000))
        assertEquals(2, guide.result().size)
    }
}
