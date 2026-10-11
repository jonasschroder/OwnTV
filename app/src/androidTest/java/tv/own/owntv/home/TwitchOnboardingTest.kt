package tv.own.owntv.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.features.home.*

/** Real onboarding presentation with a fake authorization boundary: no tokens or HTTP. */
class TwitchOnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteInputMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }

    @Test fun configuredPublicIdStartsAuthorizationOnFirstClickAndCloseCancels() {
        val prefs = compose.activity.getSharedPreferences("twitch-onboarding-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val attempts = AtomicInteger(); val cancelled = AtomicInteger()
        val publicId = "mintvPublicClientFixture1234567890"
        val activation = TwitchActivation.parse("ABCD1234", "https://www.twitch.tv/activate?public=true&device-code=ABCD1234")
        compose.setContent {
            MaterialTheme {
                TwitchCompanionUi(true, true, prefs, publicId, { false }, { TwitchState.Unavailable },
                    { id, show ->
                        assertEquals(publicId, id); attempts.incrementAndGet(); show(activation)
                        try { awaitCancellation() } finally { cancelled.incrementAndGet() }
                    }, {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)).performClick()
        compose.waitUntil { attempts.get() == 1 }
        compose.onNodeWithText(activation.code).assertIsDisplayed()
        compose.onNodeWithText(activation.verificationUri).assertIsDisplayed()
        compose.onNodeWithTag("twitch-public-client-id").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_close)).performClick()
        compose.waitUntil { cancelled.get() == 1 }
        assertEquals(1, attempts.get())
    }

    @Test fun advancedPublicIdOverrideTakesPrecedenceAndRemainsEditable() {
        val prefs = compose.activity.getSharedPreferences("twitch-onboarding-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("twitch-client-id", "manualPublicClientOverride").commit()
        val received = java.util.concurrent.CopyOnWriteArrayList<String>()
        compose.setContent {
            MaterialTheme {
                TwitchCompanionUi(true, true, prefs, "builtInPublicClientFixture", { false }, { TwitchState.Unavailable },
                    { id, _ -> received.add(id); error("Synthetic authorization failure") }, {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)).performClick()
        compose.waitUntil { received.isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_edit_id)).performClick()
        compose.onNodeWithTag("twitch-public-client-id").assertIsDisplayed().performTextReplacement("updatedPublicClientOverride")
        compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_twitch_connect)).filter(hasClickAction()).onLast().performClick()
        compose.waitUntil { received.size == 2 }
        assertEquals(listOf("manualPublicClientOverride", "updatedPublicClientOverride"), received)
    }
}
