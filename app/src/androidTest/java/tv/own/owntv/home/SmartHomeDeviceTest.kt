package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.*
import tv.own.owntv.features.shell.*
import tv.own.owntv.ui.theme.mpx

/** Production native composition, explicit synthetic local data. No provider or player. */
class SmartHomeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_800_000_000_000L
    private val names = listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6", "Sjuan", "TV8", "Kanal 9", "TV10")
    private val channels = names.mapIndexed { i, label -> SmartChannel(label, label,
        ChannelEntity(id = i + 1L, sourceId = 101, name = label, streamUrl = "http://127.0.0.1:9/synthetic/$i")) }
    private var profile by mutableLongStateOf(7)
    private var snapshot by mutableStateOf<SmartHomeSnapshot?>(SmartHomeSnapshot(7, true, channels, channels[0].channel, now - 1, null, emptyList()))
    private var guide by mutableStateOf(HomeGuideState(now, channels.associate { it.channel.homeEpgKey() to
        SwedishRowInfo(null, SwedishProgramme(it.channel.id, "Synthetic current", now - 1000, now + 1000), null) }))
    private var played = 0
    private var entered: (() -> Boolean)? = null
    @Before fun remote() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun press(code: Int) {
        compose.waitForIdle(); InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle()
    }
    private fun mount() { compose.setContent {
        MaterialTheme {
            val header = remember(profile) { FocusRequester() }
            val first = remember(profile) { FocusRequester() }
            val manager = LocalFocusManager.current
            Column {
                NativeTopNavigation(TopDestination.HOME, header, {}, { entered?.invoke() }, {})
                key(profile) {
                    SmartHomeContent(snapshot, guide, profile, { played++ }, { played++ }, {}, {}, {}, {},
                        first, { entered = it }, {}, false, {}, 64.mpx, 0.mpx,
                        Modifier.weight(1f).nativeContentNavigation(header, manager), onHeader = { header.requestFocus() })
                }
            }
            LaunchedEffect(profile) { withFrameNanos { }; header.requestFocus() }
        }
    } }
    @Test fun eightLogicalChannelsFitAndRightScrollsToAdditionalChannel() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-channel-SVT1").assertIsFocused()
        for (name in names.take(8)) compose.onNodeWithTag("smart-channel-$name").assertIsDisplayed()
        repeat(8) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.onNodeWithTag("smart-channel-Kanal 9").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, played) }
    }
    @Test fun programmeRefreshAndMidnightDoNotMoveChannelFocusOrCreateHistory() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("smart-channel-TV3").assertIsFocused()
        compose.runOnIdle { guide = HomeGuideState(now + 86_400_000, guide.rows.mapValues { (_, row) ->
            row.copy(now = SwedishProgramme(44, "Synthetic next day", now + 86_399_000, now + 86_401_000)) }) }
        compose.onNodeWithTag("smart-channel-TV3").assertIsFocused()
        compose.runOnIdle { assertEquals(0, played) }
    }
    @Test fun headerRoundTripRestoresLogicalChannel() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(3) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        compose.onNodeWithTag("smart-channel-TV4").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("top-HOME").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-channel-TV4").assertIsFocused()
        compose.onNodeWithTag("top-HOME").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.runOnIdle { snapshot = snapshot!!.copy(channels = channels.filterNot { it.key == "TV4" }) }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-channel-Kanal 5").assertIsFocused()
    }
    @Test fun emptyMissingGuideAndProfileSwitchRemainNavigable() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.runOnIdle { guide = HomeGuideState(now, emptyMap()) }
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(1, played); profile = 8; snapshot = SmartHomeSnapshot(8, false, emptyList(), null, null, null, emptyList()) }
        compose.onNodeWithTag("smart-channel-SVT1").assertDoesNotExist()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("top-HOME").assertIsFocused()
    }
    @Test fun removedRowsFallbackToHeroWithoutFocusTrap() {
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-channel-SVT1").assertIsFocused()
        compose.runOnIdle { snapshot = snapshot!!.copy(channels = emptyList(), live = null, liveWatchedAt = null) }
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("top-HOME").assertIsFocused()
    }
    @Test fun promotedSeriesKeepsLastLiveAndRemovesOnlyExactHeroFromResumeRow() {
        val episode = SmartResume("episode", SmartResumeKind.EPISODE, 3, 2, "Synthetic episode", "Synthetic series", 1, 2,
            playedAt = now, position = 20, duration = 100)
        snapshot = snapshot!!.copy(heroResume = episode, resumes = listOf(episode.copy(key = "movie", kind = SmartResumeKind.MOVIE)))
        mount(); press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        compose.onNodeWithTag("smart-resume-episode").assertDoesNotExist()
        compose.onNodeWithTag("smart-resume-movie").assertExists()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(1, played) }
    }
}
