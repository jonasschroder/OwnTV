package tv.own.owntv.home

import tv.own.owntv.R

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
                val small = description.methodName.contains("720") || InstrumentationRegistry.getArguments().getString("nativeResolution") == "1280x720"
                density = if (small) 1.2f else 1.8f
                shell(if (small) "wm size 1280x720" else "wm size 1920x1080")
                // The TV launcher's onboarding animation may never become globally idle.
                // Wait for the actual display size before launching our own test Activity.
                val expected = if (small) 1280 to 720 else 1920 to 1080
                val deadline = android.os.SystemClock.uptimeMillis() + 5_000
                var ready: Boolean
                do {
                    val shot = instrumentation.uiAutomation.takeScreenshot()
                    ready = shot.width == expected.first && shot.height == expected.second
                    shot.recycle()
                    if (!ready) Thread.sleep(50)
                } while (!ready && android.os.SystemClock.uptimeMillis() < deadline)
                check(ready) { "Native test display did not reach $expected" }
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
    private var manualChoices = 0
    private var played = 0
    private var channelsLoading by mutableStateOf(false)
    private var broadcastReady by mutableStateOf(false)
    private var broadcastLabels by mutableStateOf(false)
    private var probableBroadcast by mutableStateOf(false)
    private val broadcastChannel = ChannelEntity(id = 401, sourceId = 101, name = "TV4 Sport Live 1 HD", streamUrl = "http://127.0.0.1:9/unavailable")
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
        compose.waitForIdle()
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
                                    if (populated) listOf(fixture, fixture.copy(id = "fixture-2", homeTeam = SportsCatalog.identity("Brynäs IF", Sport.ICE_HOCKEY)!!, awayTeam = SportsCatalog.identity("Luleå HF", Sport.ICE_HOCKEY)!!, status = MatchStatus.RESULT_SNAPSHOT, score = Score(3, 1)), fixture.copy(id = "fixture-3", homeTeam = SportsCatalog.identity("Rögle BK", Sport.ICE_HOCKEY)!!), fixture.copy(id = "fixture-4", homeTeam = SportsCatalog.identity("Leksands IF", Sport.ICE_HOCKEY)!!, awayTeam = SportsCatalog.identity("Timrå IK", Sport.ICE_HOCKEY)!!)) else emptyList(),
                                    if (populated) listOf(fixture.copy(id = "fixture-next", kickoff = fixture.kickoff.plusSeconds(86_400))) else emptyList(),
                                    if (populated && !channelsLoading) (0..10).map { i -> channels.first().channel.copy(id = i + 100L, name = if (i == 0) "TV4 Hockey" else "TV4 Hockey Live $i") } else emptyList(),
                                    heroChannel = if (broadcastReady) broadcastChannel else null,
                                    broadcastLabels = if (broadcastLabels) mapOf(fixture.id to compose.activity.getString(if (probableBroadcast) R.string.mintv_probable_channel else R.string.mintv_confirmed_channel, broadcastChannel.name)) else emptyMap(),
                                    playableFixtures = if (broadcastReady) mapOf(fixture.id to broadcastChannel) else emptyMap(), publicSourceUsed = probableBroadcast && broadcastLabels, channelsLoading = channelsLoading,
                                    broadcastAvailability = if (broadcastLabels) mapOf(fixture.id to if (broadcastReady) HockeyChannelAvailability.PLAYABLE else HockeyChannelAvailability.MISSING) else emptyMap()),
                                    NativeSportActions({ details++ }, { played++ }, {}, { played++ }, {}, { game, channel ->
                                        assertEquals(fixture, game); assertEquals(broadcastChannel, channel); played++
                                    }, { game -> assertEquals(fixture, game); manualChoices++ }), first, { entry = it }, { header.requestFocus() })
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
        var entered = false
        compose.runOnIdle { entered = entry?.invoke() == true }
        if (!entered) compose.onNodeWithTag("top-${destination.name}").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.waitForIdle()
        val focusedTag = when (destination) { TopDestination.HOME -> "fixture-matchday"; TopDestination.TV -> "fixture-tv-0"; TopDestination.SPORT -> if (populated) "sport-hero-action" else "sport-empty-action"; else -> "vod-movies-entry" }
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
    private fun automaticBroadcastCard(size: String) {
        broadcastReady = true; broadcastLabels = true
        mount(); enter(TopDestination.SPORT)
        compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_confirmed_channel, broadcastChannel.name)).assertCountEquals(1)
        val card = compose.onNodeWithTag("sport-hero-action")
        card.assertIsFocused()
        compose.runOnIdle { probableBroadcast = true }
        compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_probable_channel, broadcastChannel.name)).assertCountEquals(1)
        card.assertIsFocused(); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, played); assertEquals(0, details)
        // A status update preserves the existing focus key; long OK opens details, never playback.
        compose.runOnIdle { broadcastReady = false }
        card.assertIsFocused(); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, manualChoices); assertEquals(1, played)
        compose.runOnIdle { broadcastReady = true }
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        instrumentation.sendKeySync(KeyEvent(down, down + 600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        compose.waitForIdle(); assertEquals(2, manualChoices); assertEquals(1, played)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertEquals(size.substringBefore('x').toInt(), bitmap.width); assertEquals(size.substringAfter('x').toInt(), bitmap.height)
        val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
        File(dir, "free-hockey-probable-$size-synthetic.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        compose.runOnIdle { profile = 8; populated = false; broadcastReady = false; broadcastLabels = false }
        compose.waitForIdle(); compose.onNodeWithTag("sport-hero-action").assertDoesNotExist()
    }
    @Test fun automaticBroadcastCardPlaysOnlyUniqueSourceAt1080() { automaticBroadcastCard("1920x1080") }
    @Test fun automaticBroadcastCardPlaysOnlyUniqueSourceAt720() { automaticBroadcastCard("1280x720") }
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
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("top-SPORT").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("sport-hero-action").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("sport-today-fixture-2").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("sport-today-fixture-3").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        // Consecutive remote keys must advance twice, without timing assertions or sleeps.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitForIdle(); compose.onNodeWithTag("sport-today-fixture-4").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("sport-upcoming-fixture-next").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("sport-today-fixture-3").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, details); assertEquals(0, played)
        compose.runOnIdle { populated = false }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("sport-empty-action").assertIsFocused(); true }.getOrDefault(false) }
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("top-SPORT").assertIsFocused()
        compose.onNodeWithTag("sport-hero-action").assertDoesNotExist()
        compose.onNodeWithTag("sport-today-${fixture.id}").assertDoesNotExist()
    }
    // UI contract only: post-authentication synthetic states; no HTTP, permission grant or APK install.
    private fun updateRemoteJourney() {
        val candidate = tv.own.owntv.features.update.UpdatePolicy.Candidate("qa", "se.jonasschroder.mintv.qa", 1000099,
            "0.16.0-synthetic", "synthetic.apk", "0".repeat(64), 30_000_000, 26, setOf("arm64-v8a"),
            "Syntetiska testnyheter\n" + "En lång svensk rad om tydligare navigation och bevarade profiler.\n".repeat(20), "synthetic")
        var state by mutableStateOf<tv.own.owntv.features.update.MinTvUpdater.State>(tv.own.owntv.features.update.MinTvUpdater.State.Available(candidate))
        var open by mutableStateOf(false)
        var downloads = 0; var permissionRequests = 0; var confirmations = 0
        var backdrop by mutableStateOf(MinTvNavy)
        lateinit var entryFocus: FocusRequester
        compose.setContent {
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("sv")) }
            val swedish = compose.activity.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides swedish, LocalConfiguration provides config,
                LocalDensity provides Density(density, fontScale)) {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(backdrop)) { }
                val scope = rememberCoroutineScope()
                entryFocus = remember { FocusRequester() }
                tv.own.owntv.features.update.UpdateEntryPresentation(state, "Test", { open = true }, Modifier.focusRequester(entryFocus))
                if (open) tv.own.owntv.features.update.UpdatePresentation(state, "Test", false, null,
                    onDismiss = { open = false; scope.launch { repeat(2) { withFrameNanos { } }; entryFocus.requestFocus() } },
                    onAutomatic = {}, onAction = {
                        when (state) {
                            is tv.own.owntv.features.update.MinTvUpdater.State.Available -> {
                                downloads++; state = tv.own.owntv.features.update.MinTvUpdater.State.Downloading(1, 300_000, candidate.bytes)
                            }
                            tv.own.owntv.features.update.MinTvUpdater.State.Permission -> permissionRequests++
                            tv.own.owntv.features.update.MinTvUpdater.State.Confirmation -> {
                                confirmations++; state = tv.own.owntv.features.update.MinTvUpdater.State.Installing
                            }
                            else -> Unit
                        }
                    }, availableBytes = if ((state as? tv.own.owntv.features.update.MinTvUpdater.State.Available)?.interrupted == true) 0 else 1_000_000_000)
            }
            }
        }
        fun captureStep(step: String) {
            Thread.sleep(300) // Capture the actual steady dialog after the platform window fade.
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
            File(dir, "v016-update-flow-$step-${bitmap.width}x${bitmap.height}-synthetic.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        compose.runOnIdle { entryFocus.requestFocus() }; press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Uppdatera nu").assertIsFocused(); captureStep("review")
        press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("update-body").assertIsFocused()
        val range = compose.onNodeWithTag("update-body").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        if (range.maxValue() > 0f) {
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(5_000) { range.value() > 0f }
            compose.onNodeWithTag("update-body").assertIsFocused()
        }
        compose.onNodeWithTag("update-current-version").assertIsDisplayed()
        press(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("header-update").assertIsFocused()
        assertEquals(0, downloads); press(KeyEvent.KEYCODE_DPAD_CENTER)
        // Later keeps the authenticated candidate and returns to its exact global entry.
        press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("header-update").assertIsFocused(); assertEquals(0, downloads)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Uppdatera nu").assertIsFocused(); press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, downloads)
        compose.onNodeWithText("Uppdatera nu").assertDoesNotExist()
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Downloading(42, 12_600_000, candidate.bytes) }
        assertTrue(compose.onAllNodesWithText("42", substring = true).fetchSemanticsNodes().isNotEmpty()); captureStep("progress")
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Verifying }
        compose.onNodeWithText("Uppdatera nu").assertDoesNotExist()
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Permission }
        compose.waitForIdle(); captureStep("permission")
        press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, permissionRequests)
        // Only an explicit subsequent OK may hand off to the required system confirmation.
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Confirmation }
        compose.waitForIdle(); captureStep("confirmation")
        assertEquals(0, confirmations); press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, confirmations)
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Installed }
        press(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("header-update").assertIsFocused()
        compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Available(candidate, interrupted = true) }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, downloads) // Reopening never restarts a download on its own.
        press(KeyEvent.KEYCODE_BACK)
        for (problem in listOf(tv.own.owntv.features.update.MinTvUpdater.Problem.NETWORK, tv.own.owntv.features.update.MinTvUpdater.Problem.STORAGE)) {
            compose.runOnIdle { state = tv.own.owntv.features.update.MinTvUpdater.State.Failed(problem) }
            press(KeyEvent.KEYCODE_DPAD_CENTER); press(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("header-update").assertIsFocused()
        }
        // Render the actual shared native popup against two different underlying screens.
        // Alpha on the captured window is always 255; pixel invariance proves the body masks content.
        val cases = listOf<tv.own.owntv.features.update.MinTvUpdater.State>(
            tv.own.owntv.features.update.MinTvUpdater.State.Idle,
            tv.own.owntv.features.update.MinTvUpdater.State.Checking,
            tv.own.owntv.features.update.MinTvUpdater.State.UpToDate,
            tv.own.owntv.features.update.MinTvUpdater.State.Failed(tv.own.owntv.features.update.MinTvUpdater.Problem.SETUP),
            tv.own.owntv.features.update.MinTvUpdater.State.Available(candidate),
            tv.own.owntv.features.update.MinTvUpdater.State.Downloading(42, 12_600_000, candidate.bytes),
            tv.own.owntv.features.update.MinTvUpdater.State.Verifying,
            tv.own.owntv.features.update.MinTvUpdater.State.Failed(tv.own.owntv.features.update.MinTvUpdater.Problem.STORAGE),
            tv.own.owntv.features.update.MinTvUpdater.State.Permission,
            tv.own.owntv.features.update.MinTvUpdater.State.Confirmation,
            tv.own.owntv.features.update.MinTvUpdater.State.Installing,
            tv.own.owntv.features.update.MinTvUpdater.State.Cancelled,
            tv.own.owntv.features.update.MinTvUpdater.State.Installed,
            tv.own.owntv.features.update.MinTvUpdater.State.Available(candidate, interrupted = true))
        for (scale in listOf(1f, 1.4f)) for (next in cases) {
            compose.runOnIdle { fontScale = scale; state = next; backdrop = androidx.compose.ui.graphics.Color.Red; open = true }
            compose.waitForIdle()
            compose.onNodeWithTag("update-current-version").assertIsDisplayed()
            compose.onNodeWithTag("update-status").assertIsDisplayed()
            val bounds = compose.onNodeWithTag("update-surface").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            fun pixels(): List<Int> {
                instrumentation.waitForIdleSync()
                val shot = instrumentation.uiAutomation.takeScreenshot()
                return try { listOf(.2f, .4f, .6f, .8f).map { t ->
                    shot.getPixel((bounds.left + 12 * density).toInt(), (bounds.top + bounds.height * t).toInt())
                } + shot.getPixel(2, shot.height / 2) } finally { shot.recycle() }
            }
            fun distance(a: Int, b: Int) = listOf(0, 8, 16).maxOf { shift ->
                kotlin.math.abs((a ushr shift and 255) - (b ushr shift and 255))
            }
            fun settled(redBackground: Boolean): List<Int> {
                var sample = emptyList<Int>()
                // Platform Dialog entry fades are outside Compose's idle clock. Wait for the
                // real opaque paint at the clean heading margin. Other samples may include
                // internal button shadows; all four must still be background-invariant (2/255 rounding).
                try { compose.waitUntil(3_000) {
                    sample = pixels()
                    distance(sample.first(), 0xFF142237.toInt()) <= 2 &&
                        if (redBackground) (sample.last() ushr 16 and 255) > (sample.last() ushr 8 and 255)
                        else (sample.last() ushr 8 and 255) > (sample.last() ushr 16 and 255)
                }
                } catch (error: Throwable) {
                    val shot = instrumentation.uiAutomation.takeScreenshot()
                    val file = File(compose.activity.getExternalFilesDir(null), "v04-screenshots/pixel-failure.png")
                    file.parentFile!!.mkdirs(); file.outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; shot.recycle()
                    throw AssertionError("$next / $scale / red=$redBackground / $bounds / ${sample.map { Integer.toHexString(it) }}", error)
                }
                return sample
            }
            val red = settled(true)
            compose.runOnIdle { backdrop = androidx.compose.ui.graphics.Color.Green }; compose.waitForIdle()
            val green = settled(false)
            assertTrue("Opaque modal must mask underlying content: $next / $scale",
                red.take(4).zip(green.take(4)).all { (a, b) -> distance(a, b) <= 2 })
            assertNotEquals("The underlying screen must actually change", red.last(), green.last())
            val identity = compose.onNodeWithTag("update-current-version").fetchSemanticsNode().boundsInRoot
            assertTrue("Version must stay inside the visible panel", bounds.contains(identity.topLeft) && bounds.contains(identity.bottomRight))
            press(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("header-update").assertIsFocused()
        }
        assertEquals(1, downloads); assertEquals(1, permissionRequests); assertEquals(1, confirmations)
    }
    @Test fun updateRemotePresentationJourneyAt1080() { updateRemoteJourney() }
    @Test fun updateRemotePresentationJourneyAt720() { updateRemoteJourney() }

    private fun matchFirstHome() {
        var played = 0; var details = 0; var choices = 0
        var playable by mutableStateOf(false)
        val first = FocusRequester()
        compose.setContent { MaterialTheme {
            HockeyQuickCard(fixture, now, "TV4 Sport Live 1", playable, "Färjestad", { played++ },
                Modifier.focusRequester(first), onDetails = { details++ }, hero = true,
                onChooseChannel = { choices++ }, probable = !playable)
        } }
        compose.runOnIdle { first.requestFocus() }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_v0111_match_details)).assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, details); assertEquals(0, played)
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        Thread.sleep(650)
        instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        compose.waitForIdle(); assertEquals(1, choices); assertEquals(1, details); assertEquals(0, played)
        compose.runOnIdle { playable = true }
        press(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, played); assertEquals(1, details)
    }
    @Test fun homeMatchFirstActionsAt1080() { matchFirstHome() }
    @Test fun homeMatchFirstActionsAt720() { matchFirstHome() }

    @Test fun settingsLeftReturnsToActiveCategoryAfterScrollingDetails() {
        var selected by mutableIntStateOf(tv.own.owntv.features.shell.components.SettingsGroup.APP.ordinal)
        lateinit var category: FocusRequester
        compose.setContent {
            MaterialTheme {
                category = remember { FocusRequester() }
                val content = remember { FocusRequester() }
                tv.own.owntv.features.settings.StageSettingsPage(
                    group = tv.own.owntv.features.shell.components.SettingsGroup.entries[selected].name, count = "", searchQuery = "", onSearchQuery = {},
                    scroll = androidx.compose.foundation.rememberScrollState(), showSearch = false,
                    rowsFocus = content, rowsReturnFocus = category,
                    categories = { tv.own.owntv.features.shell.components.SettingsCategoryMenu(selected, { selected = it },
                        { if (!content.restoreFocusedChild()) content.requestFocus() }, category) }) {
                    repeat(20) { index -> key(index) { HomeButton("Synthetic setting $index", {}, Modifier.testTag("setting-$index")) } }
                }
            }
        }
        compose.runOnIdle { category.requestFocus() }; compose.waitForIdle()
        compose.onNodeWithTag("settings-group-APP").assertIsFocused().assertIsSelected()
        press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("setting-0").assertIsFocused()
        repeat(6) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        compose.onNodeWithTag("setting-6").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("settings-group-APP").assertIsFocused()
        assertEquals(tv.own.owntv.features.shell.components.SettingsGroup.APP.ordinal, selected)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        try {
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("setting-6").assertIsFocused(); true }.getOrDefault(false) }
        } catch (error: Throwable) { throw AssertionError(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 10), error) }
        press(KeyEvent.KEYCODE_DPAD_LEFT); press(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("settings-group-APP").assertIsSelected()
        compose.onNodeWithTag("settings-group-WATCHING").assertIsFocused().assertIsNotSelected()
        assertEquals(tv.own.owntv.features.shell.components.SettingsGroup.APP.ordinal, selected)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("setting-0").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals(tv.own.owntv.features.shell.components.SettingsGroup.WATCHING.ordinal, selected)
        press(KeyEvent.KEYCODE_DPAD_LEFT); compose.onNodeWithTag("settings-group-WATCHING").assertIsFocused()
    }
    @Test fun homeGuideArrivalsAndMissingNextPreserveActionFocus() {
        var currentGuide by mutableStateOf(HomeGuideState(now, emptyMap()))
        lateinit var first: FocusRequester
        compose.setContent {
            MaterialTheme {
                first = remember { FocusRequester() }
                SmartHomeContent(SmartHomeSnapshot(7, true, channels, channels.first().channel, now, null, emptyList()),
                    currentGuide, 7, {}, {}, {}, {}, {}, {}, first, {}, {}, false, {}, 64.mpx, 24.mpx, onHeader = {})
            }
        }
        compose.runOnIdle { first.requestFocus() }; compose.waitForIdle()
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_live_no_programme)).onFirst().assertIsDisplayed()
        compose.runOnIdle { currentGuide = guide.copy(rows = guide.rows.mapValues { it.value.copy(next = null) }) }
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_next_not_available)).onFirst().assertIsDisplayed()
        compose.runOnIdle { currentGuide = guide }
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
        compose.onAllNodesWithText("Nästa testprogram", substring = true).onFirst().assertIsDisplayed()
        compose.runOnIdle { currentGuide = HomeGuideState(now + 86_400_000, emptyMap()) }
        compose.onNodeWithTag("smart-hero-play").assertIsFocused()
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
        compose.onNodeWithTag("sport-today-fixture-2").assertIsFocused().assertIsDisplayed()
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
        // The fixture overrides density: compare pixels, not dp values from different owners.
        val entryBounds = compose.onNodeWithTag("vod-movies-entry").fetchSemanticsNode()
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Entry must retain both TV-safe gutters: ${entryBounds.positionInRoot} / ${entryBounds.size} in $rootBounds",
            entryBounds.positionInRoot.x > rootBounds.left && entryBounds.positionInRoot.x + entryBounds.size.width < rootBounds.right)
        press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("vod-series-entry").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("vod-movies-entry").assertIsFocused()
        enter(TopDestination.SPORT)
        compose.runOnIdle { channelsLoading = true }
        compose.onNodeWithText("Inga hockeykanaler har identifierats", substring = true).assertDoesNotExist()
        compose.runOnIdle { channelsLoading = false }
        compose.onNodeWithText("Inga hockeykanaler har identifierats", substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Bläddra bland hockeykanaler") and hasClickAction()).assertCountEquals(0)
        compose.onNodeWithTag("sport-settings").assertDoesNotExist()
        compose.onNodeWithTag("sport-browse").assertDoesNotExist()
        assertEquals(0, played)
    }
}
