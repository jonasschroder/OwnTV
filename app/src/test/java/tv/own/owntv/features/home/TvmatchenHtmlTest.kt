package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TvmatchenHtmlTest {
    private val html = javaClass.getResource("/shl-broadcast/tvmatchen-match.html")!!.readText()
    private val path = "/match/farjestad-bk-malmo-redhawks-1896181"
    private val now = Instant.parse("2026-10-10T10:00:00Z")
    private val fixture = BroadcastFixture("SHL", "Färjestad BK", "IF Malmö Redhawks", LocalDate.parse("2026-10-10"), Instant.parse("2026-10-10T13:15:00Z"))
    private fun parse(value: String = html, game: BroadcastFixture = fixture) = runCatching { TvmatchenHtml.assignment(value, path, game, now) }.getOrNull()

    @Test fun savedPublicHtmlResolvesExplicitUtcAndSeparatesStreaming() {
        val value = parse()!!
        assertEquals(listOf(BroadcastChannel("TV4 Play Hockey", false), BroadcastChannel("TV4 Sport Live 2", true)), value.channels)
        assertEquals("15:15", stockholmTime(value.fixture.faceoff.toEpochMilli()))
        assertEquals(now.plusSeconds(7200), value.expiresAt)
    }
    @Test fun missingOffsetDoesNotAssumeStockholmOrUtc() {
        val changed = html.replace("13:15:00.000Z", "13:15:00.000")
        assertNotEquals(html, changed)
        assertNull(parse(changed))
    }
    @Test fun dateFaceoffLeagueAndBothTeamsMustAgree() {
        listOf(fixture.copy(home = "Luleå HF"), fixture.copy(away = "Timrå IK"), fixture.copy(league = "NHL"),
            fixture.copy(date = fixture.date.plusDays(1)), fixture.copy(faceoff = fixture.faceoff.plusSeconds(3600)))
            .forEach { assertNull(parse(game = it)) }
    }
    @Test fun canonicalPathAndRenderedLeagueAreRequired() {
        assertNull(parse(html.replace("href=\"https://www.tvmatchen.nu$path\"", "href=\"https://www.tvmatchen.nu/match/other-123\"")))
        assertNull(parse(html.replace("/ishockey/shl", "/ishockey/nhl")))
    }
    @Test fun absentVisibleChannelOrChangedStructureFailsClosed() {
        assertNull(parse(html.replace("rt-match-channel-list__channel-text", "changed")))
        assertNull(parse("<html>maintenance</html>"))
        assertNull(parse(html.replace("self.__next_f.push", "changed.push")))
    }
    @Test fun duplicateFixtureOrArchivedRecordIsRejected() {
        val script = html.substringAfter("<script>").substringBefore("</script>")
        assertNull(parse(html.replace("</body>", "<script>$script</script></body>")))
        assertNull(parse(html.replace("is_archived\\\":false", "is_archived\\\":true")))
    }
    @Test fun findsOnlyRenderedLinksAndRejectsAmbiguousTeamSlugs() {
        val listing = "<a href=\"/ishockey/shl\">SHL</a><a href=\"$path\">game</a><a href=\"$path\">duplicate</a>" +
            "<script>self.__next_f.push([1,\"href=\\\"/match/lulea-hf-timra-ik-123\\\"\"])</script>"
        assertEquals(listOf(path), TvmatchenHtml.fixtureLinks(listing))
        assertEquals(listOf(path), TvmatchenHtml.candidates(listOf(path, "/match/farjestad-bk-malmo-redhawks-lulea-123"), fixture))
        assertTrue(TvmatchenHtml.candidates(listOf(path), fixture.copy(home = "Okänt lag")).isEmpty())
    }
    @Test fun unchangedTermsAreMandatoryNotInferredFromThePrivateUsePhrase() {
        assertFalse(TvmatchenHtml.termsUnchanged("<div class=\"article\">Vi tillåter endast användning av sidan för privat bruk.</div>"))
        assertFalse(TvmatchenHtml.termsUnchanged("<html>new terms</html>"))
    }
    @Test fun oversizeInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TvmatchenHtml.assignment("x".repeat(800001), path, fixture, now) }
    }
    @Test fun allowedMatchPagesNeverAuthorizeApiGoOrOtherBlockedPaths() {
        val robots = "User-agent: *\nDisallow: /api\nDisallow: /go?\nDisallow: /wp-admin/\n"
        assertTrue(BroadcastRobots.permits(robots, path))
        assertTrue(BroadcastRobots.permits(robots, "/ishockey/shl"))
        assertFalse(BroadcastRobots.permits(robots, "/api/fixtures"))
        assertFalse(BroadcastRobots.permits(robots, "/go?channel=tv4"))
    }
    @Test fun agentRulesWildcardsAndLongestAllowAreRespected() {
        val body = "User-agent: *\nDisallow: /\nAllow: /match/\nDisallow: /match/*-123$\n"
        assertTrue(BroadcastRobots.permits(body, path))
        assertFalse(BroadcastRobots.permits(body, "/match/a-123"))
        assertFalse(BroadcastRobots.permits("User-agent: MinTV-ExperimentalBroadcast\nDisallow: /\nUser-agent: *\nAllow: /\n", path))
    }
    @Test fun unknownOrMalformedRobotsStopsRatherThanGuessing() {
        assertThrows(IllegalArgumentException::class.java) { BroadcastRobots.permits("<html>challenge</html>", path) }
        assertFalse(BroadcastRobots.permits("User-agent: *\nCrawl-delay: 30\n", path))
        assertFalse(BroadcastRobots.permits("User-agent: OtherApp\nAllow: /\n", path))
    }
    @Test fun indexUsesVerifiedFixtureDateAndInstantBeforeFetchingAPage() {
        val listing = javaClass.getResource("/shl-broadcast/tvmatchen-listing.html")!!.readText()
        val index = TvmatchenHtml.fixtureIndex(listing, now)
        assertEquals(listOf(path), index[fixtureKey(fixture)])
        assertNull(index[fixtureKey(fixture.copy(faceoff = fixture.faceoff.plusSeconds(86400)))])
        assertThrows(IllegalArgumentException::class.java) { TvmatchenHtml.fixtureIndex(listing.replace("13:15:00.000Z", "13:15:00.000"), now) }
    }
    @Test fun missingIndexOrUnsupportedDateStructureNeverFallsBackToSlugGuessing() {
        val listing = javaClass.getResource("/shl-broadcast/tvmatchen-listing.html")!!.readText()
        assertThrows(IllegalArgumentException::class.java) { TvmatchenHtml.fixtureIndex(listing.replace("fixturesByTimeData", "changed"), now) }
        assertThrows(IllegalArgumentException::class.java) { TvmatchenHtml.fixtureIndex(listing, now.plusSeconds(86400)) }
    }
    @Test fun mixedHockeyListingRetainsOnlyTheVerifiedShlFixture() {
        val listing = javaClass.getResource("/shl-broadcast/tvmatchen-listing.html")!!.readText()
        val script = Regex("<script>self\\.__next_f\\.push\\((.*?)\\)</script>").find(listing)!!
        val wrapper = org.json.JSONArray(script.groupValues[1])
        val chunk = wrapper.getString(1)
        val data = org.json.JSONArray(chunk.substringAfter(':'))
        val rows = data.getJSONObject(3).getJSONObject("data").getJSONObject("fixturesByTimeData").getJSONObject("2026-10-10").getJSONArray("newFixtures")
        repeat(32) { rows.put(org.json.JSONObject().put("league", "Liiga")) }
        wrapper.put(1, chunk.substringBefore(':') + ":" + data.toString())
        val mixed = listing.replace(script.value, "<script>self.__next_f.push($wrapper)</script>")
        assertEquals(mapOf(fixtureKey(fixture) to listOf(path)), TvmatchenHtml.fixtureIndex(mixed, now))
    }
}
