package tv.own.owntv.home

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.MainActivity
import tv.own.owntv.R
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.features.home.SportsPreferences
import tv.own.owntv.features.home.SportPreferences
import java.io.File

/** Production Application/Koin/database/Activity/shell/Home. Never supplies setContent or fake VMs. */
class ProductionStartupTest {
    // Queued test frames prevent reentrant measure/layout during real Activity recreation.
    @get:Rule val compose = createEmptyComposeRule()
    /** Both production-shell journeys run at 1080p/720p; assert the actual composed hierarchy. */
    private fun assertNavigationLevels(contextual: Boolean) {
        compose.onAllNodesWithTag("navigation-global").assertCountEquals(1)
        compose.onAllNodesWithTag("navigation-context").assertCountEquals(if (contextual) 1 else 0)
        for (tag in listOf("header-all-channels", "header-collection", "header-library-history", "header-downloads", "nav-context-search", "sub-LIVE_TV", "sub-EPG"))
            compose.onNodeWithTag(tag).assertDoesNotExist()
        val bar = compose.onNodeWithTag("navigation-global").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("header-epg-refresh", "header-guide", "header-search")) {
            compose.onNodeWithTag(tag).assertIsDisplayed().assert(hasAnyAncestor(hasTestTag("navigation-global")))
            val action = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("Utility must remain inside the existing top bar: $tag", action.top >= bar.top && action.bottom <= bar.bottom)
        }
    }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val db get() = GlobalContext.get().get<OwnTVDatabase>()
    private val settings get() = GlobalContext.get().get<SettingsRepository>()
    private val profileName = "Persisted QA profile"

    @Before fun disposableQaOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        assertEquals("1", process.inputStream.bufferedReader().use { it.readText().trim() })
        assertEquals(0, process.waitFor())
        assertEquals("se.jonasschroder.mintv.qa", app.packageName)
        instrumentation.setInTouchMode(false)
    }

    @Test fun seedPersistedQaData(): Unit = runBlocking {
        assertTrue("Fresh disposable emulator required", db.profileDao().getAllOnce().isEmpty())
        db.profileDao().insert(ProfileEntity(id = 7, name = profileName, avatarColor = 1, createdAt = 777))
        db.profileDao().insert(ProfileEntity(id = 8, name = "Other persisted profile", avatarColor = 2, createdAt = 888))
        for (source in listOf(101L, 102L)) {
            db.sourceDao().insert(SourceEntity(id = source, name = "Synthetic source $source", type = SourceType.M3U,
                url = "http://127.0.0.1:9/synthetic.m3u", lastSyncAt = System.currentTimeMillis(), syncMovies = false, syncSeries = false))
            db.sourceDao().link(ProfileSourceCrossRef(7, source))
            settings.setPlaylistAutoRefresh(source, PlaylistRefresh(PlaylistAutoRefresh.OFF))
            db.channelDao().insertAll((1L..300L).map { index -> ChannelEntity(id = source * 1000 + index,
                sourceId = source, name = listOf("SVT1 HD", "SVT2 SD", "TV4 FHD", "TV4 HD")[index.toInt() % 4],
                remoteId = "synthetic-$index", streamUrl = "http://127.0.0.1:9/channel/$index", epgChannelId = "missing-guide-$index") })
        }
        db.sourceDao().link(ProfileSourceCrossRef(8, 102))
        db.favoriteDao().add(FavoriteEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 101001))
        db.historyDao().record(WatchHistoryEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 101001))
        db.historyDao().record(WatchHistoryEntity(profileId = 8, mediaType = MediaType.LIVE, itemId = 102001))
        GlobalContext.get().get<SportsPreferences>().save(7, SportPreferences(listOf("hockey:karlskoga", "football:degerfors-if"), hideScores = true))
        settings.setActiveProfile(7)
        settings.setStartupMode(7, StartupMode.HOME)
        settings.setUpdateCheckOnStart(false)
        settings.setHeroPreviewEnabled(false)
        settings.setLivePreviewEnabled(false)
        settings.setEpgOffsetMinutes(60)
        assertTrue(app.getSharedPreferences("mintv-home-sections", Context.MODE_PRIVATE).edit()
            .putString("profile:7", """{"owner":777,"hidden":["MOVIES"]}""").commit())
        assertTrue(app.getSharedPreferences("startup-fixture", Context.MODE_PRIVATE).edit().putBoolean("seeded", true).commit())
    }

    /** Additional fixture, after the original cold-start regression. All records stay local. */
    @Test fun populatePremiumHomeFixture(): Unit = runBlocking {
        assertTrue(app.getSharedPreferences("startup-fixture", Context.MODE_PRIVATE).getBoolean("seeded", false))
        val now = System.currentTimeMillis()
        val names = listOf("SVT1 HD", "SVT2 SD", "TV3 HD", "TV4 HD", "Kanal 5 HD", "TV6 HD", "Sjuan HD", "TV8 HD", "Kanal 9 HD", "TV10 HD")
        val pictures = File(app.filesDir, "synthetic-logos").apply { mkdirs() }
        for ((index, name) in names.withIndex()) {
            val file = File(pictures, "$index.png")
            val bitmap = android.graphics.Bitmap.createBitmap(160, 100, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; textSize = 35f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            canvas.drawText(name.substringBefore(" HD").substringBefore(" SD").substringBefore(" FHD").take(6), 8f, 65f, paint)
            file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            for (source in listOf(101L, 102L)) {
                val id = source * 1000 + index + 1
                val original = db.channelDao().getById(id)!!
                db.channelDao().updateAll(listOf(original.copy(name = name,
                    logoUrl = if (index == 5) null else android.net.Uri.fromFile(file).toString())))
                if (source == 101L) db.favoriteDao().add(FavoriteEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = id))
            }
            // The persisted global +60 minute offset is deliberately retained and exercised.
            if (index != 5) db.epgDao().upsertProgrammes(listOf(
                EpgProgrammeEntity(sourceId = 101, epgChannelId = "missing-guide-${index + 1}",
                    startMs = now - 75 * 60_000, stopMs = now - 15 * 60_000,
                    title = if (index == 0) "Syntetiska Nyheterna – fördjupning och intervjuer från hela Sverige" else "Syntetiskt program ${index + 1}"),
                EpgProgrammeEntity(sourceId = 101, epgChannelId = "missing-guide-${index + 1}",
                    startMs = now - 15 * 60_000, stopMs = now + 45 * 60_000, title = "Syntetiskt nästa program ${index + 1}")))
        }
        val tv3 = db.channelDao().getById(101003)!!
        db.channelDao().insertAll(listOf(
            tv3.copy(id = 7001, remoteId = "synthetic-m3-fhd", name = "TV3 FHD"),
            tv3.copy(id = 7002, remoteId = "synthetic-m3-uhd", name = "TV3 UHD")))
        val source = db.sourceDao().getById(101)!!
        db.sourceDao().update(source.copy(syncMovies = true, syncSeries = true))
        db.movieDao().insertAll(listOf(
            MovieEntity(id = 5001, sourceId = 101, name = "Syntetisk film — sparad position", streamUrl = "http://127.0.0.1:9/movie/1"),
            MovieEntity(id = 5002, sourceId = 101, name = "Syntetisk andra film", streamUrl = "http://127.0.0.1:9/movie/2")))
        db.seriesDao().insertSeries(listOf(SeriesEntity(id = 6001, sourceId = 101, name = "Syntetisk serie")))
        db.seriesDao().upsertEpisodes(listOf(EpisodeEntity(id = 6002, seriesId = 6001, seasonNumber = 1, episodeNumber = 3,
            name = "Syntetiskt sparat avsnitt", streamUrl = "http://127.0.0.1:9/episode/3")))
        for ((index, item) in listOf(MediaType.EPISODE to 6002L, MediaType.MOVIE to 5001L, MediaType.MOVIE to 5002L).withIndex())
            db.progressDao().save(PlaybackProgressEntity(profileId = 7, mediaType = item.first, itemId = item.second,
                positionMs = (18 + index) * 60_000L, durationMs = 48 * 60_000L, updatedAt = now - 3_600_000 - index))
        // Keep the fixture's recent-TV baseline even when refreshed hours after initial seeding.
        db.historyDao().record(WatchHistoryEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 101001))
    }

    @Test fun liveEntryMatrixKeepsRealDecoderAliveAcrossFullscreenDisposal(): Unit = runBlocking {
        fun pressRemote(code: Int) {
            compose.waitForIdle()
            // A physical press has one downTime for both events. The logical short/long-OK
            // controller intentionally rejects an unrelated KeyUp (unlike a generic Button).
            val down = android.os.SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
            instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
            compose.waitForIdle()
        }
        val store = GlobalContext.get().get<tv.own.owntv.features.live.SwedishChannelStore>()
        val choices = store.choices(7)
        val source = SourceEntity(id = 99014, name = "Synthetic decoder fixture", type = SourceType.M3U,
            url = "http://127.0.0.1:9/unavailable.m3u", lastSyncAt = System.currentTimeMillis(),
            syncMovies = false, syncSeries = false, liveEnginePreference = "EXO_FIRST")
        db.sourceDao().insert(source); db.sourceDao().link(ProfileSourceCrossRef(7, source.id))
        settings.setPlaylistAutoRefresh(source.id, PlaylistRefresh(PlaylistAutoRefresh.OFF))
        // No portal or credentials: null MAC resolves the local command through Core's real
        // asynchronous Stalker path, protecting acceptance from an early false rejection.
        val portal = source.copy(id = 99015, name = "Synthetic asynchronous fixture", type = SourceType.STALKER)
        db.sourceDao().insert(portal); db.sourceDao().link(ProfileSourceCrossRef(7, portal.id))
        settings.setPlaylistAutoRefresh(portal.id, PlaylistRefresh(PlaylistAutoRefresh.OFF))
        val channels = listOf("SVT1 HD", "SVT2 FHD", "TV4 HD").mapIndexed { index, name ->
            val file = File(app.filesDir, "v014-video-$index.mp4")
            instrumentation.context.assets.open("live-entry-bars.mp4").use { input -> file.outputStream().use(input::copyTo) }
            ChannelEntity(id = 9901401L + index, sourceId = if (index == 1) portal.id else source.id, remoteId = "v014-$index", name = name, streamUrl = file.toURI().toString())
        }
        db.channelDao().insertAll(channels)
        store.save(7, choices.copy(preferred = choices.preferred + ("svt1" to tv.own.owntv.core.customize.CustomizeKeys.channel(channels[0]))))
        val fixtureChoices = store.choices(7)
        assertEquals(channels[0].id, store.library(7, listOf(101, 102, source.id, portal.id), fixtureChoices)
            .groups.single { it.identity == "svt1" }.preferred(fixtureChoices.preferred["svt1"], fixtureChoices.showUhd)?.id)
        settings.setActiveProfile(7)
        try {
            ActivityScenario.launch<MainActivity>(app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!).use { scenario ->
                compose.waitUntil(20_000) { compose.onAllNodesWithText(profileName).fetchSemanticsNodes().isNotEmpty() }
                pressRemote(KeyEvent.KEYCODE_DPAD_CENTER)
                lateinit var vm: tv.own.owntv.features.live.LiveViewModel
                scenario.onActivity { activity ->
                    vm = androidx.lifecycle.ViewModelProvider(activity, object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            GlobalContext.get().get<tv.own.owntv.features.live.LiveViewModel>() as T
                    })[tv.own.owntv.features.live.LiveViewModel::class.java]
                }
                compose.waitUntil(20_000) { vm.homeLibraryContext.value.first == 7L && listOf(source.id, portal.id).all { it in vm.homeLibraryContext.value.second } }
                // Complete the shell's normal-entry reset and open its real video surface. Home
                // intentionally has no preview surface: VM dispatch there is not a fullscreen test.
                compose.waitUntil(30_000) { compose.onAllNodesWithTag("smart-hero-play").fetchSemanticsNodes().isNotEmpty() }
                fun entryEvidence(label: String) {
                    val data = "$label: channel=${vm.previewChannel.value?.id}; state=${vm.previewEngine.state.value}; onExo=${vm.liveOnExo.value}; notice=${vm.liveEntryNotice.value}; context=${vm.homeLibraryContext.value}; metadata=${vm.swedishRowContext.value?.profileId}\n" + tv.own.owntv.player.LiveEntryDiagnostics.trace.report()
                    // println goes to Android logcat, not the instrument -w result. Retain the
                    // bounded, synthetic-only context in the actual CI case output.
                    instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", data) })
                }
                fun awaitPlaying(label: String) {
                    try {
                        compose.waitUntil(10_000) { vm.previewEngine.state.value == tv.own.owntv.player.LivePreviewEngine.State.PLAYING }
                    } catch (failure: Throwable) {
                        entryEvidence("Decoder fixture $label")
                        throw failure
                    }
                }
                compose.onNodeWithTag("top-TV").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("swedish-channel-svt1").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("swedish-channel-svt1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.onNodeWithTag("swedish-channel-svt1").assertIsFocused()
                val beforeEntry = vm.swedishRowContext.value
                instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream",
                    "Entry preflight: authorized=${vm.homeChannel(channels[0].id, 7) != null}; metadataCurrent=${beforeEntry != null && vm.swedishContextCurrent(beforeEntry)}; activity=${scenario.state}") })
                pressRemote(KeyEvent.KEYCODE_DPAD_CENTER)
                try {
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("navigation-global").fetchSemanticsNodes().isEmpty() }
                } catch (failure: Throwable) {
                    entryEvidence("TV entry fixture")
                    val failed = instrumentation.uiAutomation.takeScreenshot()
                    val directory = File(app.getExternalFilesDir(null), "startup-screenshots").apply { mkdirs() }
                    File(directory, "v014-entry-failed.png").outputStream().use { failed.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; failed.recycle()
                    throw failure
                }
                awaitPlaying("TV grid disposal")
                assertEquals(channels[0].id, vm.previewChannel.value?.id)
                // Wait real frames past disposal, not just synchronous engine-command acknowledgement.
                Thread.sleep(700)
                assertEquals(tv.own.owntv.player.LivePreviewEngine.State.PLAYING, vm.previewEngine.state.value)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("Synthetic moving video must render after real TV grid disposal", (bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) and 0x00ffffff) != 0)
                val images = File(app.getExternalFilesDir(null), "startup-screenshots").apply { mkdirs() }
                File(images, "v014-svt1-entry-${bitmap.width}x${bitmap.height}.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
                for (origin in tv.own.owntv.player.LiveEntryOrigin.entries) for (channel in channels) {
                    val result = kotlinx.coroutines.withContext(Dispatchers.Main) {
                        vm.requestLivePlayback(channel.id, channels, origin, channel)
                    }
                    assertEquals(origin.name, tv.own.owntv.features.live.LiveViewModel.HomePlayback.IN_APP, result)
                    kotlinx.coroutines.withContext(Dispatchers.Main) { vm.stopBrowsePreview() }
                    awaitPlaying("${origin.name}/${channel.id}")
                    assertEquals(channel.id, vm.previewChannel.value?.id)
                    assertEquals(channel.streamUrl, vm.previewEngine.currentUrl)
                }
                pressRemote(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("navigation-global").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("top-HOME").performClick()
                val replay = kotlinx.coroutines.withContext(Dispatchers.Main) { vm.playHomeChannel(channels[1].id, channels, channels[1]) }
                assertEquals(tv.own.owntv.features.live.LiveViewModel.HomePlayback.IN_APP, replay)
                // A direct Home VM call tests acceptance; there is deliberately no video surface
                // until its UI receives IN_APP and navigates. The fullscreen matrix above tests frames.
                kotlinx.coroutines.withContext(Dispatchers.Main) { vm.stopPreview() }
            }
        } finally { store.save(7, choices); db.sourceDao().delete(source); db.sourceDao().delete(portal); channels.indices.forEach { File(app.filesDir, "v014-video-$it.mp4").delete() } }
    }

    @Test fun selectSwedishLocale(): Unit = runBlocking { assertTrue(LocaleStore.from(app).set("sv")) }

    @Test fun productionHomeSurvivesPersistedColdStartAndActivityRecreation(): Unit = runBlocking {
        assertTrue(app.getSharedPreferences("startup-fixture", Context.MODE_PRIVATE).getBoolean("seeded", false))
        val selectedProfile = InstrumentationRegistry.getArguments().getString("startupProfile", "7")!!.toLong()
        val intent = app.packageManager.getLeanbackLaunchIntentForPackage(app.packageName)!!
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Choose the actual first profile through the production remote-driven gate.
            compose.waitUntil(20_000) { compose.onAllNodesWithText(profileName).fetchSemanticsNodes().isNotEmpty() }
            if (selectedProfile == 8L) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            // Instrumentation's target Context keeps the device locale; production Activity wraps
            // its own resources with the persisted app locale. Assert against the latter.
            var homeLabel = ""
            scenario.onActivity { activity ->
                homeLabel = activity.getString(R.string.mintv_smart_back_channel)
                if (LocaleStore.from(activity).readBlocking() == "sv") {
                    assertEquals("sv", activity.resources.configuration.locales[0].language)
                }
                println("Startup assertion locale=${activity.resources.configuration.locales[0].language}; instrumentation locale=${app.resources.configuration.locales[0].language}")
            }
            compose.waitUntil(30_000) { compose.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(8_000) // Real elapsed survival beyond the reported one-second fatal window.
            compose.onNodeWithText(homeLabel).assertIsDisplayed()
            compose.onNodeWithTag("smart-hero-play").assertIsDisplayed()
            val premium = InstrumentationRegistry.getArguments().getString("premiumHome") == "true"
            if (premium) {
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText("Syntetiska Nyheterna", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            }
            compose.waitForIdle()
            val drawn = java.util.concurrent.CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.window.decorView.postOnAnimation {
                    activity.window.decorView.postOnAnimation { drawn.countDown() }
                }
            }
            assertTrue("Wait for actual production screenshot frames", drawn.await(3, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val pictures = File(app.getExternalFilesDir(null), "startup-screenshots").apply { mkdirs() }
            File(pictures, "${if (premium) "premium" else "home"}-${bitmap.width}x${bitmap.height}-${java.util.Locale.getDefault().language}-profile-$selectedProfile.png")
                .outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            fun nativeCapture(name: String) {
                compose.waitForIdle(); instrumentation.waitForIdleSync()
                val frames = java.util.concurrent.CountDownLatch(1)
                scenario.onActivity { activity ->
                    activity.window.decorView.postOnAnimation {
                        activity.window.decorView.postOnAnimation { frames.countDown() }
                    }
                }
                assertTrue("Native capture waits for rendered frames", frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                val native = instrumentation.uiAutomation.takeScreenshot()
                File(pictures, "v010-$name-${native.width}x${native.height}.png").outputStream().use {
                    assertTrue(native.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                native.recycle()
            }
            if (premium) nativeCapture("home")

            if (premium) {
                fun press(code: Int) {
                    compose.waitForIdle()
                    val down = android.os.SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
                    instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
                    compose.waitForIdle()
                }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("smart-channel-svt1").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("smart-hero-play").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.onNodeWithTag("smart-hero-play").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.onNodeWithTag("smart-channel-svt1").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_RIGHT); press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("smart-channel-tv3").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_UP); press(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("top-HOME").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_DOWN); press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.onNodeWithTag("smart-channel-tv3").assertIsFocused()
            }
            if (premium) {
                // Real application owners and persisted data, not a replacement setContent fixture.
                fun press(code: Int) {
                    compose.waitForIdle()
                    val down = android.os.SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, code, 0))
                    instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
                    compose.waitForIdle()
                }
                compose.onNodeWithTag("top-HOME").assertExists()
                // Home overrides Compose density; compare raw root pixels, never unrelated dp units.
                val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
                for (key in listOf("movie:101:5001", "movie:101:5002", "episode:101:6001:6002")) {
                    val node = compose.onNodeWithTag("smart-resume-$key").fetchSemanticsNode()
                    val top = node.positionInRoot.y
                    val bottom = top + node.size.height
                    assertTrue("All three full resume cards fit above the fold: $top..$bottom in $viewport",
                        top >= viewport.top && bottom <= viewport.bottom)
                }
                compose.onNodeWithTag("twitch-app-entry").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("external-se.svt.android.svtplay").assertIsFocused(); true }.getOrDefault(false) }
                press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("external-se.telenor.stream").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("twitch-app-entry").assertIsFocused()
                assertNavigationLevels(contextual = false)
                nativeCapture("home-restored")
                val sourceStore = GlobalContext.get().get<tv.own.owntv.features.live.SwedishChannelStore>()
                val originalChoices = sourceStore.choices(7)
                fun openSources(tag: String) {
                    compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                    compose.onNodeWithTag(tag).assertIsFocused()
                    val down = android.os.SystemClock.uptimeMillis()
                    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    Thread.sleep(650)
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("source-picker").fetchSemanticsNodes().isNotEmpty() }
                    instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 3))
                    instrumentation.sendKeySync(KeyEvent(down, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    compose.waitForIdle()
                }
                openSources("smart-channel-tv3")
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("source-variant-7002").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(originalChoices, sourceStore.choices(7)) // held OK/release never save/play
                nativeCapture("source-picker")
                compose.onNodeWithTag("source-variant-7002").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("source-save-only").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_CENTER)
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("source-picker").fetchSemanticsNodes().isEmpty() }
                assertEquals("101:synthetic-m3-uhd", sourceStore.choices(7).preferred["tv3"])
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("smart-channel-tv3").assertIsFocused(); true }.getOrDefault(false) }
                openSources("smart-channel-tv3")
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("source-variant-7002").assertIsFocused(); true }.getOrDefault(false) }
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("top-TV").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("swedish-channel-tv3").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("swedish-channel-tv3").assert(hasText("UHD"))
                openSources("swedish-channel-tv3")
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("source-variant-7002").assertIsFocused(); true }.getOrDefault(false) }
                nativeCapture("source-picker-tv-saved")
                press(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("swedish-channel-tv3").assertIsFocused(); true }.getOrDefault(false) }
                sourceStore.save(7, originalChoices) // Restore only this disposable fixture's preferences.
                compose.onNodeWithTag("top-HOME").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty() }

                val originalProgress = db.progressDao().get(7, MediaType.EPISODE, 6002)!!
                compose.onNodeWithTag("top-TV").performClick()
                db.progressDao().save(originalProgress.copy(updatedAt = System.currentTimeMillis()))
                compose.onNodeWithTag("top-HOME").performClick()
                val episodeHero = hasText("Syntetiskt sparat avsnitt") and hasAnyAncestor(hasTestTag("smart-hero"))
                compose.waitUntil(10_000) { compose.onAllNodes(episodeHero).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("smart-hero").assert(hasAnyDescendant(hasText("Syntetiskt sparat avsnitt")))
                val storedGuide = hasText("Syntetiska Nyheterna", substring = true) and hasTestTag("smart-channel-svt1")
                compose.waitUntil(10_000) { compose.onAllNodes(storedGuide).fetchSemanticsNodes().isNotEmpty() }
                nativeCapture("smart-active-series")
                openSources("smart-last-live")
                press(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("smart-last-live").assertIsFocused(); true }.getOrDefault(false) }
                assertEquals(originalChoices, sourceStore.choices(7))
                db.progressDao().save(originalProgress)
                compose.onNodeWithTag("top-TV").performClick()
                compose.onNodeWithTag("top-HOME").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("top-TV").performClick()
                compose.onNodeWithTag("top-TV").assertIsSelected()
                compose.onNodeWithTag("sub-LIVE_TV").assertDoesNotExist()
                compose.onNodeWithTag("sub-EPG").assertDoesNotExist()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("swedish-channel-svt1").fetchSemanticsNodes().isNotEmpty() }
                assertNavigationLevels(contextual = true)
                compose.onNodeWithTag("tv-category-GENERAL").assertExists()
                compose.onNodeWithTag("tv-category-SPORT").assertExists()
                compose.onNodeWithTag("tv-category-OTHER").assertExists()
                // Explicit bookmark: earlier source tests may have restored TV3.
                compose.onNodeWithTag("swedish-channel-svt1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.onNodeWithTag("swedish-channel-svt1").assertIsFocused()
                compose.onNodeWithTag("top-TV").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv-category-GENERAL").assertIsFocused(); true }.getOrDefault(false) }
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("swedish-channel-svt1").assertIsFocused(); true }.getOrDefault(false) }
                press(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("tv-category-GENERAL").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("top-TV").assertIsFocused()
                // A focused different destination activates directly into its sole submenu.
                compose.onNodeWithTag("top-HOME").performClick()
                compose.onNodeWithTag("top-HOME").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("top-HOME").assertIsSelected()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv-category-GENERAL").assertIsFocused(); true }.getOrDefault(false) }
                compose.onNodeWithTag("top-TV").assertIsSelected()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                // Fresh entry uses available collection order; the exact in-page bookmark is tested above.
                val channelCard = SemanticsMatcher("logical channel card") {
                    it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("swedish-channel-") == true
                }
                compose.waitUntil(5_000) { compose.onAllNodes(isFocused() and channelCard).fetchSemanticsNodes().size == 1 }
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("tv-category-GENERAL").assertIsFocused()
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("top-TV").assertIsFocused()
                nativeCapture("tv")
                compose.onNodeWithTag("top-PROFILE").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("header-epg-refresh").assertIsFocused(); true }.getOrDefault(false) }
                compose.onNodeWithTag("header-guide").performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("top-TV").assertIsSelected()
                nativeCapture("guide")
                val livePreference = settings.liveView.first()
                settings.setLiveView(SettingsRepository.LiveView.GUIDE)
                compose.onNodeWithTag("top-HOME").performClick()
                compose.onNodeWithTag("top-TV").performClick()
                // The explicit EPG subview is remembered; LIVE's guide-first preference is also retained.
                compose.onNodeWithTag("header-guide").performClick()
                var nowLabel = ""
                scenario.onActivity { nowLabel = it.getString(R.string.content_epg_now_short) }
                compose.waitUntil(5_000) { compose.onAllNodesWithText(nowLabel).fetchSemanticsNodes().isNotEmpty() }
                assertEquals(SettingsRepository.LiveView.GUIDE, settings.liveView.first())
                settings.setLiveView(livePreference)
                compose.onNodeWithTag("top-TV").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.waitForIdle()
                compose.onNodeWithTag("top-TV").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_RIGHT)
                compose.onNodeWithTag("top-SPORT").assertIsFocused()
                compose.onNodeWithTag("top-TV").assertIsSelected()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitForIdle()
                compose.onNodeWithTag("top-SPORT").assertIsFocused()
                compose.onNodeWithTag("top-SPORT").assertIsSelected()
                assertNavigationLevels(contextual = false)
                nativeCapture("sport")
                compose.onNodeWithTag("top-VOD").performClick()
                compose.onNodeWithTag("sub-MOVIES").assertExists()
                compose.onNodeWithTag("sub-SERIES").performClick()
                compose.onNodeWithTag("sub-SERIES").assertIsSelected()
                compose.onNodeWithTag("top-HOME").performClick()
                compose.onNodeWithTag("top-HOME").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.waitForIdle()
                compose.onNodeWithTag("top-HOME").assertIsFocused()
                for (destination in listOf("TV", "SPORT", "VOD")) {
                    press(KeyEvent.KEYCODE_DPAD_RIGHT)
                    compose.onNodeWithTag("top-$destination").assertIsFocused()
                    compose.onNodeWithTag("top-HOME").assertIsSelected()
                }
                compose.onNodeWithTag("top-VOD").assertIsFocused()
                compose.onNodeWithTag("top-HOME").assertIsSelected()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.waitForIdle()
                compose.onNodeWithTag("sub-SERIES").assertIsSelected()
                compose.onNodeWithTag("top-VOD").assertIsNotFocused()
                compose.onNodeWithTag("sub-SERIES").assertIsFocused()
                assertNavigationLevels(contextual = true)
                nativeCapture("series")
                press(KeyEvent.KEYCODE_DPAD_UP)
                compose.onNodeWithTag("top-VOD").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.onNodeWithTag("sub-SERIES").assertIsFocused()
                press(KeyEvent.KEYCODE_DPAD_DOWN)
                compose.onNodeWithTag("sub-SERIES").assertIsNotFocused()
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("sub-SERIES").assertIsFocused()
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("top-VOD").assertIsFocused()
                compose.onNodeWithTag("top-PROFILE").performClick()
                compose.onNodeWithTag("sub-MORE").assertDoesNotExist()
                compose.onNodeWithTag("settings-group-PROFILE").assertIsDisplayed()
                assertNavigationLevels(contextual = true)
                compose.onNodeWithTag("settings-group-PROFILE").performClick()
                compose.onNodeWithTag("settings-library-history").performScrollTo().performClick()
                nativeCapture("profile")
                for (page in listOf("FAVORITES", "HISTORY", "BACKUP", "LOCAL_SYNC", "ERROR_LOG", "ABOUT")) {
                    compose.onNodeWithTag("profile-page-$page").performClick()
                    compose.onNodeWithTag("profile-content-$page").assertIsDisplayed()
                }
                compose.onNodeWithTag("top-PROFILE").performClick()
                compose.onNodeWithTag("settings-group-PROFILE").performClick()
                compose.onNodeWithTag("settings-downloads").performScrollTo().performClick()
                compose.onNodeWithTag("top-PROFILE").assertIsSelected()
                nativeCapture("downloads")
                var recordingsLabel = ""
                scenario.onActivity { recordingsLabel = it.getString(R.string.recording_title) }
                compose.onNode(hasText(recordingsLabel) and hasClickAction()).performClick()
                nativeCapture("recordings")
                compose.onNodeWithTag("top-PROFILE").performClick()
                compose.onNodeWithTag("settings-group-PROFILE").assertIsDisplayed()
                assertNavigationLevels(contextual = true)
                nativeCapture("settings")
                // Management replaces the settings selector, never stacks another menu on it.
                compose.onNodeWithTag("settings-group-SOURCES").performClick()
                var collectionLabel = ""
                var libraryLabel = ""
                scenario.onActivity {
                    collectionLabel = it.getString(R.string.mintv_collection_settings)
                    libraryLabel = it.getString(R.string.mintv_full_library)
                }
                compose.onNode(hasText(collectionLabel) and hasClickAction()).performScrollTo().performClick()
                compose.onNodeWithTag("settings-categories").assertDoesNotExist()
                assertNavigationLevels(contextual = true)
                compose.onNodeWithTag("tv-category-GENERAL").assertExists()
                // Return from the editor to the same settings group without modifying choices.
                compose.onNodeWithTag("tv-category-GENERAL").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("settings-categories").assertExists()
                compose.onNode(hasText(libraryLabel) and hasClickAction()).performScrollTo().performClick()
                compose.onNodeWithTag("settings-categories").assertDoesNotExist()
                compose.onNodeWithTag("tv-category-GENERAL").assertDoesNotExist()
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("settings-categories").assertExists()
                assertEquals(originalChoices, sourceStore.choices(7))
                compose.onNodeWithTag("top-PROFILE").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                press(KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("top-HOME").assertIsSelected()
                compose.onNodeWithTag("top-HOME").assertIsFocused()
                compose.onNodeWithTag("top-HOME").performClick()
            }
            // The populated journey intentionally leaves Home on TV3. Recreation must restore
            // that card/scroll, rather than force the hero heading back onto the screen.
            if (premium) {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("smart-channel-tv3").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("smart-channel-tv3").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("smart-channel-tv3").assertIsFocused().assertIsDisplayed(); true }.getOrDefault(false) }
            }
            val anchorBefore = if (premium) compose.onNodeWithTag("smart-channel-tv3").fetchSemanticsNode().positionInRoot else null
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            compose.waitUntil(15_000) {
                runCatching {
                    if (premium) compose.onNodeWithTag("smart-channel-tv3").assertIsFocused().assertIsDisplayed()
                    else compose.onNodeWithText(homeLabel).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            compose.onNodeWithTag("top-HOME").assertIsSelected()
            if (premium) {
                val anchorAfter = compose.onNodeWithTag("smart-channel-tv3").fetchSemanticsNode().positionInRoot
                assertEquals(anchorBefore!!.x, anchorAfter.x, 2f)
                assertEquals(anchorBefore.y, anchorAfter.y, 2f)
            } else compose.onNodeWithText(homeLabel).assertIsDisplayed()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
        assertEquals(profileName, db.profileDao().getById(7)!!.name)
        assertEquals(2, db.profileDao().getAllOnce().size)
        assertEquals(3, db.sourceDao().allLinks().size)
        assertNotNull(db.channelDao().getById(101300))
        assertNotNull(db.channelDao().getById(102300))
        assertTrue(db.favoriteDao().exists(7, MediaType.LIVE, 101001))
        assertTrue(db.historyDao().exists(8, MediaType.LIVE, 102001))
        assertEquals(SportPreferences(listOf("hockey:karlskoga", "football:degerfors-if"), hideScores = true), GlobalContext.get().get<SportsPreferences>().read(7))
        assertEquals(60, settings.epgOffsetMinutes.first())
        assertEquals(selectedProfile, settings.activeProfileId.first())
        assertTrue(app.getSharedPreferences("mintv-home-sections", Context.MODE_PRIVATE).getString("profile:7", "")!!.contains("MOVIES"))
    }
}
