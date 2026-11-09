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
            val owner = store.readSelection(101)!!.owner
            val follow = tv.own.owntv.features.sporthub.HubFollow("football:openliga:1", "Synthetic club", "gb.premier",
                tv.own.owntv.features.sporthub.HubSport.FOOTBALL)
            assertTrue(store.updateSelection(101, owner) { it.copy(hubTeams = listOf(follow),
                hubProviders = setOf(tv.own.owntv.features.sporthub.HubProvider.OPENLIGA)) })
            val persisted = SportsPreferences(isolated, dao).read(101)
            assertEquals(a.teamIds, persisted.teamIds)
            assertEquals(listOf(follow), persisted.hubTeams)
            assertEquals(legacy, store.read(102))
            // A second local picker updates only its fields, preserving the fresh public-source selection.
            assertTrue(store.updateSelection(101, owner) { it.copy(hideScores = false) })
            assertEquals(listOf(follow), store.read(101).hubTeams)
            // A later restore/new profile is not treated as an old hardcoded Färjestad preference.
            dao.insert(ProfileEntity(id = 103, name = "Synthetic new", avatarColor = 0, createdAt = 300))
            store.migrateLegacy(dao.getAllOnce())
            assertEquals(SportPreferences(), store.read(103))
            dao.delete(dao.getById(101)!!)
            dao.insert(ProfileEntity(id = 101, name = "Synthetic reused", avatarColor = 0, createdAt = 400))
            assertEquals(SportPreferences(), store.read(101))
            assertFalse(store.updateSelection(101, owner) { persisted })
            assertEquals(SportPreferences(), store.read(101))
            store.remove(101)
            assertEquals(legacy, store.read(102))
        } finally { db.close(); app.deleteSharedPreferences(file) }
    }
}
