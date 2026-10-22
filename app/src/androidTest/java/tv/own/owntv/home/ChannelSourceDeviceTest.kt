package tv.own.owntv.home

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.*
import tv.own.owntv.features.live.source.*
import tv.own.owntv.ui.theme.mpx

/** Production key handler/dialog and Android events; callback-only playback, no decoders/network. */
class ChannelSourceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var plays = 0
    private var saves = 0
    private var profile by mutableLongStateOf(7)
    private var choices by mutableStateOf(SwedishChoices(preferred = mapOf("tv4" to "101:2")))
    private fun row(id: Long, quality: String) = SwedishVariant(id, 101, "101:$id", "TV4 $quality",
        SwedishChannels.recognize("TV4 $quality")!!, null)
    private var rows by mutableStateOf(listOf(row(1, "HD"), row(2, "FHD"), row(3, "UHD")))
    private var failSave by mutableStateOf(false)
    @Before fun remote() { instrumentation.setInTouchMode(false) }
    private fun press(code: Int) { instrumentation.sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun send(action: Int, down: Long, repeat: Int = 0) {
        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_DPAD_CENTER, repeat))
    }
    private fun hold(): Long {
        val down = SystemClock.uptimeMillis()
        send(KeyEvent.ACTION_DOWN, down)
        Thread.sleep(650)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("source-picker").fetchSemanticsNodes().isNotEmpty() }
        return down
    }
    private fun mount() { compose.setContent { MaterialTheme {
        val origin = remember { FocusRequester() }
        var opened by remember(profile) { mutableStateOf(false) }
        var opening by remember(profile) { mutableStateOf<Long?>(null) }
        var restore by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<SourcePickerError?>(null) }
        Column(Modifier.fillMaxSize().background(MinTvNavy).padding(40.mpx)) {
            SmartAction("Synthetic TV4", { plays++ }, Modifier.testTag("origin").focusRequester(origin)
                .logicalChannelKeys({ plays++ }, { opening = it; opened = true; error = null }))
            SmartAction("Synthetic next", {}, Modifier.testTag("next"))
        }
        if (opened) ChannelSourcePicker(SourcePickerData(SwedishGroup("tv4", "TV4", rows), choices), opening, false, error,
            onChoose = { variant, play ->
                if (rows.none { it.stableKey == variant.stableKey }) error = SourcePickerError.UNAVAILABLE
                else if (failSave) error = SourcePickerError.SAVE_FAILED
                else { saves++; choices = choices.copy(preferred = choices.preferred + ("tv4" to variant.stableKey))
                    if (play) plays++; opened = false; restore = true }
            }, onDismiss = { opened = false; restore = true })
        LaunchedEffect(profile, restore) { if (!opened) { repeat(2) { withFrameNanos { } }; origin.requestSafely(); restore = false } }
    } } }
    @Test fun shortLongRepeatReleaseAndBackPreserveFocusAndDoNotAccidentallyPlay() {
        mount(); focus("origin"); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, plays)
        val held = hold()
        compose.onNodeWithTag("source-variant-2").assertIsFocused()
        for (repeat in 1..5) send(KeyEvent.ACTION_DOWN, held, repeat)
        send(KeyEvent.ACTION_UP, held); compose.waitForIdle()
        assertEquals(1, plays); assertEquals(0, saves)
        press(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("origin").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals("101:2", choices.preferred["tv4"])
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(2, plays)
    }
    @Test fun saveOnlyAndSavePlayReopenAtSavedVariantWithSinglePlayback() {
        mount(); focus("origin"); val down = hold(); send(KeyEvent.ACTION_UP, down); compose.waitForIdle()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("source-variant-3").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("source-save-only").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT); compose.onNodeWithTag("source-variant-3").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(0, plays); assertEquals(1, saves); assertEquals("101:3", choices.preferred["tv4"])
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("origin").assertIsFocused(); true }.getOrDefault(false) }
        val reopened = hold(); send(KeyEvent.ACTION_UP, reopened); compose.waitForIdle()
        compose.onNodeWithTag("source-variant-3").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("source-variant-2").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, plays); assertEquals(2, saves); assertEquals("101:2", choices.preferred["tv4"])
    }
    @Test fun missingReleaseDoesNotEatNextOkAndRemovedOptionHasSafeCloseFocus() {
        mount(); focus("origin"); hold() // Simulate a remote whose opening KeyUp is lost.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, plays); assertEquals(1, saves)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("origin").assertIsFocused(); true }.getOrDefault(false) }
        val held = hold(); send(KeyEvent.ACTION_UP, held); compose.waitForIdle()
        compose.runOnIdle { rows = listOf(row(1, "HD")) }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("source-close").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals("101:2", choices.preferred["tv4"])
        press(KeyEvent.KEYCODE_BACK); assertEquals(1, plays); assertEquals(1, saves)
    }
    @Test fun storageFailureKeepsDialogAndNeverReportsSavedOrStartsPlayback() {
        mount(); focus("origin"); compose.runOnIdle { failSave = true }
        val down = hold(); send(KeyEvent.ACTION_UP, down); compose.waitForIdle()
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("source-picker").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(tv.own.owntv.R.string.mintv_source_save_failed)).assertIsDisplayed()
        assertEquals("101:2", choices.preferred["tv4"]); assertEquals(0, saves); assertEquals(0, plays)
    }
    @Test fun navigationFocusAndLifecycleCancelPendingLongPressAndProfileClosesPicker() {
        mount(); focus("origin")
        val down = SystemClock.uptimeMillis(); send(KeyEvent.ACTION_DOWN, down)
        press(KeyEvent.KEYCODE_DPAD_DOWN); Thread.sleep(650); send(KeyEvent.ACTION_UP, down)
        compose.onNodeWithTag("source-picker").assertDoesNotExist(); assertEquals(0, plays)
        focus("origin"); val paused = SystemClock.uptimeMillis(); send(KeyEvent.ACTION_DOWN, paused)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED); Thread.sleep(650)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        send(KeyEvent.ACTION_UP, paused); compose.waitForIdle()
        compose.onNodeWithTag("source-picker").assertDoesNotExist(); assertEquals(0, plays)
        focus("origin"); val held = hold(); send(KeyEvent.ACTION_UP, held); compose.waitForIdle()
        compose.runOnIdle { profile = 8; choices = SwedishChoices() }
        compose.onNodeWithTag("source-picker").assertDoesNotExist(); assertEquals(0, saves)
    }
}
