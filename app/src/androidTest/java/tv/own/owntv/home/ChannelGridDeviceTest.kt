package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.live.*
import tv.own.owntv.features.live.source.*
import tv.own.owntv.features.home.requestSafely
import tv.own.owntv.ui.theme.mpx

/** Synthetic presentation fixture using production cards, movement policy and OK handler. No IPTV. */
class ChannelGridDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var plays = 0
    private var alternatives = 0
    @Before fun remote() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun press(code: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
        instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        compose.waitForIdle()
    }
    private fun mount() { compose.setContent {
        MaterialTheme {
            val scope = rememberCoroutineScope()
            val grid = rememberLazyGridState()
            val targets = remember { List(12) { FocusRequester() } }
            val cancellation = remember { SourcePressCancellation() }
            var column by remember { mutableIntStateOf(0) }
            var pending by remember { mutableIntStateOf(-1) }
            LazyVerticalGrid(GridCells.Fixed(CHANNEL_GRID_COLUMNS), state = grid,
                modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.mpx), verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                items(12, key = { it }) { index ->
                    SwedishChannelCard("Synthetic $index", ChannelQuality.HD, null, 0, { plays++ },
                        Modifier.testTag("grid-$index").focusRequester(targets[index])
                            .channelGridNavigation(index, 12, { column }, { next, direction ->
                                if (direction == ChannelGridDirection.LEFT || direction == ChannelGridDirection.RIGHT) column = next % CHANNEL_GRID_COLUMNS
                                pending = next
                                scope.launch {
                                    val visible = grid.layoutInfo.visibleItemsInfo.any { it.index == next }
                                    if (!visible) grid.scrollToItem(next)
                                    repeat(2) { withFrameNanos { } }
                                    targets[next].requestSafely()
                                }
                            }, {}, { cancellation.cancel() })
                            .logicalChannelKeys({ plays++ }, { alternatives++ }, cancellation)
                            .onFocusChanged { if (it.isFocused && pending != index) column = index % CHANNEL_GRID_COLUMNS })
                }
            }
            LaunchedEffect(Unit) { repeat(2) { withFrameNanos { } }; targets.first().requestSafely() }
        }
    } }
    @Test fun verticalMovesKeepColumnAcrossPartialRowAndHorizontalNeverWraps() {
        mount()
        repeat(4) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.onNodeWithTag("grid-4").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("grid-4").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("grid-9").assertIsFocused().assertIsDisplayed()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("grid-11").assertIsFocused().assertIsDisplayed()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("grid-9").assertIsFocused().assertIsDisplayed()
        assertEquals(0, plays)
    }
    @Test fun shortOkPlaysAndHeldOkOnlyOpensExistingSourceAction() {
        mount(); compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("grid-0").assertIsFocused(); true }.getOrDefault(false) }; press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, plays)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        Thread.sleep(650)
        instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        compose.waitForIdle()
        assertEquals(1, plays); assertEquals(1, alternatives)
    }
}
