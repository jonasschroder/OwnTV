package tv.own.owntv.performance

import android.content.Intent
import android.net.TrafficStats
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.MainActivity
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode
import java.io.File
import java.util.Collections

/** Opt-in, actual production owners. Refuses physical devices and never runs in normal home suite. */
class PerformanceJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val db get() = GlobalContext.get().get<OwnTVDatabase>()
    private val settings get() = GlobalContext.get().get<SettingsRepository>()
    private val name = "Synthetic performance profile"
    private val profile = 99000L
    @Before fun disposableOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("performanceProbe"))
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())
        assertEquals("se.jonasschroder.mintv.qa", app.packageName)
        instrumentation.setInTouchMode(false)
    }
    private fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test fun seedControlledLibrary(): Unit = runBlocking {
        // Dedicated synthetic IDs, no deletion/reset. The host also refuses non-emulator serials.
        if (db.profileDao().getById(profile) != null) return@runBlocking
        db.profileDao().insert(ProfileEntity(id = profile, name = name, avatarColor = 1, createdAt = 99000))
        db.sourceDao().insert(SourceEntity(id = profile, name = "Synthetic performance source", type = SourceType.M3U,
            url = "http://127.0.0.1:9/performance.m3u", syncMovies = false, syncSeries = false))
        db.sourceDao().link(ProfileSourceCrossRef(profile, profile))
        val names = listOf("SVT1 HD", "SVT2 HD", "TV3 HD", "TV4 HD", "Kanal 5 HD", "TV6 HD", "Sjuan HD", "TV8 HD", "Kanal 9 HD", "TV10 HD", "TV4 Hockey HD") +
            (1..12).map { "TV4 Sport Live $it HD" }
        for (batch in (0 until 12000).chunked(400)) db.channelDao().insertAll(batch.map { index ->
            ChannelEntity(id = 9900000L + index, sourceId = profile,
                name = names.getOrNull(index) ?: "Synthetic other channel $index", remoteId = "perf-$index",
                streamUrl = "http://127.0.0.1:9/perf/$index", epgChannelId = "perf-$index")
        })
        db.favoriteDao().add(FavoriteEntity(profileId = profile, mediaType = MediaType.LIVE, itemId = 9900000))
        db.historyDao().record(WatchHistoryEntity(profileId = profile, mediaType = MediaType.LIVE, itemId = 9900000))
        settings.setActiveProfile(profile)
        settings.setStartupMode(profile, StartupMode.HOME)
        settings.setUpdateCheckOnStart(false)
        settings.setHeroPreviewEnabled(false)
        settings.setLivePreviewEnabled(false)
        app.getSharedPreferences("mintv-companion", 0).edit().putBoolean("shl-enabled", false).putBoolean("broadcast-enabled", false).commit()
    }

    private fun waitTag(tag: String) {
        try { compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            val dir = File(app.getExternalFilesDir(null), "performance").apply { mkdirs() }
            File(dir, "probe-failure-tree.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 8))
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(dir, "probe-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            throw error
        }
    }
    private fun chooseSyntheticProfile(profileName: String = name) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("top-HOME").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText(profileName).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("top-HOME").fetchSemanticsNodes().isNotEmpty()) return
        // The label intentionally sits outside the clickable avatar. Use its aligned remote target.
        val label = compose.onNodeWithText(profileName).fetchSemanticsNode().boundsInRoot
        val tile = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.bottom <= label.top && it.boundsInRoot.center.x in label.left..label.right }
            .minByOrNull { kotlin.math.abs(it.boundsInRoot.center.x - label.center.x) } ?: error("Synthetic profile avatar unavailable")
        compose.onNode(SemanticsMatcher("synthetic profile avatar") { it.id == tile.id }).performSemanticsAction(SemanticsActions.RequestFocus)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
    }
    private fun press(code: Int) {
        val down = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        compose.waitForIdle()
    }
    private fun focus(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus); compose.waitForIdle() }
    private fun ready(destination: String) {
        waitTag("top-$destination")
        compose.onNodeWithTag("top-$destination").assertIsSelected()
        when (destination) {
            "HOME" -> { waitTag("smart-channel-svt1"); waitTag("smart-hero-play") }
            "TV" -> waitTag("swedish-channel-svt1")
            "VOD" -> waitTag("vod-series-entry")
            "PROFILE" -> waitTag("settings-categories")
        }
    }
    private val workKinds = listOf("CATALOG", "GUIDE_SUMMARIES", "PUBLIC_HTTP")
    private fun readerWork(): List<Long>? = runCatching {
        val type = Class.forName("tv.own.owntv.features.catalog.NativeReadWork")
        @Suppress("UNCHECKED_CAST")
        (type.getMethod("snapshot").invoke(type.getField("INSTANCE").get(null)) as List<Long>)
    }.getOrNull()
    private val memorySummaries = mutableListOf<Map<String, String>>()
    private fun pss(): Int {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        memorySummaries += info.memoryStats
        return info.totalPss
    }

    @Test fun captureNativeProductionScreens() {
        val intent = app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            chooseSyntheticProfile(); ready("HOME")
            for (destination in listOf("HOME", "TV", "SPORT", "VOD", "PROFILE")) {
                focus("top-$destination"); press(KeyEvent.KEYCODE_DPAD_CENTER); ready(destination)
                capture(scenario, destination)
            }
            // Optional global route is present only in the v0.16 update workstream. The same
            // harness remains usable against the untouched baseline production APK.
            if (compose.onAllNodesWithTag("header-update").fetchSemanticsNodes().isNotEmpty()) {
                focus("header-update"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
                capture(scenario, "UPDATE")
                press(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("header-update").assertIsFocused()
            }
        }
    }

    private fun capture(scenario: ActivityScenario<MainActivity>, destination: String) {
                val drawn = java.util.concurrent.CountDownLatch(1)
                scenario.onActivity { activity -> activity.window.decorView.postOnAnimation {
                    activity.window.decorView.postOnAnimation { drawn.countDown() }
                } }
                assertTrue("Wait for real presentation frames", drawn.await(3, java.util.concurrent.TimeUnit.SECONDS))
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val dir = File(app.getExternalFilesDir(null), "performance/native").apply { mkdirs() }
                File(dir, "$destination-${bitmap.width}x${bitmap.height}.png").outputStream().use { output ->
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }; bitmap.recycle()
    }

    /** Isolates the visible horizontal row; the full journey also includes scrolling and idling. */
    @Test fun capturePopulatedNativeShell(): Unit = runBlocking {
        seedDesignReview(app, db, settings)
        val baseline = InstrumentationRegistry.getArguments().getString("reviewBaseline") == "true"
        val video = tv.own.owntv.home.LoopbackVideoFixture(instrumentation.context.assets.open("live-entry-bars.mp4").use { it.readBytes() })
        val hockeyId = 9900013L
        val original = db.channelDao().getById(hockeyId)!!
        db.channelDao().updateAll(listOf(original.copy(streamUrl = video.url(3))))
        try {
            for (size in listOf("1920x1080", "1280x720")) {
                shell("wm size $size")
                settings.setActiveProfile(profile)
                val intent = app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    chooseSyntheticProfile(); waitTag("smart-channel-svt1")
                    for (destination in listOf("HOME", "TV", "SPORT", "VOD", "PROFILE")) {
                        focus("top-$destination"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                        if (destination == "HOME") {
                            waitTag("smart-channel-svt1")
                            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Färjestad", substring = true)).fetchSemanticsNodes().isNotEmpty() }
                        } else ready(destination)
                        press(KeyEvent.KEYCODE_DPAD_DOWN)
                        if (destination == "TV") press(KeyEvent.KEYCODE_DPAD_DOWN)
                        capture(scenario, "POPULATED_$destination")
                    }
                    focus("header-update"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.waitUntil(5_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
                    capture(scenario, "POPULATED_UPDATE")
                    press(KeyEvent.KEYCODE_BACK); compose.onNodeWithTag("header-update").assertIsFocused()

                    focus("top-TV"); press(KeyEvent.KEYCODE_DPAD_CENTER); ready("TV")
                    focus("tv-category-GENERAL"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                    val lateChannelTag = if (size == "1920x1080") "swedish-channel-tv6" else "swedish-channel-svt1varmland"
                    val lateGuideKey = if (size == "1920x1080") "perf-5" else "perf-60"
                    waitTag(lateChannelTag); focus(lateChannelTag)
                    val guideBounds = compose.onNodeWithTag(lateChannelTag).getUnclippedBoundsInRoot()
                    val lateTitle = "Syntetiskt sent program med en lång svensk titel som fyller två rader"
                    db.epgDao().upsertProgrammes(listOf(EpgProgrammeEntity(sourceId = profile, epgChannelId = lateGuideKey,
                        startMs = System.currentTimeMillis() - 60_000, stopMs = System.currentTimeMillis() + 3_600_000, title = lateTitle)))
                    compose.waitUntil(10_000) { compose.onAllNodesWithText(lateTitle).fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag(lateChannelTag).assertIsFocused()
                    if (!baseline) assertEquals(guideBounds, compose.onNodeWithTag(lateChannelTag).getUnclippedBoundsInRoot())
                    capture(scenario, "TV_LATE_GUIDE")
                    focus("tv-category-OTHER"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                    press(KeyEvent.KEYCODE_DPAD_DOWN)
                    repeat(6) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
                    val channelMatcher = SemanticsMatcher("logical TV channel") {
                        it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) &&
                            it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("swedish-channel-")
                    }
                    val focused = compose.onNode(isFocused() and channelMatcher)
                    focused.assertIsDisplayed()
                    val tag = focused.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
                    capture(scenario, "TV_32_CHANNELS")
                    val down = SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    Thread.sleep(650)
                    instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    waitTag("source-picker"); capture(scenario, "SOURCE_PICKER")
                    press(KeyEvent.KEYCODE_BACK)
                    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(tag).assertIsFocused(); true }.getOrDefault(false) }

                    focus("header-epg-refresh"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.onNodeWithTag("header-epg-refresh").assertIsFocused()
                    focus("top-SPORT"); press(KeyEvent.KEYCODE_DPAD_CENTER); ready("SPORT")
                    press(KeyEvent.KEYCODE_DPAD_DOWN)
                    val hockeyLabels = listOf("TV4 Hockey HD", "TV4 Sport Live 1 HD", "TV4 Sport Live 2 HD", "TV4 Sport Live 3 HD")
                    fun hockeyCard(label: String) = compose.onNode(hasText(label) and isFocusable())
                    fun focusedSportChannel(): Int = hockeyLabels.indexOfFirst {
                        runCatching { hockeyCard(it).assertIsFocused(); true }.getOrDefault(false)
                    }
                    repeat(5) { if (focusedSportChannel() < 0) press(KeyEvent.KEYCODE_DPAD_DOWN) }
                    val channelEntry = focusedSportChannel()
                    check(channelEntry == 0 || channelEntry == 3) { "Unexpected synthetic Sport entry: $channelEntry" }
                    if (channelEntry != 3) repeat(3) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
                    hockeyCard(hockeyLabels[3]).assertIsFocused()
                    lateinit var vm: tv.own.owntv.features.live.LiveViewModel
                    scenario.onActivity { activity ->
                        vm = androidx.lifecycle.ViewModelProvider(activity, object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                GlobalContext.get().get<tv.own.owntv.features.live.LiveViewModel>() as T
                        })[tv.own.owntv.features.live.LiveViewModel::class.java]
                    }
                    press(KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("navigation-global").fetchSemanticsNodes().isEmpty() }
                    compose.waitUntil(15_000) { vm.previewEngine.state.value == tv.own.owntv.player.LivePreviewEngine.State.PLAYING }
                    capture(scenario, "SPORT_LOOPBACK_PLAYBACK")
                    press(KeyEvent.KEYCODE_BACK) // The existing visible HUD is the first finite Back level.
                    if (compose.onAllNodesWithTag("navigation-global").fetchSemanticsNodes().isEmpty()) press(KeyEvent.KEYCODE_BACK)
                    waitTag("top-SPORT")
                    try {
                        compose.waitUntil(5_000) { runCatching { hockeyCard(hockeyLabels[3]).assertIsFocused(); true }.getOrDefault(false) }
                    } catch (error: Throwable) {
                        val dir = File(app.getExternalFilesDir(null), "performance/native").apply { mkdirs() }
                        File(dir, "SPORT_RETURN-${size}-focus.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 12))
                        capture(scenario, "SPORT_RETURN_FAILURE")
                        if (!baseline) throw error // Baseline collection records the defect; candidate/CI must pass.
                    }
                    if (!baseline) {
                        focus("sport-hero-action"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                        // The explicit fallback must open the existing fixture-scoped manual picker.
                        waitTag("sport-manual-picker")
                        capture(scenario, "SPORT_MANUAL_PICKER")
                        press(KeyEvent.KEYCODE_BACK)
                    }
                    settings.setActiveProfile(99001)
                    compose.waitUntil(15_000) { vm.homeLibraryContext.value.first == 99001L }
                    assertTrue(vm.homeLibraryContext.value.second.isEmpty())
                    compose.waitUntil(5_000) { compose.onAllNodesWithText("Synthetic empty profile").fetchSemanticsNodes().isNotEmpty() }
                    chooseSyntheticProfile("Synthetic empty profile")
                    waitTag("top-HOME")
                    focus("top-HOME"); press(KeyEvent.KEYCODE_DPAD_CENTER)
                    compose.onNodeWithTag("smart-channel-svt1").assertDoesNotExist()
                    compose.onNodeWithTag("sport-channel-$hockeyId").assertDoesNotExist()
                    settings.setActiveProfile(profile)
                    compose.waitUntil(5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
                    chooseSyntheticProfile(); waitTag("top-HOME")
                }
            }
        } finally {
            video.close()
            db.channelDao().updateAll(listOf(original))
            shell("wm size reset")
        }
    }

    @Test fun visibleSportRowProbe() {
        val label = InstrumentationRegistry.getArguments().getString("perfLabel", "unlabelled")!!
        require(label.matches(Regex("[a-z0-9-]{1,60}")))
        val intent = app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!
        ActivityScenario.launch<MainActivity>(intent).use {
            chooseSyntheticProfile(); ready("HOME")
            focus("top-SPORT"); press(KeyEvent.KEYCODE_DPAD_CENTER); ready("SPORT")
            val firstCard = compose.onNode(hasText("TV4 Hockey HD") and isFocusable())
            val secondCard = compose.onNode(hasText("TV4 Sport Live 1 HD") and isFocusable())
            compose.waitUntil(5_000) { runCatching { firstCard.assertExists(); secondCard.assertExists(); true }.getOrDefault(false) }
            firstCard.performSemanticsAction(SemanticsActions.RequestFocus); compose.waitForIdle()
            val samples = mutableListOf<Double>()
            repeat(42) { iteration ->
                val at = SystemClock.elapsedRealtimeNanos()
                press(KeyEvent.KEYCODE_DPAD_RIGHT); secondCard.assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_LEFT); firstCard.assertIsFocused()
                if (iteration >= 2) samples += (SystemClock.elapsedRealtimeNanos() - at) / 1e6
            }
            val result = org.json.JSONObject().put("label", label).put("dataset", "synthetic-12000-channel-v1")
                .put("measurement", "visible_sport_right_left_ms").put("samples_ms", org.json.JSONArray(samples))
            val dir = File(app.getExternalFilesDir(null), "performance").apply { mkdirs() }
            File(dir, "$label.json").writeText(result.toString(2))
        }
    }

    @Test fun repeatedProductionNavigation(): Unit = runBlocking {
        assertNotNull(db.profileDao().getById(profile))
        val label = InstrumentationRegistry.getArguments().getString("perfLabel", "unlabelled")!!
        require(label.matches(Regex("[a-z0-9-]{1,60}")))
        val samples = mutableMapOf<String, MutableList<Double>>()
        val memory = mutableListOf<Int>()
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        var lostCallbacks = 0L
        val worker = HandlerThread("MinTV-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metric, lost ->
            frames.add(metric.getMetric(FrameMetrics.TOTAL_DURATION)); lostCallbacks += lost
        }
        val workBefore = readerWork()
        val runtimeBefore = Debug.getRuntimeStats()
        val cpuBefore = Process.getElapsedCpuTime()
        val rxBefore = TrafficStats.getUidRxBytes(Process.myUid())
        val txBefore = TrafficStats.getUidTxBytes(Process.myUid())
        fun measure(key: String, block: () -> Unit) {
            val at = SystemClock.elapsedRealtimeNanos(); block(); compose.waitForIdle()
            samples.getOrPut(key) { mutableListOf() }.add((SystemClock.elapsedRealtimeNanos() - at) / 1e6)
        }
        val launchAt = SystemClock.elapsedRealtimeNanos()
        val intent = app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            chooseSyntheticProfile()
            ready("HOME")
            samples.getOrPut("instrumented_launch_to_useful_home_ms") { mutableListOf() }.add((SystemClock.elapsedRealtimeNanos() - launchAt) / 1e6)
            scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
            memory += pss()
            // Two unreported warmups, then 20 identical cycles. No provider playback or fabricated TTFF.
            repeat(22) { iteration ->
                for (destination in listOf("TV", "SPORT", "VOD", "PROFILE", "HOME")) {
                    focus("top-$destination")
                    val action = { press(KeyEvent.KEYCODE_DPAD_CENTER); ready(destination) }
                    if (iteration < 2) action() else measure("enter_${destination.lowercase()}_ms", action)
                    if (destination == "VOD") {
                        focus("vod-movies-entry")
                        val content: () -> Unit = {
                            press(KeyEvent.KEYCODE_DPAD_DOWN); compose.onNodeWithTag("vod-series-entry").assertIsFocused()
                            press(KeyEvent.KEYCODE_DPAD_UP); compose.onNodeWithTag("vod-movies-entry").assertIsFocused()
                        }
                        if (iteration < 2) content() else measure("vod_content_down_up_ms", content)
                    }
                }
                focus("top-HOME")
                val dpad: () -> Unit = { press(KeyEvent.KEYCODE_DPAD_RIGHT); compose.onNodeWithTag("top-TV").assertIsFocused(); press(KeyEvent.KEYCODE_DPAD_LEFT); compose.onNodeWithTag("top-HOME").assertIsFocused() }
                if (iteration < 2) dpad() else measure("dpad_right_left_ms", dpad)
                if (iteration % 5 == 0) memory += pss()
            }
            repeat(10) {
                measure("background_resume_ms") {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    ready("HOME")
                }
            }
            memory += pss()
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
        }
        worker.quitSafely(); worker.join(2_000)
        val rxAfter = TrafficStats.getUidRxBytes(Process.myUid())
        val txAfter = TrafficStats.getUidTxBytes(Process.myUid())
        val root = JSONObject().put("label", label).put("dataset", "synthetic-12000-channel-v1")
            .put("package", app.packageName).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("measurements_ms", JSONObject().apply { samples.forEach { (k,v) -> put(k, JSONArray(v)) } })
            .put("pss_kib_samples", JSONArray(memory)).put("cpu_process_ms", Process.getElapsedCpuTime() - cpuBefore)
            .put("memory_summary_samples", JSONArray(memorySummaries.map { JSONObject(it) }))
            .put("art_runtime_before", JSONObject(runtimeBefore)).put("art_runtime_after", JSONObject(Debug.getRuntimeStats()))
            .put("app_reader_work_delta", JSONObject().apply {
                val after = readerWork()
                if (workBefore != null && after != null) after.forEachIndexed { i, count -> put(workKinds[i], count - workBefore[i]) }
                else put("instrumented", false)
            })
            .put("network_rx_bytes", if (rxBefore >= 0 && rxAfter >= rxBefore) rxAfter - rxBefore else JSONObject.NULL)
            .put("network_tx_bytes", if (txBefore >= 0 && txAfter >= txBefore) txAfter - txBefore else JSONObject.NULL)
            .put("frame_duration_ns", JSONArray(frames.toList())).put("frame_callback_drops", lostCallbacks)
            .put("unmeasured", JSONArray(listOf("real_provider_first_video_frame", "channel_zap_first_frame", "SVT_Play_Telenor_Twitch_return", "physical_peak_pss")))
        val directory = File(app.getExternalFilesDir(null), "performance").apply { mkdirs() }
        File(directory, "$label.json").writeText(root.toString())
        assertEquals(profile, settings.activeProfileIdNow())
        assertEquals(12000, db.channelDao().countForSourceOnce(profile))
    }
}
