package tv.own.owntv.home

import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.home.*
import tv.own.owntv.features.update.*
import tv.own.owntv.ui.theme.mpx

/** Actual dashboard frame, hero, companions, continuation, discovery and update presentation.
 * All content is explicitly synthetic; no account, provider, stream, network or installer is used.
 * Fullscreen tests assert existing callbacks/focus paths, not a physical decoder/provider session.
 */
class HomeDashboardPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = 1_791_720_000_000L
    private var before by mutableStateOf(false)
    private var hasHistory by mutableStateOf(true)
    private var hasGuide by mutableStateOf(true)
    private var hockeyKnown by mutableStateOf(true)
    private var twitch: TwitchState by mutableStateOf(TwitchState.Offline)
    private var update: MinTvUpdater.State by mutableStateOf(MinTvUpdater.State.Idle)
    private var rows by mutableStateOf(listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.FAVORITES,
        HomeSection.CONTINUE, HomeSection.NOW, HomeSection.SERIES, HomeSection.MOVIES))
    private var opened = 0
    private var watched = 0
    private var updates = 0
    private var closeUpdate = 0
    private var scroll: ((Int) -> Unit)? = null
    @Before fun remoteMode() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
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
    private fun candidate() = UpdatePolicy.Candidate("stable", BuildConfig.APPLICATION_ID, 1234567, "synthetic-next",
        "synthetic.apk", "0".repeat(64), 1, 26, setOf("arm64-v8a"),
        "### Syntetiska testnyheter\n- Ny startsida\n- Tydligare uppdateringar\n" + "- Syntetisk rad för fjärrkontrollens skrolltest\n".repeat(16), "synthetic")
    private fun mount() {
        compose.setContent {
            val native = LocalConfiguration.current
            val configuration = Configuration(native).apply { setLocale(Locale.forLanguageTag("sv")) }
            val context = compose.activity.createConfigurationContext(configuration)
            val width = LocalWindowInfo.current.containerSize.width.toFloat().coerceAtLeast(1f)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalDensity provides Density(1.8f * width / 1920f, 1f), LocalResources provides context.resources) {
                MaterialTheme {
                    val list = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    val first = remember { FocusRequester() }
                    val nav = rememberHomeDpadNavigation(first, 10, 7, firstSection = 0)
                    var fullscreen by remember { mutableStateOf(false) }
                    var showUpdate by remember { mutableStateOf(false) }
                    val indices = dashboardIndices(rows, true, true)
                    ReconcileHomeNavigation(nav, list, indices, !fullscreen && !showUpdate)
                    SideEffect { scroll = { index -> scope.launch { list.scrollToItem(index) } } }
                    fun restore() { scope.launch { list.scrollToItem(indices[nav.selected]); withFrameNanos { }; withFrameNanos { }; nav.focus[nav.selected].requestSafely() } }
                    BackHandler(fullscreen) { fullscreen = false; restore() }
                    Box(Modifier.fillMaxSize().background(MinTvNavy)) {
                    if (fullscreen) Box(Modifier.fillMaxSize().background(MinTvNavy).testTag("dashboard-fullscreen")) { TvText("Syntetisk uppspelningskontroll") }
                    else HomeDashboardFrame(list, rows, 150.mpx, 110.mpx,
                        Modifier.fillMaxSize().testTag("dashboard").then(homeDpadModifier(nav, list, indices)),
                        header = { HomeDashboardHeader("Syntetisk profil", update, { showUpdate = true }, {}, nav.section(9).testTag("dashboard-version")) }) { section ->
                        when (section) {
                            HomeSection.LIVE -> if (before) BeforeV08Hero("TV4 HD", null,
                                XtEpgEntry("Syntetiska Nyheterna", null, now - 600_000, now + 1_200_000),
                                "Syntetiskt nästa program", now, null, {}, {}, {}, {}, firstAction = nav.section(0).testTag("dashboard-watch")) { TvText("TV4", size = 80, color = MinTvTeal) }
                            else HomeLiveHero(if (hasHistory) "TV4 HD" else null, null,
                                if (hasGuide && hasHistory) XtEpgEntry("Syntetiska Nyheterna", null, now - 600_000, now + 1_200_000) else null,
                                if (hasGuide && hasHistory) "Syntetiskt nästa program" else null, now,
                                context.getString(R.string.mintv_preview_disabled),
                                { watched++; fullscreen = true }, { opened++ }, {}, {},
                                Modifier.testTag("dashboard-hero"), nav.section(0).testTag("dashboard-watch"), onVariants = {}) {
                                    TvText("TV4", size = 80, color = MinTvTeal, bold = true)
                                }
                            HomeSection.SPORT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                                Box(Modifier.weight(1.15f)) { HockeyQuickCard(if (hockeyKnown) SportsCatalog.fixture(SportsCatalog.shl,
                                    ShlGame("synthetic", "Färjestad BK", "Luleå HF", now + 3_600_000, null)) else null,
                                    now, if (hockeyKnown) "TV4 Sport Live 2" else null, hockeyKnown, "Färjestad BK",
                                    { opened++ }, { opened++ }, nav.section(3).testTag("dashboard-hockey")) }
                                Box(Modifier.weight(1f).onFocusChanged { if (it.hasFocus) nav.selected = 6 }) {
                                    TwitchQuickCard(twitch, true, {}, { opened++ }, nav.section(6).testTag("dashboard-twitch"))
                                }
                            }
                            HomeSection.FAVORITES, HomeSection.NOW -> {
                                val index = if (section == HomeSection.NOW) 8 else 1
                                Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                                    TvText(context.getString(if (section == HomeSection.NOW) R.string.mintv_v08_now else R.string.mintv_v08_quick_channels), size = 27, bold = true)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(5.mpx)) {
                                        itemsIndexed(listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6")) { i, name ->
                                            if (section == HomeSection.NOW) HomeNowChannelCard(name, null,
                                                tv.own.owntv.features.live.ChannelQuality.HD,
                                                tv.own.owntv.features.live.SwedishRowInfo(null,
                                                    tv.own.owntv.features.live.SwedishProgramme(i.toLong(), "Syntetiskt program $i", now - 600_000, now + 1_200_000),
                                                    tv.own.owntv.features.live.SwedishProgramme(i + 100L, "Syntetiskt nästa $i", now + 1_200_000, now + 2_400_000)),
                                                now, { watched++; fullscreen = true }, Modifier.width(330.mpx).then(if (i == 0) nav.section(index) else Modifier))
                                            else ChannelCard(name, null, null, { watched++; fullscreen = true },
                                                Modifier.width(300.mpx).then(if (i == 0) nav.section(index) else Modifier))
                                        }
                                    }
                                }
                            }
                            HomeSection.CONTINUE -> HomeContinuationRow((1L..6L).map { id -> LauncherContinuationItem(
                                LauncherContinuationKind.MOVIE, id, id, "synthetic:$id", "Syntetisk film $id", "Film · 42 minuter kvar", null,
                                Uri.EMPTY, now, 100_000, 45_000, LauncherWatchNextType.CONTINUE) }, nav.section(2).testTag("dashboard-resume")) { watched++; fullscreen = true }
                            HomeSection.SERIES, HomeSection.MOVIES -> {
                                val type = if (section == HomeSection.MOVIES) MetadataType.MOVIE else MetadataType.TV
                                DiscoveryPosterRow(DiscoveryRow(DiscoveryKind.TOP, (1L..6L).map { id -> DiscoveryCard(id, 101, "synthetic:$id", type,
                                    if (type == MetadataType.MOVIE) "Syntetisk film $id" else "Syntetisk serie $id", null, 2026, 1, 8.2, setOf("Drama"), id.toInt()) }), type, null,
                                    { opened++ }, first = nav.section(if (type == MetadataType.MOVIE) 4 else 5))
                            }
                            else -> Unit
                        }
                    }
                    if (!fullscreen) tv.own.owntv.features.shell.components.StageStatusCluster(
                        null, null, "Syntetisk IPTV-källa", false, {}, false, null, false,
                        tv.own.owntv.core.settings.ClockColors(), Modifier.align(Alignment.TopEnd))
                    }
                    if (showUpdate) UpdatePresentation(update, context.getString(R.string.mintv_update_channel_stable), false, null,
                        { showUpdate = false; closeUpdate++; restore() }, {}, { updates++ })
                    LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun frozenMainHeroProvidesQualifiedBeforeComparisonWithoutPlayback() {
        before = true; mount()
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        screenshot("v08-before-hero-main-09273507-synthetic.png")
        assertEquals(0, watched); assertEquals(0, opened)
    }
    @Test fun completeDashboardShowsWatchedEpgAndHonestMissingStatesWithoutAutoplay() {
        mount()
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        compose.onNodeWithText("Syntetiska Nyheterna").assertIsDisplayed()
        assertEquals(0, watched)
        screenshot("v08-home-1080p-synthetic.png")
        screenshot("v08-home-last-watched-epg-synthetic.png")
        compose.runOnIdle { hasGuide = false }
        compose.onNodeWithText("Syntetiska Nyheterna").assertDoesNotExist()
        screenshot("v08-home-missing-epg-synthetic.png")
        compose.runOnIdle { hasHistory = false }
        compose.onNodeWithText("Din TV börjar här").assertIsDisplayed()
        screenshot("v08-home-no-history-synthetic.png")
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        assertEquals(0, watched)
    }
    @Test fun companionChangesDoNotStealFocusAndRemoteCanReachKnownAndUnknownHockeyAndLiveTwitch() {
        mount(); key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        screenshot("v08-hockey-known-and-twitch-offline-synthetic.png")
        compose.runOnIdle { hockeyKnown = false; twitch = TwitchState.Live("Syntetisk direktsändning", 1234, "Counter-Strike 2") }
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        screenshot("v08-hockey-unknown-and-twitch-live-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("dashboard-twitch").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, opened); assertEquals(0, watched)
    }
    @Test fun continuationAndReorderedRowsRestoreFocusAfterFullscreenAndDynamicRemoval() {
        mount()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("dashboard-resume").assertIsFocused()
        screenshot("v08-continue-watching-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, watched)
        compose.onNodeWithTag("dashboard-fullscreen").assertIsDisplayed()
        key(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("dashboard-resume").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        screenshot("v08-discovery-series-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        screenshot("v08-discovery-movies-synthetic.png")
        compose.runOnIdle { rows = listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.MOVIES, HomeSection.NOW, HomeSection.CONTINUE) }
        key(KeyEvent.KEYCODE_DPAD_DOWN) // movies now precedes Now, regardless of stable section ID
        compose.onNodeWithText("Syntetiskt program 0").assertIsDisplayed()
        screenshot("v08-on-tv-now-synthetic.png")
        compose.runOnIdle { rows = rows - HomeSection.NOW }
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("dashboard-watch").assertIsFocused(); true }.getOrDefault(false) }
        assertEquals(1, watched)
    }
    @Test fun updateDialogShowsRealInstalledVersionAndDistinctProgressFailureAndConfirmationStates() {
        mount(); key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("dashboard-version").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        try {
            compose.waitUntil(5000) { runCatching { compose.onNodeWithText("Stäng").assertIsFocused(); true }.getOrDefault(false) }
        } catch (error: Throwable) {
            screenshot("v08-about-failure-synthetic.png")
            compose.onAllNodes(isRoot()).printToLog("V08_SYNTHETIC_UPDATE")
            throw error
        }
        compose.onNodeWithText("Installerad: ${BuildConfig.VERSION_NAME} · Bygge ${BuildConfig.VERSION_CODE}").assertExists()
        screenshot("v08-about-current-version-synthetic.png")
        key(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("dashboard-version").assertIsFocused()
        compose.runOnIdle { update = MinTvUpdater.State.Available(candidate()) }
        screenshot("v08-header-update-available-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        screenshot("v08-update-available-synthetic.png")
        compose.onNodeWithText("Syntetiska testnyheter", substring = true).assertExists()
        val variants = listOf(
            MinTvUpdater.State.Downloading(42) to "downloading",
            MinTvUpdater.State.Verifying to "verifying",
            MinTvUpdater.State.Permission to "permission",
            MinTvUpdater.State.Confirmation to "confirmation",
            MinTvUpdater.State.Cancelled to "cancelled",
            MinTvUpdater.State.Failed(MinTvUpdater.Problem.NETWORK) to "offline",
            MinTvUpdater.State.Failed(MinTvUpdater.Problem.RATE_LIMIT) to "rate-limit",
            MinTvUpdater.State.Installed to "installed")
        for ((state, name) in variants) {
            compose.runOnIdle { update = state }; compose.waitForIdle()
            screenshot("v08-update-$name-synthetic.png")
            if (state is MinTvUpdater.State.Downloading || state == MinTvUpdater.State.Verifying)
                compose.onAllNodesWithText("Syntetiska testnyheter", substring = true).assertCountEquals(0)
        }
        assertEquals(0, updates) // Screenshots never invoke network, trust verification or installer.
        key(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("dashboard-version").assertIsFocused()
        assertEquals(2, closeUpdate)
    }
    @Test fun real720pEmulatorLayoutRetainsReadableHeroAndRemoteNavigation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Temporary display mode on the explicitly disposable emulator only; never a physical app.
        fun command(value: String) { automation.executeShellCommand(value).use { fd -> java.io.FileInputStream(fd.fileDescriptor).readBytes() } }
        try {
            command("wm size 1280x720")
            compose.waitUntil(5000) { compose.activity.resources.configuration.screenWidthDp < 800 }
            remoteMode() // resize may recreate the disposable activity/window
            mount()
            compose.onNodeWithTag("dashboard-watch").assertIsFocused()
            compose.onNodeWithText("Syntetiska Nyheterna").assertIsDisplayed()
            val shot = automation.takeScreenshot()
            assertEquals(1280, shot.width); assertEquals(720, shot.height)
            screenshot("v08-home-720p-synthetic.png")
            key(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        } finally { command("wm size reset"); compose.waitUntil(5000) { compose.activity.resources.configuration.screenWidthDp >= 800 } }
    }
}
