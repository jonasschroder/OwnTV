package tv.own.owntv.home

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    @get:Rule val compose = createEmptyComposeRule()
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
            val homeLabel = app.getString(R.string.mintv_v08_last_watched)
            compose.waitUntil(30_000) { compose.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(8_000) // Real elapsed survival beyond the reported one-second fatal window.
            compose.onNodeWithText(homeLabel).assertIsDisplayed()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val pictures = File(app.getExternalFilesDir(null), "startup-screenshots").apply { mkdirs() }
            File(pictures, "home-${bitmap.width}x${bitmap.height}-${java.util.Locale.getDefault().language}-profile-$selectedProfile.png")
                .outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(homeLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(homeLabel).assertIsDisplayed()
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
