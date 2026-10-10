package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.features.home.*

/** Synthetic in-memory profiles and an isolated preference file; never touches installed IPTV data. */
@RunWith(AndroidJUnit4::class)
class SportsProfileStorageTest {
    @Test fun migrationIsolationPersistenceAndDatabaseIdReuse() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "sports-instrumentation-" + java.util.UUID.randomUUID()
        val isolated = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = app.getSharedPreferences(file, Context.MODE_PRIVATE)
        }
        val db = Room.inMemoryDatabaseBuilder(app, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.profileDao()
            dao.insert(ProfileEntity(id = 101, name = "Synthetic A", avatarColor = 0, createdAt = 100))
            dao.insert(ProfileEntity(id = 102, name = "Synthetic B", avatarColor = 0, createdAt = 200))
            val store = SportsPreferences(isolated, dao)
            store.migrateLegacy(dao.getAllOnce())
            val legacy = SportPreferences(listOf(SportsCatalog.farjestadId))
            assertEquals(legacy, store.read(101))
            assertEquals(legacy, store.read(102))
            val a = SportPreferences(listOf("hockey:karlskoga", "football:degerfors-if"), prominent = false, hideScores = true)
            store.save(101, a)
            assertEquals(a, SportsPreferences(isolated, dao).read(101))
            assertEquals(legacy, store.read(102))
            // A later restore/new profile is not treated as an old hardcoded Färjestad preference.
            dao.insert(ProfileEntity(id = 103, name = "Synthetic new", avatarColor = 0, createdAt = 300))
            store.migrateLegacy(dao.getAllOnce())
            assertEquals(SportPreferences(), store.read(103))
            dao.delete(dao.getById(101)!!)
            dao.insert(ProfileEntity(id = 101, name = "Synthetic reused", avatarColor = 0, createdAt = 400))
            assertEquals(SportPreferences(), store.read(101))
            store.remove(101)
            assertEquals(legacy, store.read(102))
        } finally { db.close(); app.deleteSharedPreferences(file) }
    }
}
