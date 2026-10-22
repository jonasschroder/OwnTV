package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.live.*
import tv.own.owntv.features.live.source.*

/** Real Room and real existing SharedPreferences encoding; isolated synthetic profiles only. */
class ChannelSourceStorageTest {
    @Test fun preferencesSurviveRefreshRestartAndRemainProfileOwnedWithoutRewritingOtherSettings(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "source-quality-test-" + java.util.UUID.randomUUID()
        val isolated = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(isolated, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for (id in listOf(7L, 8L)) db.profileDao().insert(ProfileEntity(id = id, name = "Synthetic", avatarColor = 0, createdAt = id))
            for (id in listOf(101L, 102L)) {
                db.sourceDao().insert(SourceEntity(id = id, name = "Synthetic", type = SourceType.M3U, url = "http://127.0.0.1:9", syncLive = true))
                db.sourceDao().link(ProfileSourceCrossRef(7, id))
            }
            fun channel(id: Long, quality: String, source: Long = 101, remote: String = "$id") = ChannelEntity(
                id = id, sourceId = source, remoteId = remote, name = "TV4 $quality", streamUrl = "http://127.0.0.1:9/synthetic")
            val hd = channel(1, "HD"); val fhd = channel(2, "FHD"); val uhd = channel(3, "UHD", 102)
            db.channelDao().insertAll(listOf(hd, fhd, uhd))
            val custom = CustomizationStore(isolated)
            val store = SwedishChannelStore(isolated, db, custom)
            val original = SwedishChoices(showUhd = false, showVariants = true, order = listOf("tv4", "svt1"), hidden = setOf("svt2"),
                preferred = mapOf("svt1" to "keep:other"))
            store.save(999, original) // Legacy collection controls remain safe before profile arrival.
            assertEquals(SwedishChoices(), store.choices(999))
            store.save(7, original)
            val firstObserved = CompletableDeferred<Unit>()
            val observed = async {
                withTimeout(5000) { store.observeChoices(7).onEach { firstObserved.complete(Unit) }
                    .first { it.preferred["tv4"] == "102:3" } }
            }
            firstObserved.await()
            store.savePreferred(7, 7, "tv4", "102:3") { true }
            assertEquals("102:3", observed.await().preferred["tv4"])
            val restarted = SwedishChannelStore(isolated, db, custom)
            val saved = restarted.choices(7)
            assertEquals(original.copy(preferred = original.preferred + ("tv4" to "102:3")), saved)
            assertTrue(restarted.choices(8).preferred.isEmpty())
            assertTrue(restarted.library(8, listOf(101, 102), saved).groups.isEmpty())
            val before = restarted.library(7, listOf(101, 102), saved).groups.single()
            assertEquals(3L, ChannelSourceResolver.resolve(before, saved).selected?.id)
            db.channelDao().deleteByRemoteIds(102, listOf("3"))
            db.channelDao().insertAll(listOf(uhd.copy(id = 300)))
            assertEquals(300L, ChannelSourceResolver.resolve(restarted.library(7, listOf(101, 102), saved).groups.single(), saved).selected?.id)
            // Hidden or inaccessible variants disappear; preference remains for their return.
            custom.setItemHidden(7, MediaType.LIVE, "102:3", "Synthetic UHD", true)
            val hidden = restarted.library(7, listOf(101, 102), saved).groups.single()
            assertEquals(listOf(1L, 2L), hidden.variants.map { it.id })
            assertEquals(ChannelSourceStatus.SAVED_MISSING, ChannelSourceResolver.resolve(hidden, saved).status)
            assertEquals("102:3", restarted.choices(7).preferred["tv4"])
            assertFalse(restarted.library(7, listOf(101), saved).groups.single().variants.any { it.sourceId == 102L })
            try { restarted.savePreferred(7, 7, "tv4", "deleted") { false }; fail("Unavailable source must not save") }
            catch (_: IllegalStateException) { }
            assertEquals(saved, restarted.choices(7))
            // Failed durable commit cannot report success or change the real store.
            val failing = object : ContextWrapper(isolated) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    val real = isolated.getSharedPreferences(name, mode)
                    return object : SharedPreferences by real {
                        override fun edit(): SharedPreferences.Editor {
                            val edit = real.edit()
                            return object : SharedPreferences.Editor by edit {
                                override fun putString(key: String?, value: String?): SharedPreferences.Editor { edit.putString(key, value); return this }
                                override fun commit(): Boolean { edit.commit(); return false }
                            }
                        }
                    }
                }
            }
            try { SwedishChannelStore(failing, db, custom).savePreferred(7, 7, "tv4", "101:2") { true }; fail("Commit failure must propagate") }
            catch (_: IllegalStateException) { }
            assertEquals(saved, restarted.choices(7))
            val profile = db.profileDao().getById(7)!!
            db.profileDao().update(profile.copy(createdAt = 999))
            assertTrue(restarted.choices(7).preferred.isEmpty())
            try { restarted.savePreferred(7, 7, "tv4", "101:1") { true }; fail("Reused owner must reject old dialog") }
            catch (_: IllegalStateException) { }
        } finally { db.close() }
    }
}
