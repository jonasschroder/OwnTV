package tv.own.owntv.home

import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.*
import tv.own.owntv.features.shell.components.*
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private val now = System.currentTimeMillis()
    private var before by mutableStateOf(false)
    private var hasHistory by mutableStateOf(true)
    private var hasGuide by mutableStateOf(true)
    private var hockeyKnown by mutableStateOf(true)
    private var hockeyWatches = 0
    private var selectedSection = 0
    private var twitch: TwitchState by mutableStateOf(TwitchState.Offline)
    private var update: MinTvUpdater.State by mutableStateOf(MinTvUpdater.State.Idle)
    private var rows by mutableStateOf(listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.FAVORITES,
        HomeSection.CONTINUE, HomeSection.NOW, HomeSection.SERIES, HomeSection.MOVIES))
    private var profileKey by mutableLongStateOf(7L)
    private var profile by mutableStateOf("Syntetisk profil")
    private var programmeTitle by mutableStateOf("Syntetiska Nyheterna")
    private var nextProgramme by mutableStateOf("Syntetiskt nästa program")
    private var wideStatus by mutableStateOf(false)
    private var menuVisible by mutableStateOf(false)
    private var sidebarFocused by mutableStateOf(false)
    private var watchFocused by mutableStateOf(false)
    private var videoMounts = 0
    private var guideOpens = 0
    private var variantOpens = 0
    private var opened = 0
    private var watched = 0
    private var updates = 0
    private var closeUpdate = 0
    private var scroll: ((Int) -> Unit)? = null
    private val posterUrls = mutableMapOf<String, String>()
    @Before fun remoteMode() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val qemu = automation.executeShellCommand("getprop ro.kernel.qemu").use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).readBytes().toString(Charsets.UTF_8).trim()
        }
        assertEquals("Display fixtures are emulator-only", "1", qemu)
        if (posterUrls.isEmpty()) for (kind in listOf("MOVIE", "TV", "CONTINUE")) for (id in 1L..6L)
            posterUrls["$kind:$id"] = syntheticPoster(kind, id)
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    /** Local artwork is synthetic test content, rendered by the real Coil/card path, never HTTP. */
    private fun syntheticPoster(kind: String, id: Long): String {
        val dir = File(compose.activity.cacheDir, "v08-synthetic-art").apply { mkdirs() }
        val target = File(dir, "$kind-$id.png")
        if (!target.isFile) {
            val landscape = kind == "CONTINUE"
            val width = if (landscape) 640 else 384
            val height = if (landscape) 360 else 576
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val colors = intArrayOf(0xFF356C80.toInt(), 0xFF4A6269.toInt(), 0xFF715A49.toInt(), 0xFF31596B.toInt())
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                colors[(id.toInt() - 1) % colors.size], 0xFF0D172A.toInt(), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null; paint.color = 0x405BDACB
            canvas.drawCircle(width * .7f, height * .32f, width * .36f, paint)
            paint.color = 0xFF102D42.toInt()
            val shape = android.graphics.Path().apply {
                moveTo(0f, height * .8f); lineTo(width * .45f, height * .32f)
                lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
            }
            canvas.drawPath(shape, paint)
            paint.color = android.graphics.Color.WHITE; paint.textSize = if (landscape) 48f else 36f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            canvas.drawText((if (kind == "TV") "TESTSERIE " else "TESTFILM ") + id, 26f, height * .79f, paint)
            paint.textSize = 20f; paint.color = 0xFFB5DAD8.toInt()
            canvas.drawText("SYNTETISKT TESTMATERIAL", 26f, height * .9f, paint)
            target.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        return Uri.fromFile(target).toString()
    }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun waitFocused(label: String) {
        compose.waitUntil(5000) { runCatching { compose.onNodeWithText(label).assertIsFocused(); true }.getOrDefault(false) }
        compose.onNodeWithText(label).assertIsFocused()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Compose semantics/layout can be idle before SurfaceFlinger presents the new state.
        val drawn = CountDownLatch(1)
        compose.runOnUiThread {
            compose.activity.window.decorView.postOnAnimation {
                compose.activity.window.decorView.postOnAnimation { drawn.countDown() }
            }
        }
        assertTrue("Wait for actual screenshot frames", drawn.await(3, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
                    val nav = rememberHomeDpadNavigation(first, 10, profileKey, firstSection = 0)
                    val currentSection = nav.selected
                    SideEffect { selectedSection = currentSection }
                    val sidebar = remember { FocusRequester() }
                    val density = LocalDensity.current
                    var statusWidth by remember { mutableStateOf(760.mpx) }
                    var statusHeight by remember { mutableStateOf(120.mpx) }
                    var fullscreen by remember { mutableStateOf(false) }
                    var showUpdate by remember { mutableStateOf(false) }
                    val indices = dashboardIndices(rows, true, true)
                    ReconcileHomeNavigation(nav, list, indices, !fullscreen && !showUpdate)
                    SideEffect { scroll = { index -> scope.launch { list.scrollToItem(index) } } }
                    fun restore() { scope.launch { list.scrollToItem(indices[nav.selected]); withFrameNanos { }; withFrameNanos { }; nav.focus[nav.selected].requestSafely() } }
                    BackHandler(fullscreen) { fullscreen = false; restore() }
                    Box(Modifier.fillMaxSize().background(MinTvNavy).onPreviewKeyEvent {
                        if (it.key == Key.DirectionLeft && watchFocused && !menuVisible && !fullscreen && !showUpdate) {
                            if (it.type == KeyEventType.KeyDown) sidebar.requestSafely()
                            true
                        } else false
                    }) {
                    if (fullscreen) Box(Modifier.fillMaxSize().background(MinTvNavy).testTag("dashboard-fullscreen")) { TvText("Syntetisk uppspelningskontroll") }
                    else HomeDashboardFrame(list, rows, if (sidebarFocused) 344.mpx else 150.mpx, statusHeight + 24.mpx,
                        Modifier.fillMaxSize().testTag("dashboard").then(homeDpadModifier(nav, list, indices)),
                        statusWidth = if (wideStatus) 1300.mpx else statusWidth,
                        header = { Box(Modifier.testTag("dashboard-header")) { HomeDashboardHeader(profile, update, { showUpdate = true }, {}, nav.section(9).testTag("dashboard-version")) } }) { section ->
                        when (section) {
                            HomeSection.LIVE -> if (before) BeforeV08Hero("TV4 HD", null,
                                XtEpgEntry("Syntetiska Nyheterna", null, now - 600_000, now + 1_200_000),
                                "Syntetiskt nästa program", now, null, {}, {}, {}, {}, firstAction = nav.section(0).testTag("dashboard-watch")) { TvText("TV4", size = 80, color = MinTvTeal) }
                            else HomeLiveHero(if (hasHistory) "TV4 HD" else null, null,
                                if (hasGuide && hasHistory) XtEpgEntry(programmeTitle, "Syntetiska nyheter och reportage från vår testtablå.", now - 600_000, now + 1_200_000) else null,
                                if (hasGuide && hasHistory) nextProgramme else null, now,
                                context.getString(R.string.mintv_preview_disabled),
                                { if (hasHistory) { watched++; fullscreen = true } else opened++ }, { opened++ }, { guideOpens++ }, {},
                                Modifier.testTag("dashboard-hero"), nav.section(0).testTag("dashboard-watch").onFocusChanged { watchFocused = it.hasFocus },
                                onVariants = { variantOpens++ }, showVideo = false, sourceName = "Syntetisk IPTV-källa",
                                onMenuVisibility = { menuVisible = it }) {
                                    SideEffect { videoMounts++ }
                                    TvText("TV4", size = 80, color = MinTvTeal, bold = true)
                                }
                            HomeSection.SPORT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                                Box(Modifier.weight(if (twitch is TwitchState.Live) 1.35f else 1.8f).onFocusChanged { if (it.hasFocus) nav.selected = 3 }.focusGroup()) { HockeyQuickCard(if (hockeyKnown) SportsCatalog.fixture(SportsCatalog.shl,
                                    ShlGame("synthetic", "Färjestad BK", "Luleå HF", now + 3_600_000, null)) else null,
                                    now, if (hockeyKnown) "TV4 Sport Live 2" else null, hockeyKnown, "Färjestad BK",
                                    { hockeyWatches++ }, nav.section(3).testTag("dashboard-hockey"), onDetails = { opened++ }) }
                                Box(Modifier.weight(1f).onFocusChanged { if (it.hasFocus) nav.selected = 6 }) {
                                    TwitchQuickCard(twitch, true, {}, { opened++ }, nav.section(6).testTag("dashboard-twitch"))
                                }
                            }
                            HomeSection.FAVORITES, HomeSection.NOW -> {
                                val index = if (section == HomeSection.NOW) 8 else 1
                                Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        TvText(context.getString(if (section == HomeSection.NOW) R.string.mintv_v08_now else R.string.mintv_v08_quick_channels), size = 27, bold = true)
                                        DashboardAction(context.getString(R.string.mintv_home_live), { opened++ })
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(5.mpx)) {
                                        itemsIndexed(listOf("SVT1", "SVT2", "TV3", "TV4", "Kanal 5", "TV6")) { i, name ->
                                            if (section == HomeSection.NOW) HomeNowChannelCard(name, null,
                                                tv.own.owntv.features.live.ChannelQuality.HD,
                                                tv.own.owntv.features.live.SwedishRowInfo(null,
                                                    tv.own.owntv.features.live.SwedishProgramme(i.toLong(), "Syntetiskt program $i", now - 600_000, now + 1_200_000),
                                                    tv.own.owntv.features.live.SwedishProgramme(i + 100L, "Syntetiskt nästa $i", now + 1_200_000, now + 2_400_000)),
                                                now, { watched++; fullscreen = true }, Modifier.width(330.mpx).then(if (i == 0) nav.section(index).testTag("dashboard-now") else Modifier))
                                            else ChannelCard(name, null, null, { watched++; fullscreen = true },
                                                Modifier.width(230.mpx).then(if (i == 0) nav.section(index).testTag("dashboard-quick") else Modifier))
                                        }
                                    }
                                }
                            }
                            HomeSection.CONTINUE -> HomeContinuationRow((1L..6L).map { id -> LauncherContinuationItem(
                                LauncherContinuationKind.MOVIE, id, id, "synthetic:$id", "Syntetisk film $id", "Film · 42 minuter kvar", posterUrls["CONTINUE:$id"].takeIf { id != 6L },
                                Uri.EMPTY, now, 100_000, 45_000, LauncherWatchNextType.CONTINUE) }, nav.section(2).testTag("dashboard-resume")) { watched++; fullscreen = true }
                            HomeSection.SERIES, HomeSection.MOVIES -> {
                                val type = if (section == HomeSection.MOVIES) MetadataType.MOVIE else MetadataType.TV
                                DiscoveryPosterRow(DiscoveryRow(DiscoveryKind.TOP, (1L..6L).map { id -> DiscoveryCard(id, 101, "synthetic:$id", type,
                                    if (type == MetadataType.MOVIE) "Syntetisk film $id" else "Syntetisk serie $id", posterUrls["${type.name}:$id"].takeIf { id != 6L }, 2026, 1, 8.2, setOf("Drama"), id.toInt()) }), type, null,
                                    { opened++ }, first = nav.section(if (type == MetadataType.MOVIE) 4 else 5).testTag(if (type == MetadataType.MOVIE) "dashboard-movies" else "dashboard-series"))
                            }
                            else -> Unit
                        }
                    }
                    if (!fullscreen) tv.own.owntv.features.shell.components.StageStatusCluster(
                        null, null, "Syntetisk IPTV-källa", false, {}, false, null, false,
                        tv.own.owntv.core.settings.ClockColors(), Modifier.align(Alignment.TopEnd).testTag("dashboard-status")
                            .onSizeChanged { statusWidth = with(density) { it.width.toDp() }; statusHeight = with(density) { it.height.toDp() } })
                    if (!fullscreen) StageRail(
                        state = if (sidebarFocused) RailState.OPEN else RailState.CAPSULE,
                        size = SettingsRepository.NavSize.NORMAL, length = SettingsRepository.NavLength.FULL,
                        selected = MainSection.HOME, visibleSections = setOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.EPG, MainSection.MOVIES, MainSection.SERIES),
                        onSelect = { sidebarFocused = false; nav.focus[nav.selected].requestSafely() }, count = { null }, detail = { null },
                        profileName = profile, profileLine = "Syntetisk IPTV-källa", onSwitchProfile = {}, onPickAvatar = {},
                        selectedItemFocusRequester = sidebar, contentFocusRequester = first,
                        enterContent = { sidebarFocused = false; nav.focus[nav.selected].requestSafely() },
                        onFocused = { sidebarFocused = true }, nowPlaying = null, onNowPlaying = {})
                    }
                    if (showUpdate) UpdatePresentation(update, context.getString(R.string.mintv_update_channel_stable), false, null,
                        { showUpdate = false; closeUpdate++; restore() }, {}, { updates++ })
                    LaunchedEffect(profileKey) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
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
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, opened); assertEquals(0, watched)
    }
    @Test fun companionChangesDoNotStealFocusAndRemoteCanReachKnownAndUnknownHockeyAndLiveTwitch() {
        mount(); key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        screenshot("v08-hockey-known-and-twitch-offline-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, opened); assertEquals(0, hockeyWatches)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_watch_match)).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, hockeyWatches); assertEquals(1, opened)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("dashboard-twitch").assertIsFocused(); assertEquals(6, selectedSection)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_watch_match)).assertIsFocused()
        assertEquals("Returning from Twitch to the hockey action must restore the hockey section", 3, selectedSection)
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        compose.runOnIdle { hockeyKnown = false; twitch = TwitchState.Live("Syntetisk direktsändning", 1234, "Counter-Strike 2") }
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        screenshot("v08-hockey-unknown-and-twitch-live-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("dashboard-twitch").assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_hockey_other_channels)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_companion_settings)).assertDoesNotExist()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(2, opened); assertEquals(0, watched); assertEquals(1, hockeyWatches)
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
        compose.onNodeWithTag("dashboard-series").assertIsFocused()
        screenshot("v08-discovery-series-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("dashboard-movies").assertIsFocused()
        screenshot("v08-discovery-movies-synthetic.png")
        compose.runOnIdle { rows = listOf(HomeSection.LIVE, HomeSection.SPORT, HomeSection.MOVIES, HomeSection.NOW, HomeSection.CONTINUE) }
        // runOnIdle waits BEFORE its mutation, not for the resulting new frame. Assert the
        // committed reordered screen before dispatching a key meant for that new layout.
        compose.onNodeWithTag("dashboard-movies").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN) // movies now precedes Now, regardless of stable section ID
        compose.onNodeWithText("Syntetiskt program 0").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-now").assertIsFocused()
        screenshot("v08-on-tv-now-synthetic.png")
        compose.runOnIdle { rows = rows - HomeSection.NOW }
        try {
            compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("dashboard-watch").assertIsFocused(); true }.getOrDefault(false) }
        } catch (error: Throwable) {
            screenshot("v08-dynamic-focus-failure-synthetic.png")
            compose.onAllNodes(isRoot()).printToLog("V08_SYNTHETIC_FOCUS")
            val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { it.config.toString() }
            throw AssertionError("Hero focus not restored after removing Now. Focused synthetic UI: $focused", error)
        }
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
        val releaseNotes = compose.onNodeWithText("Syntetiska testnyheter", substring = true)
        releaseNotes.assertExists()
        key(KeyEvent.KEYCODE_DPAD_UP)
        releaseNotes.assertIsFocused()
        val beforeScroll = releaseNotes.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN)
        val afterScroll = releaseNotes.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Remote must scroll authenticated notes", afterScroll > beforeScroll)
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
    @Test fun firstViewportShowsQuickTileInsideRealShellInsetsWithoutAllocatingVideo() {
        mount()
        val hero = compose.onNodeWithTag("dashboard-hero").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("dashboard-header").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("dashboard-status").fetchSemanticsNode().boundsInRoot
        val tile = compose.onNodeWithTag("dashboard-quick").fetchSemanticsNode().boundsInRoot
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val scale = screen.width / 1920f
        assertTrue("Compact hero, measured in mockup pixels: $hero", hero.height / scale in 270f..320f)
        assertTrue("Header remains above content", header.bottom <= hero.top)
        assertTrue("Header does not overlap status", header.right <= status.left || header.top >= status.bottom)
        assertTrue("An entire useful quick tile must be in the initial viewport", tile.bottom <= screen.height && tile.top >= hero.bottom && tile.height >= 100 * scale)
        val progress = compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag("dashboard-hero"))).fetchSemanticsNode().boundsInRoot
        assertTrue("Hero progress must remain concise", progress.width <= 461 * scale)
        compose.onNodeWithTag("dashboard-quick").assertIsDisplayed()
        assertEquals("Disabled preview must never compose its video slot", 0, videoMounts)
        screenshot("v08-review-home-1080p-focused-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("dashboard-version").assertIsFocused()
        screenshot("v08-review-home-1080p-unfocused-hero-synthetic.png")
        compose.runOnIdle { twitch = TwitchState.Live("Syntetisk direktsändning", 1234, "Counter-Strike 2") }
        compose.onNodeWithTag("dashboard-version").assertIsFocused()
        screenshot("v08-review-home-twitch-live-synthetic.png")
    }
    @Test fun overflowUsesRemoteGuideAndQualityActionsAndBackRestoresItsOrigin() {
        mount()
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFocused("Fler val")
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFocused("TV-guide")
        screenshot("v08-review-remote-overflow-synthetic.png")
        key(KeyEvent.KEYCODE_BACK)
        waitFocused("Fler val")
        key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, guideOpens); assertEquals(0, watched)
        focus("dashboard-watch")
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithText("Källa / kvalitet").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, variantOpens); assertEquals(0, watched)
    }
    @Test fun sidebarLongTextAndProfileArrivalKeepControlsReachableWithoutPlayback() {
        mount(); key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(5000) { sidebarFocused }
        screenshot("v08-review-sidebar-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        compose.runOnIdle {
            programmeTitle = "Syntetisk lång programtitel med flera ord och en utförlig beskrivning av kvällens sändning ".repeat(4)
            nextProgramme = "Syntetiskt nästa program med en mycket lång rubrik ".repeat(4)
            wideStatus = true
        }
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        compose.onNodeWithTag("dashboard-quick").assertIsDisplayed()
        val tile = compose.onNodeWithTag("dashboard-quick").fetchSemanticsNode().boundsInRoot
        assertTrue(tile.bottom <= InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().height)
        screenshot("v08-review-long-text-wide-status-synthetic.png")
        compose.runOnIdle { profileKey = 8L; profile = "Syntetisk profil två" }
        compose.onNodeWithText("Syntetisk profil två").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-watch").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        assertEquals(0, watched); assertEquals(0, videoMounts)
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
            screenshot("v08-review-home-720p-focused-synthetic.png")
            compose.onNodeWithTag("dashboard-quick").assertIsDisplayed()
            val tile = compose.onNodeWithTag("dashboard-quick").fetchSemanticsNode().boundsInRoot
            assertTrue("Quick tile remains visible at 720p", tile.bottom <= 720)
            assertEquals(0, videoMounts)
            compose.runOnIdle {
                programmeTitle = "Syntetisk lång programtitel med fördjupning, intervjuer och kvällens viktigaste händelser ".repeat(3)
                nextProgramme = "Syntetiskt nästa program med en lång rubrik ".repeat(3)
            }
            compose.onNodeWithTag("dashboard-watch").assertIsFocused()
            compose.onNodeWithTag("dashboard-quick").assertIsDisplayed()
            val longTile = compose.onNodeWithTag("dashboard-quick").fetchSemanticsNode().boundsInRoot
            assertTrue("Long text must retain a complete quick tile at 720p", longTile.bottom <= 720 && longTile.height >= 66)
            screenshot("v08-review-home-720p-long-text-synthetic.png")
            key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFocused("TV-guide")
            screenshot("v08-review-overflow-720p-synthetic.png")
            key(KeyEvent.KEYCODE_BACK); waitFocused("Fler val")
            key(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("dashboard-hockey").assertIsFocused()
        } finally { command("wm size reset"); compose.waitUntil(5000) { compose.activity.resources.configuration.screenWidthDp >= 800 } }
    }
}
