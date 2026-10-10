package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.model.*
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.features.home.HomeChannelDefaults
import java.io.File

/** Never reads/writes the installed user's database, settings or favorite claims. */
@RunWith(AndroidJUnit4::class)
class HomeChannelDefaultsStorageTest {
    @Test fun preservesFavoritesRemovalRestartSyncAndProfileIsolation() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "default-favorite-test-" + java.util.UUID.randomUUID()).apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(directory.name + name, mode)
        }
        val settings = SettingsRepository(isolated, LocaleStore.from(isolated))
        val db = Room.inMemoryDatabaseBuilder(isolated, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for (id in listOf(101L, 102L)) {
                db.profileDao().insert(ProfileEntity(id = id, name = "Synthetic", avatarColor = 0, createdAt = id))
                db.sourceDao().insert(SourceEntity(id = id, name = "Synthetic", type = SourceType.M3U, url = "https://example.invalid", lastSyncAt = 1))
                db.sourceDao().link(ProfileSourceCrossRef(id, id))
            }
            val hockey = ChannelEntity(id = 1, sourceId = 101, name = "TV4 Hockey SE", streamUrl = "https://example.invalid/configured")
            val other = hockey.copy(id = 2, sourceId = 102)
            db.channelDao().insertAll(listOf(hockey, other))
            settings.setActiveProfile(101)
            fun store() = HomeChannelDefaults(isolated, db, settings, CatalogSyncScheduler(isolated))
            suspend fun ids(id: Long) = db.favoriteDao().observeFavoriteIds(id, MediaType.LIVE).first()
            store().seed(101, listOf(101), other) { true }
            assertTrue(ids(101).isEmpty()) // foreign source refused
            store().seed(101, listOf(101), hockey) { false }
            assertTrue(ids(101).isEmpty()) // hidden channel refused
            store().seed(101, listOf(101), null) { true }
            assertTrue(ids(101).isEmpty()) // no candidate leaves the initial offer available
            kotlinx.coroutines.coroutineScope {
                val first = async { store().seed(101, listOf(101), hockey) { true } }
                val second = async { store().seed(101, listOf(101), hockey) { true } }
                first.await(); second.await()
            }
            assertEquals(listOf(1L), ids(101))
            store().seed(101, listOf(101), hockey) { true }
            assertEquals(listOf(1L), ids(101))
            db.favoriteDao().remove(101, MediaType.LIVE, 1)
            // Restart and provider re-import changes row IDs: a removed default stays removed.
            db.channelDao().insertAll(listOf(hockey.copy(id = 3)))
            store().seed(101, listOf(101), hockey.copy(id = 3)) { true }
            assertTrue(ids(101).isEmpty())
            store().seed(102, listOf(102), other) { true }
            assertTrue(ids(102).isEmpty()) // switching context is required
            settings.setActiveProfile(102)
            db.favoriteDao().add(FavoriteEntity(profileId = 102, mediaType = MediaType.LIVE, itemId = 2, addedAt = 42))
            db.channelDao().insertAll(listOf(other.copy(id = 4, name = "SVT 1")))
            db.favoriteDao().add(FavoriteEntity(profileId = 102, mediaType = MediaType.LIVE, itemId = 4, addedAt = 43))
            val before = db.favoriteDao().getAllOnce()
            store().seed(102, listOf(102), other) { true }
            assertEquals(before, db.favoriteDao().getAllOnce())
            db.favoriteDao().remove(102, MediaType.LIVE, 2)
            db.favoriteDao().remove(102, MediaType.LIVE, 4)
            store().seed(102, listOf(102), other) { true }
            assertTrue(ids(102).isEmpty()) // existing favorites consumed the offer too
            // Deleting/recreating a profile with a reused numeric ID is a new owner.
            db.profileDao().delete(db.profileDao().getById(101)!!)
            db.profileDao().insert(ProfileEntity(id = 101, name = "Synthetic new owner", avatarColor = 0, createdAt = 999))
            db.sourceDao().link(ProfileSourceCrossRef(101, 101))
            settings.setActiveProfile(101)
            store().seed(101, listOf(101), hockey.copy(id = 3)) { true }
            assertEquals(listOf(3L), ids(101))
            db.profileDao().insert(ProfileEntity(id = 103, name = "Synthetic removed before upgrade", avatarColor = 0, createdAt = 103))
            db.sourceDao().link(ProfileSourceCrossRef(103, 101))
            db.tombstoneDao().record(103, "fav", """{"t":"LIVE","src":101,"name":"TV4 Hockey SE"}""", 1)
            settings.setActiveProfile(103)
            store().seed(103, listOf(101), hockey) { true }
            assertTrue(ids(103).isEmpty()) // retained manual deletion is intent, including before this feature
        } finally { db.close() }
        // Keep the isolated DataStore directory until process exit; it owns an asynchronous scope.
    }
}
