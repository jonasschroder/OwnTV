package tv.own.owntv.home

import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.core.launcher.*
import tv.own.owntv.features.home.*
import tv.own.owntv.features.shell.components.MediaDetailsScreen
import tv.own.owntv.features.shell.components.MediaDetailsUi

/** Disposable emulator content only. No provider network/decoder/auth or real profile data. */
class VodPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use {
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun providerOnlyDetailsOfferPlayAndBackWithoutMetadataOrAutoplay() {
        var plays = 0
        var closed = false
        compose.setContent {
            MaterialTheme {
                MediaDetailsScreen(MediaDetailsUi("Instrumented provider title", plot = "Provider synopsis. ".repeat(150)),
                    onExit = { closed = true }, onPlay = { plays++ }, playLabel = "Play fixture")
            }
        }
        compose.waitUntil(5000) { compose.onAllNodes(hasText("Play fixture") and isFocused()).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Play fixture").assertIsFocused()
        assertEquals(0, plays)
        screenshot("provider-details-test-fixture.png")
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithText("Play fixture").assertIsFocused().assertIsDisplayed()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, plays)
        key(KeyEvent.KEYCODE_BACK)
        assertTrue(closed)
    }
    @Test fun continuationUsesCorrectIdentityPositionAndDoesNotAutoplayOnFocus() {
        val item = LauncherContinuationItem(LauncherContinuationKind.MOVIE, 42, 42, "movie:42",
            "Instrumented resume title", null, null, Uri.EMPTY, 10, 100_000, 50_000, LauncherWatchNextType.CONTINUE)
        var played: LauncherContinuationItem? = null
        compose.setContent {
            MaterialTheme {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { withFrameNanos { }; first.requestSafely() }
                Box(Modifier.fillMaxSize().background(MinTvNavy)) {
                    HomeContinuationRow(listOf(item), Modifier.focusRequester(first)) { played = it }
                }
            }
        }
        compose.onNodeWithText(item.title).assertExists()
        assertNull(played)
        screenshot("continuation-test-fixture.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertSame(item, played)
        assertEquals(42L, played!!.sourceItemId)
        assertEquals(50_000L, played!!.positionMs)
        assertTrue(homeContinuations(HomeUiState(profileId = 1, continueMovies = listOf(item)), 2).isEmpty())
        assertEquals(listOf(item), homeContinuations(HomeUiState(profileId = 1, continueMovies = listOf(item)), 1))
    }

    @Test fun insertedResumeKeepsFocusedAppsAndDpadCanReachNewSection() {
        var insert: (() -> Unit)? = null
        compose.setContent {
            MaterialTheme {
                var history by remember { mutableStateOf(false) }
                val first = remember { FocusRequester() }
                val navigation = rememberHomeDpadNavigation(first, sectionCount = 6)
                val list = rememberLazyListState()
                SideEffect { insert = { history = true } }
                LaunchedEffect(Unit) { withFrameNanos { }; navigation.focus[5].requestSafely() }
                LazyColumn(state = list, modifier = homeDpadModifier(navigation, list,
                    listOf(0, 1, 2, if (history) 3 else -1, if (history) 4 else 3, if (history) 5 else 4))) {
                    item(key = "hero") { HomeButton("Hero", {}, navigation.section(0)) }
                    item(key = "favorites") { HomeButton("Favorites", {}, navigation.section(1)) }
                    item(key = "sports") { HomeSportsCard(true, {}, navigation.section(2)) }
                    if (history) item(key = "resume") { HomeButton("Resume", {}, navigation.section(3).testTag("new-resume")) }
                    item(key = "twitch") { HomeButton("Twitch", {}, navigation.section(4)) }
                    item(key = "apps") { HomeButton("Apps", {}, navigation.section(5).testTag("stable-apps")) }
                }
            }
        }
        compose.onNodeWithTag("stable-apps").assertIsFocused()
        compose.runOnIdle { checkNotNull(insert).invoke() }
        compose.onNodeWithTag("stable-apps").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("new-resume").assertIsFocused()
    }
}
