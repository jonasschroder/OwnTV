package tv.own.owntv.home

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.Locale
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.*
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.ui.theme.mpx

/** Actual row/frame/selected pane, synthetic EPG only. Idle existing singleton, no video/network.
 * Playback is a callback assertion, not decoder/fullscreen-player verification on hardware. */
class SwedishLivePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var plays = 0
    private var selected = 0
    private var chosen = ChannelQuality.HD
    private val now = System.currentTimeMillis()
    private val names = listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6", "Sjuan", "TV8", "Kanal 9", "TV10", "Kanal 11", "TV12")
    private val titles = listOf("Morgonstudio", "Vetenskapens värld", "Svensk dramaserie", "Nyheterna", "Dokumentär", "Sportmagasinet", "Matlagning", "Resor", "Historia", "Underhållning", "Svensk film", "Kvällsnyheter")
    private fun name(index: Int) = names.getOrElse(index) { "Syntetisk kanal $index" }
    private fun row(index: Int) = if (index == 6) SwedishRowInfo(null, null, null) else SwedishRowInfo(null,
        SwedishProgramme(index * 2L + 1, titles.getOrElse(index) { "Syntetiskt program $index" }, now - 1_800_000, now + 1_800_000),
        SwedishProgramme(index * 2L + 2, "Nästa program $index", now + 1_800_000, now + 3_600_000))
    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun screenshot(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun content(count: Int = 12) {
        val configuration = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("sv")) }
        val swedish = compose.activity.createConfigurationContext(configuration)
        val engine = GlobalContext.get().get<LivePreviewEngine>() // Existing DI singleton, never tuned.
        compose.setContent {
            CompositionLocalProvider(LocalContext provides swedish, LocalConfiguration provides configuration,
                LocalDensity provides Density(1.8f, 1f)) {
                androidx.tv.material3.MaterialTheme {
                    val scope = rememberCoroutineScope()
                    val list = rememberLazyListState()
                    val entry = remember { FocusRequester() }
                    val targets = remember { mutableMapOf<String, FocusRequester>() }
                    val schedule = remember { FocusRequester() }
                    var focused by remember { mutableIntStateOf(0) }
                    var fullscreen by remember { mutableStateOf(false) }
                    var variants by remember { mutableStateOf(false) }
                    var quality by remember { mutableStateOf(ChannelQuality.HD) }
                    SideEffect { selected = focused; chosen = quality }
                    suspend fun restore() = restoreSwedishChannel(list, (0 until count).map { it.toString() }, focused.toString(), 0, targets, entry)
                    BackHandler(fullscreen || variants) {
                        fullscreen = false; variants = false; scope.launch { restore() }
                    }
                    if (fullscreen) {
                        Box(Modifier.fillMaxSize().background(MinTvNavy).testTag("fullscreen-callback")) { TvText("Syntetisk uppspelningskontroll") }
                    } else SwedishLiveFrame(Modifier.background(MinTvNavy), header = {
                        TvText(swedish.getString(R.string.mintv_swedish_channels), Modifier.weight(1f), size = 34, bold = true)
                        SwedishLiveAction(swedish.getString(R.string.mintv_full_library), {}, Modifier.focusRequester(entry))
                        SwedishLiveAction(swedish.getString(R.string.mintv_collection_settings), {})
                        SwedishLiveAction(swedish.getString(R.string.common_nav_guide), {})
                    }, categories = {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                            items(listOf(R.string.mintv_swedish_channels, R.string.mintv_hockey_sport, R.string.mintv_news, R.string.mintv_children)) {
                                SwedishLiveAction(swedish.getString(it), {}, selected = it == R.string.mintv_swedish_channels)
                            }
                        }
                    }, channels = {
                        LazyColumn(Modifier.fillMaxSize().testTag("swedish-list"), state = list, verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                            items(count, key = { it.toString() }) { index ->
                                val requester = remember { targets.getOrPut(index.toString()) { FocusRequester() } }
                                SwedishChannelRow(name(index), if (index == focused) quality else ChannelQuality.HD, row(index), now, focused == index,
                                    { plays++; fullscreen = true }, Modifier.testTag("row-$index").focusRequester(requester).onFocusChanged { if (it.isFocused) focused = index })
                                if (variants && focused == index) for (alternative in listOf(ChannelQuality.HD, ChannelQuality.SD, ChannelQuality.FHD, ChannelQuality.UHD)) {
                                    SwedishLiveAction("${name(index)} ${alternative.name}", {
                                        quality = alternative; variants = false; scope.launch { restore() }
                                    }, Modifier.testTag("quality-${alternative.name}"))
                                }
                            }
                        }
                    }, preview = {
                        SwedishLiveAction(swedish.getString(R.string.mintv_choose_variant), { variants = !variants }, Modifier.testTag("variants"))
                        val info = row(focused)
                        fun entry(p: SwedishProgramme) = XtEpgEntry(p.title, "Syntetisk programinformation för layoutgranskning.", p.startMs, p.stopMs)
                        LiveStagePane(ChannelEntity(id = focused + 1L, sourceId = 101, name = name(focused), streamUrl = "https://example.invalid/synthetic"),
                            name(focused), EpgNowNext(info.now?.let(::entry), info.next?.let(::entry)), engine, false, false, schedule,
                            {}, { scope.launch { restore() } }, Modifier.fillMaxWidth())
                    })
                    LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; targets["0"]?.requestSafely() }
                }
            }
        }
    }

    @Test fun compactRowsShowRealSuppliedNowNextMissingStateAndExistingSelectedPane() {
        content()
        compose.onNodeWithTag("row-0").assertIsFocused()
        val viewport = compose.onNodeWithTag("swedish-list").fetchSemanticsNode().boundsInRoot
        val visible = (0 until 12).count { index -> runCatching {
            val bounds = compose.onNodeWithTag("row-$index").fetchSemanticsNode().boundsInRoot
            bounds.top >= viewport.top && bounds.bottom <= viewport.bottom && bounds.height > 0
        }.getOrDefault(false) }
        assertTrue("Fully visible channels=$visible", visible in 7..9)
        compose.onNodeWithText("Ingen programinformation").assertIsDisplayed()
        compose.onNodeWithText("Nästa: Nästa program 1").assertIsDisplayed()
        screenshot("pr10-swedish-live-now-next-synthetic.png")
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithTag("row-3").assertIsFocused(); assertEquals(3, selected); assertEquals(0, plays)
        compose.onAllNodesWithText("Nyheterna").assertCountEquals(2) // Row and full selected-pane description.
        screenshot("pr10-swedish-live-selected-tv4-synthetic.png")
        focus("row-6"); compose.onNodeWithText("Ingen programinformation").assertIsDisplayed()
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, plays) // No EPG does not block OK.
        key(KeyEvent.KEYCODE_BACK); compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("row-6").assertIsFocused(); true }.getOrDefault(false) }
    }

    @Test fun remoteQualitySelectionKeepsManualUhdAndReturnsFocusWithoutPlayback() {
        content(); focus("variants"); key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("quality-UHD").performScrollTo(); focus("quality-UHD")
        screenshot("pr10-swedish-live-quality-picker-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("row-0").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals(ChannelQuality.UHD, chosen); assertEquals(0, plays)
        compose.onNodeWithTag("row-0").assert(hasText("UHD"))
    }

    @Test fun hundredsOfLazyRowsPreserveOffscreenChannelAfterOkBackAndRapidDpad() {
        content(500)
        compose.onNodeWithTag("swedish-list").performScrollToIndex(120); focus("row-120")
        repeat(20) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        assertEquals(140, selected); compose.onNodeWithTag("row-140").assertIsFocused(); assertEquals(0, plays)
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, plays)
        compose.onNodeWithTag("fullscreen-callback").assertIsDisplayed()
        key(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("row-140").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals(140, selected); assertEquals(1, plays)
        key(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("row-141").assertIsFocused()
    }
}
