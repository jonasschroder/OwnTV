package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.home.*

/** Production presentation with synthetic metadata; neither a player nor a network source. */
class HomePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteInputMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }

    @Test fun heroPreservesClockViewportAndOkUsesExistingWatchActionWithoutAutoplay() {
        val now = System.currentTimeMillis()
        val watch = AtomicInteger(); val live = AtomicInteger()
        var current by mutableStateOf(XtEpgEntry("Synthetic programme", "Description. ".repeat(100), now - 1000, now + 60_000))
        compose.setContent {
            MaterialTheme {
                val first = remember { FocusRequester() }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().homeContentViewport(100.dp).testTag("viewport"),
                        contentPadding = PaddingValues(start = 80.dp, end = 32.dp, top = 12.dp)) {
                        item {
                            HomeLiveHero("Synthetic channel", null, current, "Synthetic next programme", now, null,
                                { watch.incrementAndGet() }, { live.incrementAndGet() }, {}, {}, Modifier.testTag("hero"),
                                firstAction = Modifier.focusRequester(first).testTag("watch")) {
                                Box(Modifier.fillMaxSize().testTag("video-slot"))
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.TopEnd).width(300.dp).height(80.dp).testTag("clock"))
                }
                LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
            }
        }
        compose.onNodeWithTag("watch").assertIsFocused()
        compose.onNodeWithText("Synthetic programme").assertIsDisplayed()
        compose.onNodeWithText("Synthetic next programme", substring = true).assertIsDisplayed()
        assertEquals(0, watch.get()); assertEquals(0, live.get())
        val clock = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("hero").fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("video-slot").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.top >= clock.bottom)
        assertEquals(16f / 9f, video.width / video.height, 0.03f)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, watch.get()); assertEquals(0, live.get())
        compose.runOnIdle { current = current.copy(startMs = now - 60_000, stopMs = now - 1) }
        compose.onNodeWithText("Synthetic programme").assertDoesNotExist()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, live.get()); assertEquals(1, watch.get())
    }

    @Test fun failedSportsPanelIsCompactAndContainsNoHttpOrParserDetails() {
        val now = System.currentTimeMillis(); val retries = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().testTag("viewport")) {
                    SportsStatusPanel(SportsCatalog.shl, true, SportsLoadState(issue = HockeyIssue(
                        HockeyFailureKind.FORMAT, HockeyStage.SCHEDULE, now, httpCode = 200,
                        detail = HockeyFormatReason.COMPETITION)), now, now - 60_000, { retries.incrementAndGet() },
                        Modifier.testTag("sports-status"))
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_sports_fetch_failed)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_sports_retry)).assertIsDisplayed()
        listOf("HTTP", "200", "FORMAT", "COMPETITION").forEach {
            compose.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
        val panel = compose.onNodeWithTag("sports-status").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.height < viewport.height * 0.35f)
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_sports_retry)).performClick()
        assertEquals(1, retries.get())
    }
}
