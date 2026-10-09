package tv.own.owntv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MinTvForegroundPolicyTest {
    @Test fun coldActivityNeverRestoresAFormerActivitysStream() {
        val calls = mutableListOf<String>()
        val policy = MinTvForegroundPolicy({ calls.add("discard") }, { calls.add("restore") })
        policy.onResume(allowFullscreenResume = true)
        assertEquals(listOf("discard"), calls)
    }

    @Test fun warmNormalAppIntentSuppressesRestorationBeforeResume() {
        val calls = mutableListOf<String>()
        val policy = MinTvForegroundPolicy({ calls.add("discard") }, { calls.add("restore") })
        policy.onResume(allowFullscreenResume = true)
        policy.onNormalAppEntry()
        policy.onResume(allowFullscreenResume = true)
        assertEquals(listOf("discard", "discard"), calls)
    }

    @Test fun returningWithoutAnAppIconIntentRetainsFullscreenResume() {
        val calls = mutableListOf<String>()
        val policy = MinTvForegroundPolicy({ calls.add("discard") }, { calls.add("restore") })
        policy.onResume(allowFullscreenResume = true)
        policy.onResume(allowFullscreenResume = true)
        assertEquals(listOf("discard", "restore"), calls)
    }
    @Test fun returningToHomeCannotResurrectAStaleEngineSnapshot() {
        val calls = mutableListOf<String>()
        val policy = MinTvForegroundPolicy({ calls.add("discard") }, { calls.add("restore") })
        policy.onResume(allowFullscreenResume = false)
        policy.onResume(allowFullscreenResume = false)
        assertEquals(listOf("discard", "discard"), calls)
    }
}
