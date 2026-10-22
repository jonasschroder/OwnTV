package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.features.home.HomeButton
import tv.own.owntv.features.shell.*
import tv.own.owntv.ui.theme.mpx

/** Production header and content key policy, synthetic rows. No provider, decoder or account. */
class NativeNavigationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var profile by mutableLongStateOf(7)
    private var available by mutableStateOf(true)
    private var fullscreen by mutableStateOf(false)
    private var active by mutableStateOf(TopDestination.HOME)
    private val enteredPages = mutableListOf<TopDestination>()
    private var activations = 0
    private var timelineMoves = 0
    private var enter: (() -> Boolean)? = null

    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun press(key: Int) {
        // Apply profile/page state mutations before dispatching the next physical-style input.
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(key)
        compose.waitForIdle()
    }
    private fun mount() {
        compose.setContent {
            MaterialTheme {
                val header = remember { FocusRequester() }
                val holder = rememberSaveableStateHolder()
                val manager = LocalFocusManager.current
                var inHeader by remember { mutableStateOf(true) }
                BackHandler {
                    if (fullscreen) fullscreen = false
                    else when (nativeBack(inHeader, active)) {
                        NativeBack.HEADER -> header.requestFocus()
                        NativeBack.HOME -> { active = TopDestination.HOME; header.requestFocus() }
                        NativeBack.EXIT -> Unit
                    }
                }
                if (!fullscreen) Column {
                    var entryRequest by remember(profile) { mutableIntStateOf(0) }
                    NativeContentEntry(entryRequest, "$profile:$active", { enter?.invoke() == true }, { header.requestFocus() }, { entryRequest = 0 })
                    NativeTopNavigation(active, header, { entryRequest = 0; active = it; activations++ }, {
                        active = it; activations++; entryRequest++
                    }, { inHeader = true })
                    val section = nativeEntry(active, MainSection.entries.toSet(), MainSection.MOVIES)!!
                    holder.SaveableStateProvider(nativePageKey(profile, section, active)) {
                        val rows = rememberLazyListState()
                        var last by rememberSaveable { mutableIntStateOf(0) }
                        val targets = remember(profile, active) { (0..30).associateWith { FocusRequester() } }
                        DisposableEffect(profile, active, available) {
                            enter = { enteredPages.add(active); if (available) runCatching { targets.getValue(last).requestFocus() }.getOrDefault(false) else false }
                            onDispose { enter = null }
                        }
                        LazyColumn(state = rows, modifier = Modifier.fillMaxSize().nativeContentNavigation(header, manager)
                            .onFocusChanged { if (it.hasFocus) inHeader = false }.focusGroup()) {
                            if (available) items((0..30).toList(), key = { it }) { index ->
                                HomeButton("Synthetic row $index", { fullscreen = true },
                                    Modifier.fillMaxWidth().height(64.mpx).focusRequester(targets.getValue(index))
                                        .testTag("native-row-$index").onFocusChanged { if (it.isFocused) last = index }
                                        .onKeyEvent { event ->
                                            if (active == TopDestination.TV && event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                                                if (event.type == KeyEventType.KeyDown) timelineMoves++
                                                true
                                            } else false
                                        })
                            }
                        }
                    }
                    LaunchedEffect(profile) { withFrameNanos { }; header.requestFocus() }
                } else HomeButton("Synthetic player", { fullscreen = false }, Modifier.testTag("native-player"))
            }
        }
    }

    @Test fun downActivatesFocusedDestinationWithoutEnteringOutgoingContent() {
        mount()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnIdle { assertEquals(TopDestination.HOME, active); assertEquals(0, activations) }
        compose.onNodeWithTag("top-SPORT").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.runOnIdle { assertEquals(TopDestination.SPORT, active); assertEquals(1, activations); assertTrue(enteredPages.isNotEmpty()); assertTrue(enteredPages.all { it == TopDestination.SPORT }) }
        compose.onNodeWithTag("native-row-0").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("top-SPORT").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.runOnIdle { assertEquals(TopDestination.TV, active) }
        press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-1").assertIsFocused()
    }

    @Test fun downToUnavailableDestinationKeepsNavigationUsableAndCanRetryAfterLoading() {
        mount()
        compose.runOnIdle { available = false }
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.runOnIdle { assertEquals(TopDestination.TV, active) }
        compose.waitUntil(5000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { available = true }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-0").assertIsFocused()
    }
    @Test fun fullLabelsFitAndFocusDoesNotResizeHeader() {
        mount()
        for (destination in TopDestination.entries) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("top-${destination.name}").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Header exposes actual text layout", layouts.isNotEmpty())
            assertTrue("Full primary label must fit", layouts.none { it.hasVisualOverflow })
        }
        val before = compose.onNodeWithTag("top-TV").fetchSemanticsNode().boundsInRoot
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(before, compose.onNodeWithTag("top-TV").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun focusDoesNotActivateAndAllFiveDestinationsUseOk() {
        mount()
        for (destination in TopDestination.entries) {
            compose.onNodeWithTag("top-${destination.name}").assertIsFocused()
            compose.runOnIdle { assertEquals(if (destination == TopDestination.HOME) 0 else destination.ordinal, activations) }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.runOnIdle { assertEquals(destination, active) }
            if (destination != TopDestination.PROFILE) press(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        compose.runOnIdle { assertEquals(5, activations) }
    }
    @Test fun downRestoresLastRowAcrossPageVisitsAndUpTargetsActiveHeader() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-0").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("top-HOME").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-1").assertIsFocused()
        press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_DPAD_CENTER); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-1").assertIsFocused()
    }
    @Test fun scrollingReentryPreservesSavedRowAndProfileDoesNotReuseIt() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(15) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithTag("native-row-15").assertIsFocused()
        press(KeyEvent.KEYCODE_BACK); press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_DPAD_CENTER); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-15").assertIsFocused()
        compose.runOnIdle { profile = 9 }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-0").assertIsFocused()
    }

    @Test fun guideHorizontalInputStaysLocalAndFullscreenHidesHeader() {
        mount(); press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_CENTER); press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.runOnIdle { assertEquals(2, timelineMoves); assertEquals(TopDestination.TV, active) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("top-TV").assertDoesNotExist()
        compose.onNodeWithTag("native-player").assertExists()
        press(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("top-TV").assertExists()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-0").assertIsFocused()
    }
    @Test fun profileSwitchInvalidatesBookmarkAndEmptyDataKeepsHeaderReachable() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-1").assertIsFocused()
        compose.runOnIdle { profile = 8 }
        compose.onNodeWithTag("top-HOME").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("native-row-0").assertIsFocused()
        press(KeyEvent.KEYCODE_BACK)
        compose.runOnIdle { available = false }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("top-HOME").assertIsFocused()
    }
}
