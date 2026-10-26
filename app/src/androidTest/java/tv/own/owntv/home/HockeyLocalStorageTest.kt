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
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.features.home.*

class HockeyLocalStorageTest {
    @Test fun boundedAliasQueriesFindOnlyExactAuthorizedLinearChannels(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(app, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            for (id in listOf(101L, 102L)) db.sourceDao().insert(SourceEntity(id = id, name = "Synthetic", type = tv.own.owntv.core.model.SourceType.M3U, url = "http://127.0.0.1:9/unavailable"))
            val names = listOf("TV Hockey Live 2 HD", "TV Hockey Live 12 HD", "TV4SportLive2 FHD", "TV4 Play Hockey", "NO: TV Hockey Live 2")
            db.channelDao().insertAll(names.mapIndexed { i, name -> ChannelEntity(id = i + 1L, sourceId = 101, name = name, streamUrl = "http://127.0.0.1:9/unavailable") } +
                ChannelEntity(id = 99, sourceId = 102, name = "TV Hockey Live 2 HD", streamUrl = "http://127.0.0.1:9/unavailable"))
            val broadcaster = BroadcastChannel("TV4 Sport Live 2", true)
            val found = BroadcastResolver.librarySearchTerms(broadcaster).flatMap { db.channelDao().searchList(it, listOf(101), 129) }
                .distinctBy { it.id }.filter { BroadcastResolver.matches(broadcaster, it.name) }
            assertEquals(setOf(1L, 3L), found.map { it.id }.toSet())
        } finally { db.close() }
    }

    @Test fun storedGuideHonorsManualMappingOffsetsProfileGuardAndChosenQuality(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = "v07-hockey-fixture-" + java.util.UUID.randomUUID()
        val isolated = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(preferences, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(app, OwnTVDatabase::class.java).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val now = java.time.Instant.parse("2026-10-13T15:00:00Z").toEpochMilli()
            val channel = ChannelEntity(id = 10, sourceId = 101, name = "SE | TV4 Sport Live 2 FHD", remoteId = "quality-fhd",
                streamUrl = "https://example.invalid/synthetic", epgChannelId = "wrong-guide")
            val key = CustomizeKeys.channel(channel)
            val custom = SectionCustomizations(epgMatches = mapOf(key to "  HOCKEY-GUIDE  "), epgShifts = mapOf(key to "60"))
            val programme = EpgProgrammeEntity(id = 1, sourceId = 101, epgChannelId = "hockey-guide", startMs = now + 60_000,
                stopMs = now + 3 * 60 * 60_000, title = "SHL LIVE: FBK – LHF")
            db.epgDao().upsertProgrammes(listOf(programme, programme.copy(id = 2, epgChannelId = "wrong-guide", title = "SHL studio")))
            val rows = storedHockeyProgrammes(db.epgDao(), channel, custom, -120, now) { true }
            assertEquals(1, rows.size); assertEquals(programme.startMs + 60 * 60_000, rows.single().startMs)
            val collector = HockeyEpgCollector(SportPreferences(listOf(SportsCatalog.farjestadId)), now)
            rows.forEach { collector.add(channel.id, it) }
            val match = collector.result().single(); assertEquals(listOf(channel.id), match.channelIds)
            assertNull(match.fixture.score)
            var checks = 0
            assertTrue(storedHockeyProgrammes(db.epgDao(), channel, custom, 0, now) { ++checks == 1 }.isEmpty())
            assertEquals(2, checks)
            val window = storedHockeyWindow(db.epgDao(), channel, custom, -120, now, now + 4 * 60 * 60_000) { true }
            assertEquals(rows, window)
            val unmapped = storedHockeyWindow(db.epgDao(), channel, SectionCustomizations(), 0, now, now + 4 * 60 * 60_000) { true }
            assertEquals(listOf("SHL studio"), unmapped.map { it.title })
            assertNotEquals(window, unmapped)
            var windowChecks = 0
            assertTrue(storedHockeyWindow(db.epgDao(), channel, custom, 0, now, now + 4 * 60 * 60_000) { ++windowChecks == 1 }.isEmpty())
            assertEquals(2, windowChecks)
            val choices = ShlChannelOverrides(isolated)
            choices.save(FixtureChannelChoice(101, fixtureKey(match.fixture.broadcastFixture()), channel.id, channel.sourceId,
                channel.name, now + 4 * 60 * 60_000), now)
            val restored = ShlChannelOverrides(isolated).read(101, match.fixture.broadcastFixture(), now)!!
            assertTrue(restored.usable(101, match.fixture.broadcastFixture(), now, channel.id, channel.sourceId, channel.name))
            assertFalse(restored.usable(102, match.fixture.broadcastFixture(), now, channel.id, channel.sourceId, channel.name))
            assertFalse(restored.usable(101, match.fixture.broadcastFixture(), now, channel.id, 102, channel.name))
            assertFalse(restored.usable(101, match.fixture.broadcastFixture(), now, channel.id, channel.sourceId, "TV4 Sport Live 2 HD"))
            assertNull(choices.read(102, match.fixture.broadcastFixture(), now))
            assertNull(choices.read(101, match.fixture.copy(kickoff = match.fixture.kickoff.plusSeconds(24 * 60 * 60)).broadcastFixture(), now))
        } finally { db.close(); app.deleteSharedPreferences(preferences) }
    }
}
