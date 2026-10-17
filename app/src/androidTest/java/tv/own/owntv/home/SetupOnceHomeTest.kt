package tv.own.owntv.home

import android.content.ComponentName
import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.features.settings.StageFullPage

class SetupOnceHomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Before fun remoteMode() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        instrumentation.setInTouchMode(false)
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun prefs() = compose.activity.getSharedPreferences("setup-once-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    private fun activate(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        node.assertIsFocused(); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val frames = CountDownLatch(1)
        compose.activity.runOnUiThread { compose.activity.window.decorView.postOnAnimation {
            compose.activity.window.decorView.postOnAnimation { frames.countDown() }
        } }
        assertTrue(frames.await(3, TimeUnit.SECONDS)); instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertEquals(1920, bitmap.width); assertEquals(1080, bitmap.height)
        val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    @Test fun contextualConnectionIsShownOnceAndOfflineCardBackRestoresFocusWithoutAdministration() {
        val prefs = prefs(); val setups = AtomicInteger(); var remount: (() -> Unit)? = null
        compose.setContent { MaterialTheme {
            var epoch by remember { mutableIntStateOf(0) }; remount = { epoch++ }
            key(epoch) {
                TwitchCompanionUi(true, true, prefs, "publicFixture", { false }, { TwitchState.Unavailable },
                    { _, _ -> error("Home must route setup into Settings") }, {}, modifier = Modifier.testTag("home-twitch"), compact = true,
                    onOpenSettings = { setups.incrementAndGet() })
            }
        } }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)))
        assertEquals(1, setups.get()); assertTrue(prefs.getBoolean("twitch-setup-seen", false))
        compose.runOnIdle { remount!!.invoke() }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_companion_settings)).assertDoesNotExist()
        activate(compose.onNodeWithTag("home-twitch"))
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_setup_twitch)).assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("home-twitch").assertIsFocused(); true }.getOrDefault(false) }
    }
    @Test fun liveCardUsesSavedDirectClientWithoutReauthorizationAndRestoresFocus() {
        val prefs = prefs(); prefs.edit().putString("twitch-player-package", "fixture.second").commit()
        val players = listOf(TwitchPlayer(ComponentName("fixture.first", "Player"), "First", true),
            TwitchPlayer(ComponentName("fixture.second", "Player"), "Second", true))
        val launches = java.util.concurrent.CopyOnWriteArrayList<TwitchPlayer>()
        compose.setContent { MaterialTheme {
            TwitchCompanionUi(true, true, prefs, "publicFixture", { true }, { TwitchState.Live("Synthetic live stream", 1234, "Counter-Strike 2") },
                { _, _ -> error("Watching must not authorize") }, {}, modifier = Modifier.testTag("home-twitch"),
                listPlayers = { players }, openPlayer = { launches.add(it); true }, compact = true)
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Synthetic live stream").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_companion_settings)).assertDoesNotExist()
        activate(compose.onNodeWithTag("home-twitch"))
        compose.waitUntil(5000) { launches.size == 1 }
        assertEquals(players[1], launches.single())
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("home-twitch").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals("fixture.second", prefs.getString("twitch-player-package", null))
    }
    @Test fun settingsPreservesPublicIdAndNeverPollsStreamsWhileRecheckingPlayerInstallation() {
        val prefs = prefs(); prefs.edit().putString("twitch-client-id", "storedPublicId").commit()
        val reads = AtomicInteger(); val discoveries = AtomicInteger(); val installations = java.util.concurrent.CopyOnWriteArrayList<String>()
        var resume: (() -> Unit)? = null
        val manual = TwitchPlayer(ComponentName("fixture.manual", "TV"), "Synthetic manual TV client", false)
        var installed = false
        compose.setContent { MaterialTheme {
            var active by remember { mutableStateOf(true) }; resume = { active = !active }
            StageFullPage(emptyList(), compose.activity.getString(R.string.mintv_setup_twitch), "", {},
                modifier = Modifier.fillMaxSize().background(MinTvNavy)) {
                TwitchCompanionUi(true, active, prefs, "builtInId", { true }, { reads.incrementAndGet(); TwitchState.Unavailable },
                    { _, _ -> error("Settings entry must not auto-authorize") }, {},
                    listPlayers = { discoveries.incrementAndGet(); if (installed) listOf(manual) else emptyList() },
                    settingsMode = true, installPlayer = { installations.add(it); installed = true; true })
            }
        } }
        compose.waitUntil(5000) { discoveries.get() == 1 }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_no_player)).assertIsDisplayed()
        screenshot("v08-setup-twitch-no-client-1080p-synthetic.png")
        val install = compose.activity.getString(R.string.mintv_setup_player_install, compose.activity.getString(R.string.mintv_setup_soundtv))
        activate(compose.onNodeWithText(install).performScrollTo())
        assertEquals(listOf("com.s0und.s0undtv"), installations)
        compose.runOnIdle { resume!!.invoke() }; compose.runOnIdle { resume!!.invoke() }
        compose.waitUntil(5000) { discoveries.get() == 2 }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_setup_player_recheck)).performScrollTo())
        compose.waitUntil(5000) { discoveries.get() == 3 }
        val choice = compose.activity.getString(R.string.mintv_twitch_open_manual, manual.name)
        activate(compose.onNodeWithText(choice).performScrollTo())
        assertEquals("fixture.manual", prefs.getString("twitch-player-package", null))
        assertEquals("storedPublicId", prefs.getString("twitch-client-id", null)); assertEquals(0, reads.get())
        screenshot("v08-setup-twitch-manual-client-1080p-synthetic.png")
    }
}
