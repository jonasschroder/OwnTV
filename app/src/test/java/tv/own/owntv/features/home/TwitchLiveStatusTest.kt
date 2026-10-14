package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class TwitchLiveStatusTest {
    @Test fun officialLiveResponseIncludesTitleCategoryAndViewersWithoutChangingOfflineSemantics() {
        assertEquals(TwitchState.Offline, TwitchStatus.parse("""{"data":[]}"""))
        val live = TwitchStatus.parse("""{"data":[{"user_login":"ohnepixel","type":"live","title":"Synthetic stream","viewer_count":12000,"game_name":"Counter-Strike 2"}]}""") as TwitchState.Live
        assertEquals("Synthetic stream", live.title); assertEquals(12000, live.viewers); assertEquals("Counter-Strike 2", live.category)
        assertNull((TwitchStatus.parse("""{"data":[{"user_login":"ohnepixel","type":"live","title":"Synthetic","viewer_count":1}]}""") as TwitchState.Live).category)
    }
    @Test fun wrongIdentityNegativeViewersAndMultipleStreamsFailClosed() {
        for (row in listOf("""{"user_login":"another","type":"live","title":"Synthetic","viewer_count":1}""",
            """{"user_login":"ohnepixel","type":"live","title":"Synthetic","viewer_count":-1}""",
            """{"user_login":"ohnepixel","type":"rerun","title":"Synthetic","viewer_count":1}"""))
            assertTrue(runCatching { TwitchStatus.parse("""{"data":[$row]}""") }.isFailure)
        assertTrue(runCatching { TwitchStatus.parse("""{"data":[{},{}]}""") }.isFailure)
    }
}
