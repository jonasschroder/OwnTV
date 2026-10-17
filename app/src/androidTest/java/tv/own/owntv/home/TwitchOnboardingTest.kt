package tv.own.owntv.home

import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
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

    private fun activate(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        node.assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }

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
                    }, {}, settingsMode = true)
            }
        }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)))
        compose.waitUntil { attempts.get() == 1 }
        compose.onNodeWithText(activation.code).assertIsDisplayed()
        compose.onNodeWithText(activation.verificationUri).assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("twitch-activation-qr").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("twitch-activation-qr").assertIsDisplayed()
        compose.onNodeWithTag("twitch-public-client-id").assertDoesNotExist()
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_close)))
        compose.waitUntil { cancelled.get() == 1 }
        assertEquals(1, attempts.get())
    }

    @Test fun advancedPublicIdOverrideTakesPrecedenceAndRemainsEditable() {
        val prefs = compose.activity.getSharedPreferences("twitch-onboarding-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("twitch-client-id", "manualPublicClientOverride").commit()
        val received = java.util.concurrent.CopyOnWriteArrayList<String>()
        val cancelled = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                TwitchCompanionUi(true, true, prefs, "builtInPublicClientFixture", { false }, { TwitchState.Unavailable },
                    { id, _ ->
                        received.add(id)
                        if (received.size == 1) {
                            try { awaitCancellation() } finally { cancelled.incrementAndGet() }
                        } else error("Synthetic authorization failure")
                    }, {})
            }
        }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)))
        compose.waitUntil { received.isNotEmpty() }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_edit_id)))
        compose.waitUntil { cancelled.get() == 1 }
        compose.onNodeWithTag("twitch-public-client-id").assertIsDisplayed().performTextReplacement("updatedPublicClientOverride")
        activate(compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_twitch_connect)).filter(hasClickAction()).onLast())
        compose.waitUntil { received.size == 2 }
        assertEquals(listOf("manualPublicClientOverride", "updatedPublicClientOverride"), received)
    }
    @Test fun authorizationFailureShowsSafeStageAndHttpStatus(){
        val prefs=compose.activity.getSharedPreferences("twitch-onboarding-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        compose.setContent {MaterialTheme {
            TwitchCompanionUi(true,true,prefs,"publicClientFixture123",{false},{TwitchState.Unavailable},
                {_,_->throw TwitchFailure(TwitchFailureKind.ACCESS,TwitchStage.DEVICE,403)}, {})
        }}
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)))
        val stage=compose.activity.getString(R.string.mintv_twitch_stage_device)
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_diagnostic_http,stage,403)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_error_access)).assertIsDisplayed()
    }
    @Test fun previewVisibilityChangesKeepPendingAuthorizationButBackgroundCancels(){
        val prefs=compose.activity.getSharedPreferences("twitch-onboarding-test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val cancelled=AtomicInteger();val attempts=AtomicInteger()
        var hideStatus:(()->Unit)?=null;var background:(()->Unit)?=null
        val activation=TwitchActivation.parse("ABCD1234","https://www.twitch.tv/activate?public=true&device-code=ABCD1234")
        compose.setContent {MaterialTheme {
            var visible by remember{mutableStateOf(true)};var active by remember{mutableStateOf(true)}
            hideStatus={visible=false};background={active=false}
            TwitchCompanionUi(visible,active,prefs,"publicClientFixture123",{false},{TwitchState.Unavailable},
                {_,show->attempts.incrementAndGet();show(activation);try{awaitCancellation()}finally{cancelled.incrementAndGet()}},{})
        }}
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)))
        compose.waitUntil {attempts.get()==1}
        compose.runOnIdle{hideStatus!!.invoke()}
        compose.onNodeWithText(activation.code).assertIsDisplayed();assertEquals(0,cancelled.get());assertEquals(1,attempts.get())
        compose.runOnIdle{background!!.invoke()}
        compose.waitUntil {cancelled.get()==1}
        compose.onNodeWithText(activation.code).assertDoesNotExist()
    }
}
