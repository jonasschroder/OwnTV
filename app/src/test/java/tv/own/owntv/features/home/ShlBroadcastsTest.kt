package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ShlBroadcastsTest {
    private val instant = Instant.parse("2026-10-10T13:15:00Z")
    private val fixture = BroadcastFixture("SHL", "Färjestad BK", "IF Malmö Redhawks", LocalDate.parse("2026-10-10"), instant)
    private val assignment = BroadcastAssignment(fixture, listOf(BroadcastChannel("TV4 Sport Live 2", true),
        BroadcastChannel("TV4 Play Hockey", false)), instant.minusSeconds(60), instant.plusSeconds(600), "licensed-test")

    @Test fun documentedQualityAndCountryVariantsMatch() {
        val channel = assignment.channels.first()
        listOf("TV4 Sport Live 2", "TV4 SPORT LIVE 2 HD", "SE | TV4 Sport Live 2", "SE: TV4 Sport Live 2 FHD").forEach {
            assertTrue(it, BroadcastResolver.matches(channel, it))
        }
    }
    @Test fun channelNumbersNeverCollapse() {
        val channel = assignment.channels.first()
        listOf("TV4 Sport Live 1", "TV4 Sport Live 3 HD", "SE: TV4 Sport Live 4 FHD", "TV4 Sport Live", "TV4 Sport Live 12").forEach {
            assertFalse(it, BroadcastResolver.matches(channel, it))
        }
    }
    @Test fun unrelatedCountryAndNamesDoNotFuzzyMatch() {
        val channel = assignment.channels.first()
        assertFalse(BroadcastResolver.matches(channel, "NO: TV4 Sport Live 2"))
        assertFalse(BroadcastResolver.matches(channel, "TV4 Sport Live 2 Extra"))
    }
    @Test fun streamingBrandNeverBecomesLinearByName() {
        assertFalse(BroadcastResolver.matches(assignment.channels[1], "TV4 Play Hockey"))
        assertFalse(BroadcastResolver.matches(BroadcastChannel("TV4 Play Hockey", true), "TV4 Play Hockey"))
    }
    @Test fun exactFixtureAndAliasesAccepted() {
        assertNotNull(BroadcastResolver.usable(fixture, assignment, instant))
        assertNotNull(BroadcastResolver.usable(fixture, assignment.copy(fixture = fixture.copy(home = "FBK", away = "Malmö")), instant))
    }
    @Test fun leagueBothTeamsDateAndFaceoffAreMandatory() {
        val mismatches = listOf(fixture.copy(league = "Hockeyallsvenskan"), fixture.copy(home = "Frölunda HC"),
            fixture.copy(away = "Luleå HF"), fixture.copy(date = fixture.date.plusDays(1)), fixture.copy(faceoff = instant.plusSeconds(60)),
            fixture.copy(home = "Okänt lag"), fixture.copy(home = "FBK Malmö"), fixture.copy(home = fixture.away))
        mismatches.forEach { assertNull(it.toString(), BroadcastResolver.usable(fixture, assignment.copy(fixture = it), instant)) }
    }
    @Test fun expiredFutureAndInvalidMetadataFailClosed() {
        listOf(assignment.copy(expiresAt = instant), assignment.copy(checkedAt = instant.plusSeconds(1)),
            assignment.copy(source = ""), assignment.copy(channels = emptyList()), assignment.copy(expiresAt = instant.plusSeconds(90_000)))
            .forEach { assertNull(BroadcastResolver.usable(fixture, it, instant)) }
    }
    @Test fun rescheduleInvalidatesOldMapping() {
        assertNull(BroadcastResolver.usable(fixture.copy(faceoff = instant.plusSeconds(3600)), assignment, instant))
    }
    @Test fun utcUsesStockholmActualDayAndSummerOffset() {
        assertEquals("15:15", stockholmTime(instant.toEpochMilli()))
        assertEquals(LocalDate.parse("2026-10-11"), stockholmDay(Instant.parse("2026-10-10T22:30:00Z").toEpochMilli()))
        assertEquals(ZoneOffset.ofHours(2), instant.atZone(Stockholm).offset)
    }
    @Test fun winterAndDstTransitionUseZoneDatabase() {
        assertEquals("15:15", stockholmTime(Instant.parse("2026-11-10T14:15:00Z").toEpochMilli()))
        val before = Instant.parse("2026-10-25T00:30:00Z").atZone(Stockholm)
        val after = Instant.parse("2026-10-25T01:30:00Z").atZone(Stockholm)
        assertEquals(ZoneOffset.ofHours(2), before.offset)
        assertEquals(ZoneOffset.ofHours(1), after.offset)
        assertNotEquals(before.toInstant(), after.toInstant())
    }
    @Test fun multipleQualityAndSourceEntriesRemainSeparateCandidates() {
        val entries = listOf(Triple(1, 10, "TV4 Sport Live 2 HD"), Triple(2, 10, "TV4 Sport Live 2 FHD"), Triple(3, 20, "SE: TV4 Sport Live 2"))
        assertEquals(3, entries.filter { BroadcastResolver.matches(assignment.channels.first(), it.third) }.distinctBy { it.first }.size)
    }
    @Test fun defaultAdapterIsNetworkFreeAndHasNoAssignments() = runTest {
        assertNull(NoBroadcastMetadata.assignment(fixture))
    }
}
