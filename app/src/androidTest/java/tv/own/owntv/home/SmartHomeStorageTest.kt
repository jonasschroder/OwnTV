package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.features.home.*

class SmartHomeStorageTest {
    @Test fun storedProgressKeepsExactEpisodeProfileScopeAndExcludesUnknownCompletedOrMissingItems(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(app, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for (id in listOf(7L, 8L)) db.profileDao().insert(ProfileEntity(id = id, name = "Synthetic", avatarColor = 0, createdAt = id))
            db.sourceDao().insert(SourceEntity(id = 101, name = "Synthetic source", type = SourceType.M3U, url = "http://127.0.0.1:9", syncMovies = true, syncSeries = true))
            db.sourceDao().link(ProfileSourceCrossRef(7, 101))
            db.movieDao().insertAll((1L..4L).map { MovieEntity(id = it, sourceId = 101, name = "Synthetic same title", streamUrl = if (it == 4L) "" else "http://127.0.0.1:9/movie/$it") })
            db.seriesDao().insertSeries(listOf(SeriesEntity(id = 11, sourceId = 101, name = "Synthetic series")))
            db.seriesDao().upsertEpisodes(listOf(EpisodeEntity(id = 12, seriesId = 11, seasonNumber = 1, episodeNumber = 2, name = "Synthetic exact episode", streamUrl = "http://127.0.0.1:9/episode")))
            for ((id, duration) in listOf(1L to 100L, 2L to 0L, 3L to 100L, 4L to 100L))
                db.progressDao().save(PlaybackProgressEntity(profileId = 7, mediaType = MediaType.MOVIE, itemId = id, positionMs = if (id == 3L) 90 else 1, durationMs = duration, updatedAt = id))
            db.progressDao().save(PlaybackProgressEntity(profileId = 7, mediaType = MediaType.EPISODE, itemId = 12, positionMs = 50, durationMs = 100, updatedAt = 100))
            db.progressDao().save(PlaybackProgressEntity(profileId = 8, mediaType = MediaType.MOVIE, itemId = 1, positionMs = 20, durationMs = 100, updatedAt = 1000))
            val prefix = "smart-home-test-" + java.util.UUID.randomUUID()
            val isolated = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
            }
            val custom = CustomizationStore(isolated)
            val actual = readSmartResumesForSources(db, custom, 7, setOf(101), setOf(101))
            assertEquals(listOf(12L, 1L), actual.map { it.itemId })
            assertEquals(11L, actual.first().seriesId)
            assertEquals(1L, actual.last().position) // no inherited launcher ten-second threshold
            assertTrue(readSmartResumesForSources(db, custom, 8, setOf(101), setOf(101)).isEmpty())
            assertTrue(readSmartResumesForSources(db, custom, 7, emptySet(), emptySet()).isEmpty())
            custom.setItemHidden(7, MediaType.MOVIE, "101:Synthetic same title", "Synthetic same title", true)
            assertEquals(listOf(12L), readSmartResumesForSources(db, custom, 7, setOf(101), setOf(101)).map { it.itemId })
            db.movieDao().insertAll((1000L..1120L).map { MovieEntity(id = it, sourceId = 101, name = "Synthetic budget $it", streamUrl = "http://127.0.0.1:9/movie/$it") })
            for (id in 1000L..1120L) db.progressDao().save(PlaybackProgressEntity(profileId = 7, mediaType = MediaType.MOVIE,
                itemId = id, positionMs = 1, durationMs = 100, updatedAt = id))
            val bounded = readSmartResumesForSources(db, custom, 7, setOf(101), setOf(101))
            assertEquals(96, bounded.size); assertEquals(1120L, bounded.first().itemId)
            assertEquals(50L, db.progressDao().get(7, MediaType.EPISODE, 12)?.positionMs)
            assertFalse(db.historyDao().exists(7, MediaType.EPISODE, 12))
        } finally { db.close() }
    }
}
