package tv.own.owntv.update

import android.view.KeyEvent
import android.content.res.Configuration
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.features.update.GlobalUpdateEntry
import tv.own.owntv.features.update.MinTvUpdater
import org.koin.core.context.GlobalContext
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.features.update.UpdatePresentation
import tv.own.owntv.features.update.UpdatePolicy

class UpdateDialogNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var fixture by mutableStateOf<Pair<String, MinTvUpdater.State>?>(null)
    private var cached by mutableStateOf(false)
    private var actions = 0
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Suppress("DEPRECATION")
    @Before fun disposableOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        // Foundation clickable uses SystemDefined focusability and excludes touchscreen mode.
        // Model a TV remote without requesting focus on any node or changing physical settings.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.waitForIdle()
        assertFalse(compose.activity.window.decorView.isInTouchMode)
        GlobalContext.get().get<MinTvUpdater>().dismiss()
    }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun mount(): MutableState<Boolean> {
        val open = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            GlobalUpdateEntry()
            if (open.value) UpdateDialog(onDismiss = { open.value = false })
            fixture?.let { (id, state) -> key(id) {
                val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("sv")) }
                val swedish = compose.activity.createConfigurationContext(config)
                val small = instrumentation.targetContext.resources.displayMetrics.widthPixels < 1500
                CompositionLocalProvider(LocalContext provides swedish, LocalResources provides swedish.resources,
                    LocalConfiguration provides config, LocalDensity provides Density(if (small) 1.2f else 1.8f)) {
                    UpdatePresentation(state, "QA", false, null, cached = cached, availableBytes = 128_000_000,
                        onDismiss = { fixture = null }, onAutomatic = {}, onAction = { actions++ })
                }
            } }
        } }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A platform dialog attaches asynchronously; require actual focus without forcing it in test.
        try {
            compose.waitUntil(5_000) {
                compose.onNodeWithText(context.getString(R.string.settings_close)).fetchSemanticsNode()
                    .config.getOrElse(SemanticsProperties.Focused) { false }
            }
        } catch (error: Throwable) {
            runCatching { compose.onNode(isDialog()).printToLog("MinTvAboutFocus") }
            throw error
        }
        return open
    }
    @Test fun dpadOkClosesAboutWithoutStartingAnInstallation() {
        val open = mount()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.mintv_update_about)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_close)).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNode(hasText(context.getString(R.string.settings_check_updates)) and isFocusable()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(open.value)
    }
    @Test fun backReturnsFromAbout() {
        val open = mount(); key(KeyEvent.KEYCODE_BACK); assertFalse(open.value)
        compose.onNodeWithTag("header-update").performSemanticsAction(SemanticsActions.RequestFocus)
        key(KeyEvent.KEYCODE_DPAD_CENTER); compose.onNode(isDialog()).assertExists()
        key(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("header-update").assertIsFocused(); true }.getOrDefault(false) }
        compose.onNode(isDialog()).assertDoesNotExist()
        verifySyntheticJourney()
    }

    /** Shared native production presentation. No downloads, installer intents or fabricated live release. */
    private fun verifySyntheticJourney() {
        val notes = "### Syntetiskt testunderlag\n" + (1..30).joinToString("\n") { "- Testpunkt $it: fjärrkontroll, läsbarhet och återgång." }
        val candidate = UpdatePolicy.Candidate("qa", "se.jonasschroder.mintv.qa", 1000099, "0.16.0-test-fixture",
            "synthetic.apk", "0".repeat(64), 42_000_000, 28, setOf("arm64-v8a"), notes, "qa-1000099")
        val available = MinTvUpdater.State.Available(candidate)
        val states = listOf(
            "checking" to MinTvUpdater.State.Checking,
            "available" to available,
            "downloading" to MinTvUpdater.State.Downloading(45, 18_900_000, candidate.bytes),
            "verifying" to MinTvUpdater.State.Verifying,
            "interrupted" to MinTvUpdater.State.Available(candidate, interrupted = true),
            "permission" to MinTvUpdater.State.Permission,
            "confirmation-handoff" to MinTvUpdater.State.Confirmation,
            "installing" to MinTvUpdater.State.Installing,
            "failed" to MinTvUpdater.State.Failed(MinTvUpdater.Problem.NETWORK),
            "installed" to MinTvUpdater.State.Installed,
            "cached" to available)
        for ((name, state) in states) {
            compose.runOnUiThread { cached = name == "cached"; fixture = name to state }
            compose.waitForIdle(); compose.onNode(isDialog()).assertExists()
            if (name == "available") {
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Uppdatera nu").assertIsFocused(); true }.getOrDefault(false) }
                key(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithText("Syntetiskt testunderlag", substring = true).assertExists()
                val notesNode = compose.onNodeWithTag("update-body")
                notesNode.assertIsFocused()
                val before = notesNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                key(KeyEvent.KEYCODE_DPAD_DOWN)
                notesNode.assertIsFocused()
                compose.waitUntil(5_000) { notesNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before }
            }
            capture(name)
            assertEquals("Presentation/focus must never start a transfer or installation", 0, actions)
            key(KeyEvent.KEYCODE_BACK); assertNull(fixture)
        }
        compose.runOnUiThread { cached = false; fixture = "transition" to available }
        compose.waitForIdle()
        compose.runOnUiThread { fixture = "transition" to MinTvUpdater.State.Downloading(1, 420_000, candidate.bytes) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Fortsätt använda appen").assertIsFocused(); true }.getOrDefault(false) }
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertNull(fixture); assertEquals(0, actions)
    }

    private fun capture(name: String) {
        // Semantics can lead the rendered surface. Wait two actual draws before UiAutomation captures it.
        val drawn = CountDownLatch(1)
        compose.runOnUiThread { compose.activity.window.decorView.postOnAnimation { compose.activity.window.decorView.postOnAnimation { drawn.countDown() } } }
        assertTrue(drawn.await(5, TimeUnit.SECONDS))
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = File(compose.activity.getExternalFilesDir(null), "v016-update-screenshots").apply { mkdirs() }
        File(dir, "$name-${screenshot.width}x${screenshot.height}-synthetic.png").outputStream().use {
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }
}
