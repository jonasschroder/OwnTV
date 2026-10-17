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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
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
    @Test fun offlineAndUnavailableCardsOpenOfficialAppWithoutAuthorizationAndRestoreFocus() {
        val prefs = prefs(); prefs.edit().putString("twitch-player-package", "keep.optional.client").commit()
        val launches = AtomicInteger(); var resume: (() -> Unit)? = null; var offline: (() -> Unit)? = null
        compose.setContent { MaterialTheme {
            var active by remember { mutableStateOf(true) }; resume = { active = !active }
            var isOffline by remember { mutableStateOf(false) }; offline = { isOffline = true }
            key(isOffline) {
                TwitchCompanionUi(true, active, prefs, "publicFixture", { false },
                    { if (isOffline) TwitchState.Offline else TwitchState.Unavailable },
                    { _, _ -> error("Opening Twitch must never authorize") }, {}, modifier = Modifier.testTag("home-twitch"), compact = true,
                    listPlayers = { error("Home must not discover alternative players") },
                    onOpenSettings = { error("Opening Twitch must not require Settings") },
                    openOfficialApp = { launches.incrementAndGet(); true })
            }
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_connect)).assertDoesNotExist()
        activate(compose.onNodeWithTag("home-twitch"))
        assertEquals(1, launches.get())
        compose.runOnIdle { resume!!.invoke() }; compose.runOnIdle { resume!!.invoke() }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("home-twitch").assertIsFocused(); true }.getOrDefault(false) }
        compose.runOnIdle { offline!!.invoke() }
        compose.waitUntil(5000) { compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_twitch_offline)).fetchSemanticsNodes().isNotEmpty() }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_open_app)))
        assertEquals(2, launches.get())
        assertEquals("keep.optional.client", prefs.getString("twitch-player-package", null))
        assertFalse(prefs.contains("twitch-setup-seen"))
    }
    @Test fun liveCardOpensOfficialAppInsteadOfSavedAlternativeClient() {
        val prefs = prefs(); prefs.edit().putString("twitch-player-package", "fixture.second").commit()
        val launches = AtomicInteger()
        compose.setContent { MaterialTheme {
            TwitchCompanionUi(true, true, prefs, "publicFixture", { true }, { TwitchState.Live("Synthetic live stream", 1234, "Counter-Strike 2") },
                { _, _ -> error("Opening Twitch must not authorize") }, {}, modifier = Modifier.testTag("home-twitch"),
                listPlayers = { error("Home must not open its old player picker") }, compact = true,
                openOfficialApp = { launches.incrementAndGet(); true })
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Synthetic live stream").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_watch)).assertDoesNotExist()
        activate(compose.onNodeWithTag("home-twitch"))
        assertEquals(1, launches.get())
        assertEquals("fixture.second", prefs.getString("twitch-player-package", null))
    }
    @Test fun missingOfficialAppShowsExplicitStoreActionAndBackRestoresFocus() {
        val prefs = prefs(); val stores = AtomicInteger()
        compose.setContent { MaterialTheme {
            TwitchCompanionUi(true, true, prefs, "publicFixture", { false }, { TwitchState.Unavailable },
                { _, _ -> error("Missing app must not start authorization") }, {}, modifier = Modifier.testTag("home-twitch"), compact = true,
                openOfficialApp = { false }, installOfficialApp = { stores.incrementAndGet(); false })
        } }
        activate(compose.onNodeWithTag("home-twitch"))
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_app_missing)).assertIsDisplayed()
        assertEquals(0, stores.get())
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_app_find)))
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_app_store_unavailable)).assertIsDisplayed()
        assertEquals(1, stores.get())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("home-twitch").assertIsFocused(); true }.getOrDefault(false) }
    }
    @Test fun realTvLauncherReturnsToHomeCardAfterBack() {
        val prefs = prefs()
        val playback = TwitchPlayback(compose.activity, officialPackage = instrumentation.context.packageName)
        compose.setContent { MaterialTheme {
            val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
            TwitchCompanionUi(true, lifecycle.isAtLeast(Lifecycle.State.RESUMED), prefs, "publicFixture", { false }, { TwitchState.Offline },
                { _, _ -> error("External launch must not authorize") }, {}, modifier = Modifier.testTag("home-twitch"), compact = true,
                openOfficialApp = playback::openOfficialApp)
        } }
        activate(compose.onNodeWithTag("home-twitch"))
        instrumentation.waitForIdleSync()
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")).bufferedReader().use { it.readText() }
        assertTrue("A real external TV activity must have opened", dump.contains("TwitchFixtureLauncher"))
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("home-twitch").assertIsFocused(); true }.getOrDefault(false) }
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
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_setup_alternatives_missing)).assertIsDisplayed()
        screenshot("v08-setup-twitch-no-client-1080p-synthetic.png")
        val install = compose.activity.getString(R.string.mintv_setup_player_install, compose.activity.getString(R.string.mintv_setup_soundtv))
        activate(compose.onNodeWithText(install).performScrollTo())
        assertEquals(listOf("com.s0und.s0undtv"), installations)
        compose.runOnIdle { resume!!.invoke() }; compose.runOnIdle { resume!!.invoke() }
        compose.waitUntil(5000) { discoveries.get() == 2 }
        activate(compose.onNodeWithText(compose.activity.getString(R.string.mintv_setup_player_recheck)).performScrollTo())
        compose.waitUntil(5000) { discoveries.get() == 3 }
        val choice = compose.activity.getString(R.string.mintv_setup_player_use_manual, manual.name)
        activate(compose.onNodeWithText(choice).performScrollTo())
        assertEquals("fixture.manual", prefs.getString("twitch-player-package", null))
        assertEquals("storedPublicId", prefs.getString("twitch-client-id", null)); assertEquals(0, reads.get())
        screenshot("v08-setup-twitch-manual-client-1080p-synthetic.png")
    }
}
