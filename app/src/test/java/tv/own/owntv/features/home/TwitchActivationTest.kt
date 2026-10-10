package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class TwitchActivationTest {
    private val code = "ABCDEFGH"
    @Test fun documentedPublicActivationAccepted() {
        val value = "https://www.twitch.tv/activate?public=true&device-code=$code"
        assertEquals(TwitchActivation(code, value), TwitchActivation.parse(code, value))
    }
    @Test fun untrustedOriginsAndUnexpectedParametersFailClosed() {
        val rejected = listOf("http://www.twitch.tv/activate?public=true&device-code=$code",
            "https://evil.example/activate?public=true&device-code=$code", "https://www.twitch.tv.evil.example/activate?public=true&device-code=$code",
            "https://user@www.twitch.tv/activate?public=true&device-code=$code", "https://www.twitch.tv:8443/activate?public=true&device-code=$code",
            "https://www.twitch.tv/other?public=true&device-code=$code", "https://www.twitch.tv/activate?public=false&device-code=$code",
            "https://www.twitch.tv/activate?public=true&device-code=WRONGCODE", "https://www.twitch.tv/activate?public=true&device-code=$code&redirect=evil",
            "https://www.twitch.tv/activate?public=true&public=true&device-code=$code", "https://www.twitch.tv/activate?public=true&device-code=$code#secret")
        rejected.forEach { assertTrue(it, runCatching { TwitchActivation.parse(code, it) }.isFailure) }
    }
    @Test fun malformedAndOversizedValuesRejected() {
        assertTrue(runCatching { TwitchActivation.parse("", "https://www.twitch.tv/activate") }.isFailure)
        assertTrue(runCatching { TwitchActivation.parse(code, "x".repeat(513)) }.isFailure)
    }
}
