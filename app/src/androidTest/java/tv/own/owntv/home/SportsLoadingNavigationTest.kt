package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*

/** Real remote events against production tabs/cards and the production cancellable fetch adapter.
 * No external HTTP, IPTV import, installed user app or release keys are involved. */
class SportsLoadingNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteInputMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    @Test fun tabsAndBackRemainUsableWhileFetchingAndReopeningCancelsOldWork() {
        val starts = AtomicInteger(); val cancels = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(false) }
                val home = remember { FocusRequester() }
                if (!open) {
                    HomeSportsCard(true, { open = true }, Modifier.focusRequester(home).testTag("sports-home")) { TvText("My Sports") }
                    LaunchedEffect(Unit) { withFrameNanos { }; home.requestSafely() }
                } else {
                    var section by remember { mutableStateOf(MatchcenterSection.MATCHES) }
                    var state by remember { mutableStateOf(SportsLoadState()) }
                    val first = remember { FocusRequester() }
                    BackHandler { open = false }
                    val fetchingVisible = sportsScheduleVisible(false, true, section, false, false)
                    LaunchedEffect(fetchingVisible) {
                        if (!fetchingVisible) return@LaunchedEffect
                        fetchSports<String>(System.currentTimeMillis(), HockeyStage.SCHEDULE, { false }, {
                            starts.incrementAndGet()
                            try { awaitCancellation() } finally { cancels.incrementAndGet() }
                        }, { error("Cancelled response published") }, { state = it }, timeoutMillis = 60_000)
                    }
                    LaunchedEffect(Unit) { withFrameNanos { }; first.requestSafely() }
                    Column {
                        MatchcenterTabs(section, first) { section = it }
                        TvText(section.name, Modifier.testTag("selected-tab"))
                        TvText(if (state.loading) "Fetching" else "Finished", Modifier.testTag("fetch-state"))
                        SportsStatusPanel(SportsCatalog.shl, true, state, System.currentTimeMillis(), null, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("sports-home").assertIsFocused()
        repeat(2) { visit ->
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5000) { starts.get() == visit + 1 }
            compose.onNodeWithTag("sports-tab-MATCHES").assertIsFocused()
            compose.onAllNodesWithTag("sports-tab-TABLE").assertCountEquals(0)
            repeat(3) {
                listOf(MatchcenterSection.FARJESTAD).forEach { tab ->
                    key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.onNodeWithTag("sports-tab-${tab.name}").assertIsFocused()
                    compose.onNodeWithTag("selected-tab").assertTextEquals(tab.name)
                    compose.onNodeWithTag("fetch-state").assertTextEquals("Fetching")
                }
                repeat(1) { key(KeyEvent.KEYCODE_DPAD_LEFT) }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                compose.onNodeWithTag("selected-tab").assertTextEquals("MATCHES")
            }
            assertEquals(visit + 1, starts.get()) // Tabs do not restart the same request.
            repeat(2) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(5000) { cancels.get() == visit + 1 }
            compose.onNodeWithTag("fetch-state").assertTextEquals("Finished")
            compose.onNodeWithTag("sports-tab-SETTINGS").assertIsFocused()
            key(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5000) { cancels.get() == visit + 1 }
            compose.onNodeWithTag("sports-home").assertIsFocused()
        }
        assertEquals(2, starts.get()); assertEquals(2, cancels.get())
        assertFalse(FocusRequester().requestSafely()) // A detached target never exits the app.
    }

    @Test fun slowRequestEndsInErrorAndLeavesTabsFocusable() {
        compose.setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(SportsLoadState()) }
                var section by remember { mutableStateOf(MatchcenterSection.MATCHES) }
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    fetchSports<String>(System.currentTimeMillis(), HockeyStage.SCHEDULE, { false }, { awaitCancellation() },
                        { error("Timed out response published") }, { state = it }, timeoutMillis = 250)
                }
                LaunchedEffect(Unit) { withFrameNanos { }; first.requestSafely() }
                Column {
                    MatchcenterTabs(section, first) { section = it }
                    TvText(if (state.loading) "Fetching" else state.issue?.kind?.name ?: "Idle", Modifier.testTag("fetch-state"))
                    SportsStatusPanel(SportsCatalog.shl, true, state, System.currentTimeMillis(), null, {})
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("fetch-state").fetchSemanticsNodes().any {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { text -> text.text == "TIMEOUT" }
        } }
        compose.onNodeWithTag("fetch-state").assertTextEquals("TIMEOUT")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("sports-tab-FARJESTAD").assertIsFocused()
    }
}
