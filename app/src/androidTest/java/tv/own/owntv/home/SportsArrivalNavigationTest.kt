package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*

/** HTTP-free arrival fixture through production fetch, tabs, Home card and fixture cards. */
class SportsArrivalNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteInput() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    @Test fun verifiedArrivalDoesNotStealTabFocusAndRemoteCanOpenFixture() {
        val response = CompletableDeferred<List<SportFixture>>()
        val now = 1_791_720_000_000L
        val fixture = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("arrival", "Färjestad BK", "Luleå HF", now + 60_000, null))
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                var section by remember { mutableStateOf(MatchcenterSection.MATCHES) }
                var fixtures by remember { mutableStateOf(emptyList<SportFixture>()) }
                var load by remember { mutableStateOf(SportsLoadState()) }
                val first = remember { FocusRequester() }
                val card = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    fetchSports(now, HockeyStage.SCHEDULE, { false }, { response.await() },
                        { fixtures = it; false }, { load = it })
                }
                LaunchedEffect(Unit) { withFrameNanos { }; first.requestSafely() }
                Column {
                    MatchcenterTabs(section, first) { section = it }
                    TvText(if (load.loading) "Loading" else "Verified", Modifier.testTag("arrival-state"))
                    LazyColumn {
                        items(fixtures, key = { it.id }) { game ->
                            MatchScorecard(game, now, false, true, null, { opened = game.id }, null,
                                Modifier.focusRequester(card).testTag("arrival-fixture"))
                        }
                    }
                }
            }
        }
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("sports-tab-TABLE").assertIsFocused()
        compose.runOnIdle { response.complete(listOf(fixture)) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("arrival-fixture").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("sports-tab-TABLE").assertIsFocused()
        compose.onNodeWithTag("arrival-state").assertTextEquals("Verified")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("arrival-fixture"))).assertExists()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(fixture.id, opened) }
    }

    @Test fun homeCardCanChangeFromLoadingToVerifiedWhileOtherActionKeepsFocus() {
        var publish: (() -> Unit)? = null
        compose.setContent {
            MaterialTheme {
                var ready by remember { mutableStateOf(false) }
                val settings = remember { FocusRequester() }
                SideEffect { publish = { ready = true } }
                LaunchedEffect(Unit) { withFrameNanos { }; settings.requestSafely() }
                Column {
                    HomeSportsCard(true, {}, Modifier.testTag("arrival-home")) {
                        TvText(if (ready) "Färjestad – Luleå" else "Loading")
                    }
                    HomeButton("Settings", {}, Modifier.focusRequester(settings).testTag("arrival-settings"))
                }
            }
        }
        compose.onNodeWithTag("arrival-settings").assertIsFocused()
        compose.runOnIdle { checkNotNull(publish).invoke() }
        compose.onNodeWithTag("arrival-settings").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("arrival-home").assertIsFocused()
    }
}
