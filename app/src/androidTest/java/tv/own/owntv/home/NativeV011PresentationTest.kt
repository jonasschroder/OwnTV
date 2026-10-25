package tv.own.owntv.home

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import java.io.File
import java.util.Locale
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.*
import tv.own.owntv.features.shell.*
import tv.own.owntv.ui.theme.mpx

/** Actual production Compose screens, deterministic synthetic data, disposable emulator only. */
class NativeV011PresentationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true" && shell("getprop ro.kernel.qemu").trim() == "1")
                val small = description.methodName.contains("720")
                density = if (small) 1.2f else 1.8f
                shell(if (small) "wm size 1280x720" else "wm size 1920x1080")
                instrumentation.waitForIdleSync()
                try { base.evaluate() } finally { shell("wm size reset") }
            }
        }
    }).around(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val now = java.time.Instant.parse("2026-10-25T16:00:00Z").toEpochMilli()
    private val channels = listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6", "Sjuan", "TV8", "Kanal 9", "TV10", "Kanal 11", "TV12").mapIndexed { i, name ->
        SmartChannel(name, name, ChannelEntity(id = i + 1L, sourceId = 101, name = name, streamUrl = "https://example.invalid/synthetic/$i")) }
    private val resumes = (1..3).map { SmartResume("episode:$it", SmartResumeKind.EPISODE, it.toLong(), 10 + it.toLong(),
        "Testavsnitt $it", "Testserie $it", 1, it, playedAt = now - it, position = 600_000, duration = 2_400_000) }
    private val fixture = SportsCatalog.fixture(SportsCatalog.shl, ShlGame("fixture-fbk", "Färjestad BK", "Skellefteå AIK", now + 3_600_000, null))
    private val guide = HomeGuideState(now, channels.associate { row -> row.channel.homeEpgKey() to SwedishRowInfo(null,
        SwedishProgramme(row.channel.id, "Testprogram ${row.label}", now - 600_000, now + 1_800_000),
        SwedishProgramme(row.channel.id + 100, "Nästa testprogram", now + 1_800_000, now + 3_600_000)) })
    private val preferences = SportPreferences(listOf(SportsCatalog.farjestadId))
    private var page by mutableStateOf(TopDestination.HOME)
    private var density by mutableFloatStateOf(1.8f)
    private var fontScale by mutableFloatStateOf(1f)
    private var profile by mutableLongStateOf(7)
    private var populated by mutableStateOf(true)
    private var entry: (() -> Boolean)? = null
    private var details = 0
    private var played = 0
    private var resumed: String? = null
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Before fun disposableOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true" && shell("getprop ro.kernel.qemu").trim() == "1")
        instrumentation.setInTouchMode(false)
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun press(code: Int) {
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
        instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        compose.waitForIdle()
    }
    private fun cards(type: MetadataType) = (1..3).map { DiscoveryCard(it.toLong(), 101, "${type.name}:$it", type,
        if (type == MetadataType.MOVIE) "Testfilm $it" else "Testserie $it", null, 2025, now, null, emptySet(), null) }
    private fun mount() { compose.setContent {
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("sv")) }
        val swedish = compose.activity.createConfigurationContext(config)
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale), LocalContext provides swedish, LocalConfiguration provides config) {
            MaterialTheme {
                val header = remember { FocusRequester() }
                Column(Modifier.fillMaxSize().background(MinTvNavy)) {
                    NativeTopNavigation(page, header, { page = it }, { page = it; entry?.invoke() }, {})
                    key(page, profile, density) {
                        val first = remember { FocusRequester() }
                        Box(Modifier.weight(1f)) {
                            when (page) {
                                TopDestination.HOME -> SmartHomeContent(
                                    SmartHomeSnapshot(profile, populated, if (populated) channels else emptyList(), if (populated) channels[0].channel else null,
                                        now, null, if (populated) resumes else emptyList()), guide, profile, { played++ }, { resumed = it.key }, {}, {}, {}, {},
                                    first, { entry = it }, {}, false, {}, 64.mpx, 24.mpx, onHeader = { header.requestFocus() },
                                    matchday = if (populated) { modifier -> HockeyQuickCard(fixture, now, null, false, "Färjestad BK", {}, modifier.testTag("fixture-matchday"), hero = true) } else null,
                                    companion = { TwitchQuickCard(TwitchState.Unavailable, {}, authenticated = false) })
                                TopDestination.TV -> TvFixture(first)
                                TopDestination.SPORT -> NativeSportDashboard(NativeSportSnapshot(profile, now, preferences, if (populated) fixture else null,
                                    if (populated) listOf(fixture, fixture.copy(id = "fixture-2", homeTeam = SportsCatalog.identity("Brynäs IF", Sport.ICE_HOCKEY)!!, awayTeam = SportsCatalog.identity("Luleå HF", Sport.ICE_HOCKEY)!!, status = MatchStatus.RESULT_SNAPSHOT, score = Score(3, 1))) else emptyList(),
                                    if (populated) listOf(fixture.copy(id = "fixture-next", kickoff = fixture.kickoff.plusSeconds(86_400))) else emptyList(),
                                    if (populated) (0..10).map { i -> channels.first().channel.copy(id = i + 100L, name = if (i == 0) "TV4 Hockey" else "TV4 Hockey Live $i") } else emptyList()),
                                    NativeSportActions({ details++ }, { played++ }, {}, {}, {}), first, { entry = it }, { header.requestFocus() })
                                TopDestination.VOD -> NativeVodLanding(NativeVodSnapshot(profile, if (populated) cards(MetadataType.MOVIE) else emptyList(),
                                    if (populated) cards(MetadataType.TV) else emptyList(), if (populated) resumes else emptyList()), first, { entry = it },
                                    { header.requestFocus() }, {}, { details++ }, { resumed = it.key })
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    } }
    @Composable private fun TvFixture(first: FocusRequester) {
        val grid = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val targets = remember { List(channels.size) { if (it == 0) first else FocusRequester() } }
        var column by remember { mutableIntStateOf(0) }
        DisposableEffect(first) { entry = { first.requestSafely() }; onDispose { entry = null } }
        SwedishChannelGridFrame(Modifier, header = { TvText("På TV just nu", size = 34, bold = true) }, categories = {}, notice = {}, channels = {
            SwedishChannelGrid(grid, Modifier.fillMaxSize()) {
                items(if (populated) channels.size else 0, key = { channels[it].key }) { index ->
                    val channel = channels[index]
                    SwedishChannelCard(channel.label, ChannelQuality.HD, guide.rows[channel.channel.homeEpgKey()], now, { played++ },
                        Modifier.testTag("fixture-tv-$index").focusRequester(targets[index]).channelGridNavigation(index, channels.size, { column }, { next, direction ->
                            if (direction == ChannelGridDirection.LEFT || direction == ChannelGridDirection.RIGHT) column = next % CHANNEL_GRID_COLUMNS
                            scope.launch { grid.scrollToItem((next / CHANNEL_GRID_COLUMNS) * CHANNEL_GRID_COLUMNS); repeat(2) { withFrameNanos { } }; targets[next].requestSafely() }
                        }, {}, {}))
                }
            }
        })
    }
    private fun enter(destination: TopDestination) {
        compose.runOnIdle { if (page != destination) { entry = null; page = destination } }
        compose.waitUntil(5000) { entry != null }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { entry?.invoke() }
        compose.waitForIdle()
        val focusedTag = when (destination) { TopDestination.HOME -> "fixture-matchday"; TopDestination.TV -> "fixture-tv-0"; TopDestination.SPORT -> "sport-hero-action"; else -> "vod-movies-entry" }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag(focusedTag).assertIsFocused(); true }.getOrDefault(false) }
    }
    private fun captures(size: String, suffix: String) {
        mount()
        compose.waitUntil(10000) { val bitmap = instrumentation.uiAutomation.takeScreenshot(); val ok = bitmap.width == size.substringBefore('x').toInt(); bitmap.recycle(); ok }
        for ((screen, name) in listOf(TopDestination.HOME to "home", TopDestination.TV to "tv", TopDestination.SPORT to "sport", TopDestination.VOD to "vod")) {
            enter(screen)
            val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            assertEquals(size.substringBefore('x').toInt(), bitmap.width)
            assertEquals(size.substringAfter('x').toInt(), bitmap.height)
            File(dir, "v011-native-$name-$suffix-synthetic.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
    @Test fun capturesFourProductionScreensAt1080WithHonestFallbacks() { captures("1920x1080", "1080") }
    @Test fun capturesFourProductionScreensAt720WithHonestFallbacks() { captures("1280x720", "720") }
    @Test fun filmSeriesRowsUseDirectionalEntryDetailsAndExactResume() {
        mount(); enter(TopDestination.VOD)
        compose.onNodeWithTag("vod-movies-entry").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("vod-series-entry").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("vod-series-TV:1").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("vod-movie-MOVIE:1").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, details); assertEquals(0, played); assertNull(resumed)
        press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("episode:2", resumed)
    }
    @Test fun sportEntryRowNavigationAndRemovedFixtureRestoreSafeFocus() {
        mount(); enter(TopDestination.SPORT)
        compose.onNodeWithTag("sport-hero-action").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("sport-today-${fixture.id}").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("sport-today-fixture-2").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("sport-upcoming-fixture-next").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("sport-today-fixture-2").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, details); assertEquals(0, played)
        compose.runOnIdle { populated = false }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("sport-hero-action").assertIsFocused(); true }.getOrDefault(false) }
        compose.onNodeWithTag("sport-today-${fixture.id}").assertDoesNotExist()
    }
    @Test fun libraryEntryUsesLatestStateWithoutRegistrationRecompositionLoop() {
        var generation by mutableIntStateOf(0)
        var count = 0
        var last = -1
        var registered: (() -> Boolean)? = null
        compose.setContent {
            NativeContentEntryHook({ hook -> registered = hook; if (hook != null) count++ }, Unit) { last = generation; true }
            MaterialTheme { TvText(generation.toString()) }
        }
        compose.waitForIdle(); assertEquals(1, count)
        compose.runOnIdle { generation = 4 }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(registered?.invoke() == true) }
        assertEquals(4, last); assertEquals(1, count)
    }
    @Test fun fontScalingAt720KeepsBothEntriesAndSportsRowsReachable() {
        fontScale = 1.4f
        mount(); enter(TopDestination.VOD)
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("vod-series-entry").assertIsFocused().assertIsDisplayed()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("vod-movies-entry").assertIsFocused().assertIsDisplayed()
        enter(TopDestination.SPORT)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("sport-today-${fixture.id}").assertIsFocused().assertIsDisplayed()
    }
    @Test fun signedOutTwitchShowsStatusAndPreservesIndependentAppEntry() {
        var launches = 0
        val focus = FocusRequester()
        compose.setContent { MaterialTheme { TwitchQuickCard(TwitchState.Unavailable, { launches++ }, Modifier.focusRequester(focus), authenticated = false) } }
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithText(compose.activity.getString(tv.own.owntv.R.string.mintv_v011_signed_out)).assertExists()
        compose.onNodeWithText(compose.activity.getString(tv.own.owntv.R.string.mintv_twitch_connect)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(tv.own.owntv.R.string.mintv_twitch_open_app)).assertExists()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(1, launches) }
    }
    @Test fun emptyProfileKeepsBothVodEntriesReachableWithoutLeakingTitles() {
        mount(); enter(TopDestination.VOD)
        compose.runOnIdle { populated = false; profile = 8 }
        compose.waitForIdle(); compose.runOnIdle { entry?.invoke() }; compose.waitForIdle()
        compose.onNodeWithTag("vod-movie-MOVIE:1").assertDoesNotExist()
        compose.onNodeWithTag("vod-movies-entry").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("vod-series-entry").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("vod-movies-entry").assertIsFocused()
        enter(TopDestination.SPORT)
        compose.onNodeWithText("Inga hockeykanaler har identifierats", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Bläddra bland hockeykanaler") and hasClickAction()).onFirst().assertIsDisplayed()
        assertEquals(0, played)
    }
}
