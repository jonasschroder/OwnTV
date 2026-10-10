package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class SportsModelsTest {
    private val fbk = SportsCatalog.farjestadId
    private val bik = SportsCatalog.identity("BIK Karlskoga", Sport.ICE_HOCKEY)!!.id
    private val degerfors = SportsCatalog.identity("Degerfors IF", Sport.FOOTBALL)!!.id
    private val now = Instant.parse("2026-10-10T08:00:00Z").toEpochMilli()
    private fun fixture(id: String, home: String, away: String, instant: String, competition: Competition = SportsCatalog.shl) =
        SportsCatalog.fixture(competition, ShlGame(id, home, away, Instant.parse(instant).toEpochMilli(), null))

    @Test fun clubIdentitySurvivesPromotionAndSeparatesSports() {
        val original = fixture("one", "Färjestad BK", "AIK", "2026-10-10T13:15:00Z")
        val promoted = fixture("two", "FBK", "AIK", "2027-10-10T13:15:00Z", SportsCatalog.allsvenskan)
        assertEquals(original.homeTeam.id, promoted.homeTeam.id)
        assertEquals(fbk, original.homeTeam.id)
        assertNotEquals(SportsCatalog.identity("AIK", Sport.ICE_HOCKEY)?.id, SportsCatalog.identity("AIK", Sport.FOOTBALL)?.id)
        assertNull(SportsCatalog.identity("Färjestaden BK", Sport.ICE_HOCKEY))
        assertEquals(44, SportsCatalog.teams.size)
        assertEquals(44, SportsCatalog.teams.map { it.id }.distinct().size)
    }
    @Test fun primaryReorderingRemovalAndIndependentProfiles() {
        val a = SportPreferences(listOf(fbk, bik, degerfors), hideScores = true)
        val b = SportPreferences(listOf(degerfors))
        assertEquals(listOf(bik, fbk, degerfors), a.primary(bik).teamIds)
        assertEquals(listOf(fbk, degerfors, bik), a.move(bik, 1).teamIds)
        assertEquals(a, a.move(fbk, -1))
        assertEquals(listOf(fbk, degerfors), a.toggle(bik).teamIds)
        assertEquals(listOf(degerfors), b.teamIds)
        assertTrue(a.hideScores)
        assertTrue(SportPreferences().teamIds.isEmpty())
    }
    @Test fun requiredCompetitionsAreLimitedToSelectedTeams() {
        assertEquals(listOf(SportsCatalog.shl), SportsCatalog.required(SportPreferences(listOf(fbk))))
        assertEquals(listOf(SportsCatalog.allsvenskan), SportsCatalog.required(SportPreferences(listOf(bik))))
        assertEquals(listOf(SportsCatalog.football), SportsCatalog.required(SportPreferences(listOf(degerfors))))
        assertTrue(SportsCatalog.required(SportPreferences()).isEmpty())
    }
    @Test fun disabledHiddenBackgroundAndFootballHaveZeroRequestCompetitions() {
        val teams = SportPreferences(listOf(fbk, bik, degerfors))
        assertTrue(sportsRequestCompetitions(teams, true, true, false, false).isEmpty())
        assertTrue(sportsRequestCompetitions(teams, false, true, true, true).isEmpty())
        assertTrue(sportsRequestCompetitions(teams, true, false, false, true).isEmpty())
        assertTrue(sportsRequestCompetitions(teams.copy(prominent = false), true, true, false, true).isEmpty())
        assertTrue(sportsRequestCompetitions(SportPreferences(), true, true, true, true).isEmpty())
        assertTrue(sportsRequestCompetitions(SportPreferences(listOf(degerfors)), true, true, true, true).isEmpty())
        assertEquals(listOf(SportsCatalog.shl, SportsCatalog.allsvenskan), sportsRequestCompetitions(teams, true, true, false, true))
        assertEquals(2, sportsRequestCompetitions(teams.copy(prominent = false), true, false, true, true).size)
    }
    @Test fun personalFixturesKeepOverlappingMatchesAndRemoveUnfollowedOrPastDays() {
        val games = listOf(
            fixture("b", "BIK Karlskoga", "MoDo Hockey", "2026-10-10T13:15:00Z", SportsCatalog.allsvenskan),
            fixture("a", "Färjestad BK", "Malmö Redhawks", "2026-10-10T13:15:00Z"),
            fixture("past", "FBK", "Malmö", "2026-10-09T13:15:00Z"),
            fixture("other", "Brynäs IF", "Luleå HF", "2026-10-10T16:00:00Z"),
            fixture("next", "FBK", "Brynäs IF", "2026-10-11T13:15:00Z"))
        val personal = personalFixtures(games + games.first(), SportPreferences(listOf(fbk, bik)), now)
        assertEquals(3, personal.size)
        assertEquals(2, personal.count { it.on(LocalDate.parse("2026-10-10")) })
        assertEquals(personal.map { it.faceoff }.sorted(), personal.map { it.faceoff })
        assertTrue(personalFixtures(games, SportPreferences(listOf(degerfors)), now).isEmpty())
    }
    @Test fun stockholmDateAndKickoffRespectDaylightSaving() {
        val winter = fixture("winter", "FBK", "Malmö", "2026-10-25T00:30:00Z")
        assertEquals(2, winter.kickoff.atZone(Stockholm).hour)
        assertTrue(winter.on(LocalDate.parse("2026-10-25")))
        val late = fixture("late", "FBK", "Malmö", "2026-10-09T22:15:00Z")
        assertTrue(late.on(LocalDate.parse("2026-10-10")))
    }
    @Test fun unknownScoresNeverBecomeLiveOrConfirmedResults() {
        val game = ShlGame("a", "FBK", "Malmö", now, "unknown")
        assertNull(SportsCatalog.fixture(SportsCatalog.shl, game).score)
        assertEquals(MatchStatus.SCHEDULED, SportsCatalog.fixture(SportsCatalog.shl, game).status)
        val result = SportsCatalog.fixture(SportsCatalog.shl, game.copy(result = "2-1"))
        assertEquals(Score(2, 1), result.score)
        assertEquals(MatchStatus.RESULT_SNAPSHOT, result.status)
        val previous = Locale.getDefault()
        try { Locale.setDefault(Locale.US); assertEquals("2–1", result.result) }
        finally { Locale.setDefault(previous) }
    }
    @Test fun allsvenskanEpgRequiresBothTeamsAndDoesNotUseGenericHockeyTitles() {
        val game = ShlGame("bik", "BIK Karlskoga", "MoDo Hockey", now, null)
        assertTrue(ShlEpgMatcher.confirmed(game, "BIK – MoDo", null, now, now + 3 * 60 * 60_000, listOf(game)))
        assertFalse(ShlEpgMatcher.confirmed(game, "HockeyAllsvenskan", null, now, now + 3 * 60 * 60_000, listOf(game)))
        assertFalse(ShlEpgMatcher.confirmed(game, "BIK - AIK", null, now, now + 3 * 60 * 60_000, listOf(game)))
    }
}
