package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ShlDataTest {
    private val game = ShlGame("test", "Färjestad BK", "IF Malmö Redhawks", Instant.parse("2026-10-10T13:15:00Z").toEpochMilli(), null)
    private fun match(title: String, description: String? = null, start: Long = game.faceoff - 15 * 60_000, stop: Long = game.faceoff + 3 * 60 * 60_000,
        other: List<ShlGame> = emptyList()) = ShlEpgMatcher.confirmed(game, title, description, start, stop, listOf(game) + other)

    @Test fun requiresBothTeamsAndTiming() {
        assertTrue(match("Färjestad - Malmö"))
        assertTrue(match("SHL", "FBK möter Malmö"))
        assertFalse(match("SHL"))
        assertFalse(match("FBK"))
        assertFalse(match("Färjestaden - Malmö"))
        assertFalse(match("FBK - Malmö", start = game.faceoff - 2 * 60 * 60_000))
        assertFalse(match("FBK - Malmö", stop = game.faceoff - 1))
        assertFalse(match("FBK - Malmö", stop = game.faceoff + 12 * 60 * 60_000))
    }
    @Test fun rejectsHighlightsAndAmbiguousMultiplexDescription() {
        assertFalse(match("FBK - Malmö highlights"))
        assertFalse(match("Repris FBK - Malmö"))
        val other = game.copy(id = "other", home = "Brynäs IF", away = "Luleå HF")
        assertFalse(match("SHL", "FBK Malmö Brynäs Luleå", other = listOf(other)))
        assertTrue(match("FBK - Malmö", other = listOf(other)))
    }
    @Test fun stockholmMatchdayUsesLocalDateIncludingDst() {
        assertTrue(game.fbk)
        assertTrue(game.on(LocalDate.parse("2026-10-10")))
        assertFalse(game.on(LocalDate.parse("2026-10-11")))
        assertTrue(game.copy(faceoff = Instant.parse("2026-10-09T22:15:00Z").toEpochMilli()).on(LocalDate.parse("2026-10-10")))
    }
    @Test fun discoversCurrentIdAndRejectsConflictingOrMissingIds() {
        assertEquals("987", SwehockeyParser.discover("<a href=\"/ScheduleAndResults/Live/987\">SHL</a>"))
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.discover("<html>maintenance</html>") }
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.discover("<a href=\"/ScheduleAndResults/Live/987\">SHL</a><a href=\"/ScheduleAndResults/Live/988\">SHL</a>") }
    }
    @Test fun respectsRobotsRestrictionsAndFailsClosedOnUnexpectedMarkup() {
        assertTrue(SwehockeyParser.robotsPermit("User-agent: *\nDisallow:\n"))
        assertFalse(SwehockeyParser.robotsPermit("User-agent: *\nDisallow: /\n"))
        assertFalse(SwehockeyParser.robotsPermit("User-agent: *\nDisallow: /ScheduleAndResults/\n"))
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.robotsPermit("<html>challenge</html>") }
    }
    @Test fun sourceUpdateTimestampIsSeparateFromFetchTime() {
        assertEquals(Instant.parse("2026-10-09T09:33:00Z").toEpochMilli(), SwehockeyParser.lastUpdated("Last update: 2026-10-09 11:33"))
        assertNull(SwehockeyParser.lastUpdated("<html>no source timestamp</html>"))
    }
    @Test fun parsesDesktopCellsWithRepeatedDatesAndNeverInventsScore() {
        val html = "<table><h2>Schedule and Results</h2><tr><th>Date</th><th>Time</th><th>Game</th><th>Result</th></tr>" +
            "<tr><td>2026-10-10</td><td class=\"d-sm-none\">duplicate mobile cell</td><td><span title=\"100\">15:15</span></td><td>Färjestad BK - IF Malmö Redhawks</td><td> </td></tr>" +
            "<tr><td/><td class=\"d-sm-none\">18:00</td><td><span title=\"101\">18:00</span></td><td>Brynäs IF - Luleå HF</td><td><a href=\"/Game/GameSummary/555\">2 - 1</a></td></tr></table>"
        val parsed = SwehockeyParser.schedule(html)
        assertEquals(2, parsed.size)
        assertEquals(game.faceoff, parsed[0].faceoff)
        assertNull(parsed[0].result)
        assertEquals("555", parsed[1].id)
        assertEquals("2-1", parsed[1].result)
        assertTrue(parsed.all { it.on(LocalDate.parse("2026-10-10")) })
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.schedule("<table><h2>Schedule and Results</h2></table>") }
    }
    @Test fun standingsOnlyAcceptFullKnownRanking() {
        val html = "<table><h2>Standings</h2><tr><th>RK</th><th>Team</th><th>GP</th><th>W</th><th>T</th><th>L</th><th>GF:GA (GD)</th><th>TP</th></tr>" + (1..14).joinToString("") { rank ->
            "<tr><td>$rank</td><td>Team $rank</td><td>1</td><td>1</td><td>0</td><td>0</td><td>3:0</td><td class=\"d-sm-none\">3</td><td>3</td></tr>"
        } + "<tr><td colspan=\"12\"><hr class=\"hrType1\" /></td></tr></table>"
        assertEquals(14, SwehockeyParser.standings(html).size)
        assertEquals(3, SwehockeyParser.standings(html).first().points)
        assertEquals(1, SwehockeyParser.standings(html).first().played)
        assertEquals(3, SwehockeyParser.standings(html).first().goalDifference)
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.standings(html.replace("3:0", "3:0 (-1)")) }
        assertNull(SwehockeyParser.standings(html.replace("3:0", "unknown")).first().goalDifference)
        assertThrows(IllegalArgumentException::class.java) { SwehockeyParser.standings(html.replace("<td>14</td>", "<td>1</td>")) }
    }
    @Test fun twitchOfflineRequiresValidEmptyApiResponse() {
        assertEquals(TwitchState.Offline, TwitchStatus.parse("{\"data\":[]}"))
        assertThrows(org.json.JSONException::class.java) { TwitchStatus.parse("{\"error\":\"Unauthorized\",\"status\":401}") }
        val live = TwitchStatus.parse("{\"data\":[{\"user_login\":\"ohnepixel\",\"type\":\"live\",\"title\":\"Counter-Strike\",\"viewer_count\":123}]}" )
        assertEquals(TwitchState.Live("Counter-Strike", 123), live)
        assertThrows(IllegalArgumentException::class.java) { TwitchStatus.parse("{\"data\":[{\"user_login\":\"someoneelse\",\"type\":\"live\",\"title\":\"X\",\"viewer_count\":1}]}") }
    }
}
