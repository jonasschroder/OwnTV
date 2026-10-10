package tv.own.owntv.update

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.home.SportsPreferences
import tv.own.owntv.features.home.SportPreferences

/** Real persistent stores in a disposable emulator only. Never clears, backs up, or touches another app. */
@RunWith(AndroidJUnit4::class)
class SameSignerUpgradeTest {
    @Test fun syntheticDataSurvivesSameSignerUpgrade() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        // The host script also verifies ro.kernel.qemu and emulator serial before any installation.
        assertEquals("true", arguments.getString("disposableEmulator"))
        assertTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = app.packageManager.getPackageInfo(app.packageName, 0)
        assertEquals(arguments.getString("expectedCode")!!.toLong(), installed.longVersionCode)
        val db = GlobalContext.get().get<OwnTVDatabase>()
        val settings = GlobalContext.get().get<SettingsRepository>()
        val sentinel = File(app.filesDir, "same-signer-synthetic-marker")
        val sports = SportsPreferences(app, db.profileDao())
        val choice = SportPreferences(listOf("hockey:karlskoga", "football:degerfors-if"), hideScores = true)
        if (arguments.getString("phase") == "seed") {
            assertFalse(sentinel.exists())
            assertNull(db.profileDao().getById(900001))
            db.profileDao().insert(ProfileEntity(id = 900001, name = "Upgrade synthetic profile", avatarColor = 7, createdAt = 123456789))
            db.sourceDao().insert(SourceEntity(id = 900001, name = "Synthetic offline source", type = SourceType.M3U,
                url = "https://example.invalid/synthetic.m3u", username = "synthetic-user", password = "synthetic-password",
                syncLive = false, syncMovies = false, syncSeries = false))
            db.channelDao().insertAll(listOf(ChannelEntity(id = 900001, sourceId = 900001,
                name = "Synthetic offline channel", streamUrl = "https://example.invalid/synthetic.ts", epgChannelId = "synthetic-epg")))
            db.favoriteDao().add(FavoriteEntity(profileId = 900001, mediaType = MediaType.LIVE, itemId = 900001))
            settings.setEpgOffsetMinutes(60)
            settings.setWeatherLocation("Synthetic upgrade marker")
            settings.setUpdateCheckOnStart(false)
            sports.save(900001, choice)
            assertTrue(app.getSharedPreferences("same-signer-fixture", Context.MODE_PRIVATE).edit()
                .putString("owner", app.packageName).putBoolean("preview-muted", true).commit())
            sentinel.writeText(app.packageName + "|synthetic-only")
        } else {
            assertEquals("verify", arguments.getString("phase"))
        }
        assertEquals(app.packageName + "|synthetic-only", sentinel.readText())
        assertEquals("Upgrade synthetic profile", db.profileDao().getById(900001)!!.name)
        val source = db.sourceDao().getById(900001)!!
        assertEquals("synthetic-user", source.username)
        assertEquals("synthetic-password", source.password)
        assertFalse(source.syncLive)
        assertTrue(db.favoriteDao().exists(900001, MediaType.LIVE, 900001))
        assertEquals("synthetic-epg", db.channelDao().getById(900001)!!.epgChannelId)
        assertEquals(60, settings.epgOffsetMinutes.first())
        assertEquals("Synthetic upgrade marker", settings.weatherLocation.first())
        assertFalse(settings.updateCheckOnStart.first())
        assertEquals(choice, sports.read(900001))
        val prefs = app.getSharedPreferences("same-signer-fixture", Context.MODE_PRIVATE)
        assertEquals(app.packageName, prefs.getString("owner", null))
        assertTrue(prefs.getBoolean("preview-muted", false))
    }
}
