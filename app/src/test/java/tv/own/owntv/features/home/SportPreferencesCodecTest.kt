package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class SportPreferencesCodecTest {
    private val value = SportPreferences(listOf(SportsCatalog.farjestadId, "hockey:karlskoga", "football:degerfors-if"), false, true)
    @Test fun orderedTeamsAndOptionsRoundTripWithoutCredentials() {
        val json = SportPreferencesCodec.encode(value)
        assertEquals(value, SportPreferencesCodec.decode(json))
        assertFalse(json.contains("password"))
        assertFalse(json.contains("url"))
    }
    @Test fun profileLifetimePreventsReusedDatabaseIdInheritingOldSelection() {
        val saved = SportPreferencesCodec.bound(value, 100L)
        assertEquals(value, SportPreferencesCodec.forOwner(saved, 100L))
        assertEquals(SportPreferences(), SportPreferencesCodec.forOwner(saved, 101L))
        assertEquals(SportPreferences(), SportPreferencesCodec.forOwner(null, 100L))
    }
    @Test fun malformedOversizedOrUnsupportedTeamIdsFailClosed() {
        for (invalid in listOf(null, "{}", "broken", "x".repeat(32_769), "{\"teams\":[\"../../secret\"]}", "{\"teams\":[\"shl:fbk\"]}"))
            assertEquals(SportPreferences(), SportPreferencesCodec.decode(invalid))
    }
    @Test fun duplicateIdsCollapseWithoutChangingPrimary() {
        assertEquals(value, SportPreferencesCodec.decode(SportPreferencesCodec.encode(value.copy(teamIds = value.teamIds + value.teamIds))))
    }
    @Test fun unknownValidClubIdSurvivesCatalogAndLeagueChanges() {
        val future = SportPreferences(listOf("hockey:new-club"))
        assertEquals(future, SportPreferencesCodec.decode(SportPreferencesCodec.encode(future)))
    }
}
