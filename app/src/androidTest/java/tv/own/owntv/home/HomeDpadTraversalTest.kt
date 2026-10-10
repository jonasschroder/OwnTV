package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*

/** Real Android key dispatch and production card/navigation in a scrolling Compose lazy list. */
class HomeDpadTraversalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun noTeamsChooseTeamsAndBack() = traverse(false)
    @Test fun multipleTeamsMatchcenterAndBack() = traverse(true)

    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun traverse(hasTeams: Boolean) {
        compose.setContent {
            MaterialTheme {
                val favorite = remember { FocusRequester() }
                val nav = rememberHomeDpadNavigation(favorite)
                val list = rememberLazyListState()
                var destination by remember { mutableStateOf(false) }
                var restore by remember { mutableStateOf(false) }
                if (destination) {
                    BackHandler { destination = false; restore = true }
                    HomeButton(if (hasTeams) "Matchcenter" else "Teams", {}, Modifier.testTag("destination"))
                } else {
                    LaunchedEffect(restore) {
                        if (restore) { list.scrollToItem(2); withFrameNanos { }; withFrameNanos { }; nav.focus[2].requestFocus(); restore = false }
                        else if (nav.selected == 1) { list.scrollToItem(1); withFrameNanos { }; favorite.requestFocus() }
                    }
                    LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(220.dp).then(homeDpadModifier(nav, list, listOf(0, 1, 2, 3, 4))),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { HomeButton("Live TV", {}, nav.section(0).testTag("top")) }
                        item { HomeButton("IPTV", {}, nav.section(1).testTag("iptv")) }
                        item { HomeSportsCard(hasTeams, { destination = true }, nav.section(2).testTag("sport")) { TvText("Next fixture") } }
                        item { HomeButton("Twitch", {}, nav.section(3).testTag("twitch")) }
                        item { HomeButton("Applications", {}, nav.section(4).testTag("apps")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("iptv").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("sport").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("destination").assertExists()
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("sport").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("twitch").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("apps").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("twitch").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("sport").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("iptv").assertIsFocused()
    }
}
