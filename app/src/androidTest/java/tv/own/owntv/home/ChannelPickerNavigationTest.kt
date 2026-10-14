package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*

/** Production TV cards and lazy-focus restoration, synthetic local rows, real remote input. */
class ChannelPickerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteInput() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    @Test fun offscreenChannelRetainsSelectionAfterPlayerBackWithoutAutoplay() {
        val plays = mutableListOf<Int>()
        compose.setContent { MaterialTheme {
            val list = rememberLazyListState()
            val target = remember { FocusRequester() }
            val back = remember { FocusRequester() }
            var player by remember { mutableStateOf(false) }
            var selected by remember { mutableIntStateOf(37) }
            if (player) {
                BackHandler { player = false }
                TvText("Synthetic player", Modifier.testTag("picker-player"))
            } else {
                Column {
                    HomeButton("Back", {}, Modifier.focusRequester(back))
                    LazyColumn(state = list, modifier = Modifier.height(260.dp)) {
                        itemsIndexed((0 until 48).toList(), key = { _, id -> id }) { _, id ->
                            ChannelCard("Synthetic channel $id", null, null, {
                                selected = id; plays.add(id); player = true
                            }, Modifier.testTag("picker-channel-$id")
                                .then(if (id == selected) Modifier.focusRequester(target) else Modifier))
                        }
                    }
                }
                LaunchedEffect(player) { focusPickerItem(list, selected, target, back) }
            }
        } }
        compose.onNodeWithTag("picker-channel-37").assertIsDisplayed().assertIsFocused()
        assertTrue(plays.isEmpty())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("picker-player").assertIsDisplayed()
        assertEquals(listOf(37), plays)
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("picker-channel-37").assertIsDisplayed().assertIsFocused()
        assertEquals(listOf(37), plays)
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("picker-channel-36").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(37, 36), plays)
    }

    @Test fun delayedPageFocusesFirstChannelAndEmptyPageKeepsRemoteRecovery() {
        var publish: ((List<Int>) -> Unit)? = null
        var recoveries = 0
        compose.setContent { MaterialTheme {
            var rows by remember { mutableStateOf<List<Int>?>(null) }
            val list = rememberLazyListState()
            val target = remember { FocusRequester() }
            val back = remember { FocusRequester() }
            SideEffect { publish = { rows = it } }
            LaunchedEffect(rows) {
                if (rows == null) back.requestSafely()
                else focusPickerItem(list, 0, target, back)
            }
            Column {
                HomeButton("Back", { recoveries++ }, Modifier.focusRequester(back).testTag("picker-back"))
                LazyColumn(state = list, modifier = Modifier.height(260.dp)) {
                    itemsIndexed(rows.orEmpty(), key = { _, id -> id }) { index, id ->
                        ChannelCard("Synthetic channel $id", null, null, {}, Modifier.testTag("picker-channel-$id")
                            .then(if (index == 0) Modifier.focusRequester(target) else Modifier))
                    }
                }
            }
        } }
        compose.onNodeWithTag("picker-back").assertIsFocused()
        compose.runOnIdle { checkNotNull(publish).invoke((48 until 96).toList()) }
        compose.onNodeWithTag("picker-channel-48").assertIsDisplayed().assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("picker-channel-49").assertIsFocused()
        compose.runOnIdle { checkNotNull(publish).invoke(emptyList()) }
        compose.onNodeWithTag("picker-back").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, recoveries)
    }
}
