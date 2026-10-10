package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ShlOfficialPagesTest {
    private val now = Instant.parse("2026-10-10T15:06:00Z").toEpochMilli()
    private fun page(name: String) = javaClass.getResource("/swehockey-2026-10-10/$name.html")!!.readText()

    @Test fun actualH1PagesWereRejectedByOldRepositoryH2Guard() {
        val html = page("shl-schedule")
        assertFalse(Regex("<h2>\\s*SHL\\s*</h2>").containsMatchIn(html))
        SwehockeyParser.competitionPage(html, SportsCatalog.shl, "20961", now, false)
        assertEquals("20961", SwehockeyParser.discover(page("index")))
    }
    @Test fun frolundaTodayAndNextResolveFromOfficialOctoberSchedule() {
        val games = SwehockeyParser.schedule(page("shl-schedule"))
        assertEquals(364, games.size)
        val team = SportsCatalog.identity("Frölunda HC", Sport.ICE_HOCKEY)!!
        val mine = personalFixtures(games.map { SportsCatalog.fixture(SportsCatalog.shl, it) }, SportPreferences(listOf(team.id)), now)
        val today = mine.single { it.on(LocalDate.parse("2026-10-10")) }
        assertEquals("IF Björklöven", today.home)
        assertEquals("Frölunda HC", today.away)
        assertEquals(Instant.parse("2026-10-10T16:00:00Z"), today.kickoff)
        assertEquals(today, mine.first { it.faceoff >= now })
        assertTrue(mine.any { it.on(LocalDate.parse("2026-10-15")) })
        assertTrue(games.any { it.fbk && it.on(LocalDate.parse("2026-10-10")) })
    }
    @Test fun actualBothTablesAndAllsvenskanScheduleRemainSeparate() {
        for ((competition, prefix, season) in listOf(Triple(SportsCatalog.shl, "shl", "20961"), Triple(SportsCatalog.allsvenskan, "ha", "20962"))) {
            SwehockeyParser.competitionPage(page("$prefix-table"), competition, season, now, true)
            val table = SwehockeyParser.standings(page("$prefix-table"))
            assertEquals(14, table.size)
            assertEquals(SportsCatalog.inCompetition(competition).map { it.id }.toSet(), table.map { SportsCatalog.identity(it.team, Sport.ICE_HOCKEY)!!.id }.toSet())
        }
        SwehockeyParser.competitionPage(page("ha-schedule"), SportsCatalog.allsvenskan, "20962", now, false)
        val bik = SportsCatalog.identity("BIK Karlskoga", Sport.ICE_HOCKEY)!!
        val fixtures = SwehockeyParser.schedule(page("ha-schedule")).map { SportsCatalog.fixture(SportsCatalog.allsvenskan, it) }
        assertEquals(52, fixtures.count { it.homeTeam == bik || it.awayTeam == bik })
        assertTrue(personalFixtures(fixtures, SportPreferences(listOf(bik.id)), now).isNotEmpty())
        assertEquals("20962", SwehockeyParser.discover(page("index"), "HockeyAllsvenskan"))
    }
    @Test fun wrongLeagueSeasonAndRouteFailClosed() {
        val html = page("shl-schedule")
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.competitionPage(html, SportsCatalog.allsvenskan, "20961", now, false) }
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.competitionPage(html.replace("2026-27", "2025-26"), SportsCatalog.shl, "20961", now, false) }
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.competitionPage(html, SportsCatalog.shl, "20962", now, false) }
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.competitionPage(html, SportsCatalog.shl, "20961", now, true) }
    }
}
