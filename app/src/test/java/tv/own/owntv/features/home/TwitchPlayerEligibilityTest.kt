package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class TwitchPlayerEligibilityTest {
    @Test fun android14CompatibleExportedTvHandlerRequiresExplicitTwitchAuthority() {
        assertTrue(twitchDirectPlayerEligible(true, true, 23, true, setOf("www.twitch.tv")))
        assertTrue(twitchDirectPlayerEligible(true, true, 37, true, setOf("twitch.tv")))
        // A TV browser with a generic VIEW filter must never become a Twitch player fallback.
        for (hosts in listOf(emptySet(), setOf("*"), setOf("www.twitch.tv.evil.example"), setOf("example.com")))
            assertFalse(twitchDirectPlayerEligible(true, true, 37, true, hosts))
        assertFalse(twitchDirectPlayerEligible(true, true, 22, true, setOf("www.twitch.tv")))
        assertFalse(twitchDirectPlayerEligible(true, true, 37, false, setOf("www.twitch.tv")))
        assertFalse(twitchDirectPlayerEligible(false, true, 37, true, setOf("www.twitch.tv")))
        assertFalse(twitchDirectPlayerEligible(true, false, 37, true, setOf("www.twitch.tv")))
    }
}
