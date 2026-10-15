package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.*
import tv.own.owntv.features.home.*

class HomeDashboardStorageTest {
    @Test fun legacyLayoutAndProfileOwnershipSurviveReorderHideAndRestart(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "v08-layout-" + java.util.UUID.randomUUID()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(context, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            db.profileDao().insert(ProfileEntity(id = 7, name = "Synthetic A", avatarColor = 0, createdAt = 1))
            db.profileDao().insert(ProfileEntity(id = 8, name = "Synthetic B", avatarColor = 0, createdAt = 2))
            val prefs = context.getSharedPreferences("mintv-home-sections", 0)
            assertTrue(prefs.edit().putString("profile:7", """{"owner":1,"hidden":["TWITCH","MOVIES"]}""").commit())
            fun store() = HomeSections(context, db.profileDao())
            assertEquals(setOf(HomeSection.TWITCH, HomeSection.MOVIES), store().hidden(7))
            assertEquals(defaultHomeOrder, store().order(7))
            val order = normalizedHomeOrder(listOf(HomeSection.NOW, HomeSection.CONTINUE))
            store().saveOrder(7, order)
            assertEquals(setOf(HomeSection.TWITCH, HomeSection.MOVIES), store().hidden(7))
            store().save(7, setOf(HomeSection.TWITCH))
            assertEquals(order, store().order(7))
            assertEquals(setOf(HomeSection.TWITCH), store().hidden(7))
            assertTrue(store().hidden(8).isEmpty()); assertEquals(defaultHomeOrder, store().order(8))
            // Reusing an ID with another profile owner must not inherit the previous user's layout.
            db.profileDao().insert(ProfileEntity(id = 7, name = "Synthetic replacement", avatarColor = 0, createdAt = 3))
            assertTrue(store().hidden(7).isEmpty()); assertEquals(defaultHomeOrder, store().order(7))
        } finally { db.close(); context.getSharedPreferences("mintv-home-sections", 0).edit().clear().commit() }
    }
    @Test fun watchedHistoryIsTimestampOrderedProfileScopedAndHighlightDoesNotRecord(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(app, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for (id in listOf(7L, 8L)) db.profileDao().insert(ProfileEntity(id = id, name = "Synthetic", avatarColor = 0, createdAt = id))
            for (id in listOf(101L, 102L)) db.sourceDao().insert(SourceEntity(id = id, name = "Synthetic", type = SourceType.M3U, url = "https://example.invalid"))
            val channels = (1L..4L).map { ChannelEntity(id = it, sourceId = if (it == 4L) 102 else 101, name = "Synthetic $it", streamUrl = "https://example.invalid/$it") }
            db.channelDao().insertAll(channels)
            db.historyDao().record(WatchHistoryEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 1, watchedAt = 100))
            db.historyDao().record(WatchHistoryEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 2, watchedAt = 200))
            db.historyDao().record(WatchHistoryEntity(profileId = 7, mediaType = MediaType.LIVE, itemId = 4, watchedAt = 300))
            db.historyDao().record(WatchHistoryEntity(profileId = 8, mediaType = MediaType.LIVE, itemId = 3, watchedAt = 400))
            val recent = db.channelDao().recentlyWatchedWithTimestampFiltered(7, listOf(101), 12).first().map { it.channel }
            val state = HomeUiState(profileId = 7, recentLive = recent, favoriteLive = listOf(channels[2]))
            assertEquals(2L, homeHistoryCandidate(state, 7, listOf(101))?.id)
            assertEquals(listOf(2L, 1L), recent.map { it.id })
            assertFalse(db.historyDao().exists(7, MediaType.LIVE, 3))
            assertNull(homeHistoryCandidate(state, 8, listOf(101)))
        } finally { db.close() }
    }
}
