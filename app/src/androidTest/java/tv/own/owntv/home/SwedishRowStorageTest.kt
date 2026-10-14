package tv.own.owntv.home

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
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.live.*

/** Real bundled SQLite / Room, synthetic records only. No provider or stream requests. */
class SwedishRowStorageTest {
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OwnTVDatabase::class.java)
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private fun channel(id: Long, epg: String?, source: Long = 101) = ChannelEntity(id = id, sourceId = source,
        name = "Synthetic $id HD", remoteId = "$id", epgChannelId = epg, streamUrl = "https://example.invalid/synthetic")
    private fun key(channel: ChannelEntity) = SwedishRowKey(channel.id, channel.sourceId, CustomizeKeys.channel(channel))

    @Test fun storedNowNextHonorsIndependentXmltvManualMappingOffsetsMidnightAndAuthorization(): Unit = runBlocking {
        val db = database()
        try {
            val now = java.time.Instant.parse("2026-10-14T22:15:00Z").toEpochMilli()
            val ch = channel(1, "wrong"); val second = channel(2, "other", 102); val missing = channel(3, null)
            val custom = SectionCustomizations(epgMatches = mapOf(CustomizeKeys.channel(ch) to "  SVT-MANUAL  "),
                epgShifts = mapOf(CustomizeKeys.channel(ch) to "60"))
            val context = SwedishRowContext(1, listOf(101, 102), custom, -120)
            fun programme(id: Long, epg: String, start: Long, stop: Long, title: String) =
                EpgProgrammeEntity(id = id, sourceId = 999, epgChannelId = epg, startMs = start, stopMs = stop, title = title, description = "Not projected")
            db.epgDao().upsertProgrammes(listOf(
                programme(1, "svt-manual", now - 90 * 60_000, now - 30 * 60_000, "Across midnight"),
                programme(2, "svt-manual", now - 30 * 60_000, now + 30 * 60_000, "Next after midnight"),
                programme(3, "other", now + 60 * 60_000, now + 180 * 60_000, "Global offset"),
                programme(4, "wrong", now - 3_600_000, now + 3_600_000, "Wrong mapping")))
            val result = storedSwedishRows(db, listOf(ch, second, missing), context, now) { true }
            assertEquals("Across midnight", result[key(ch)]!!.now!!.title)
            assertEquals(now - 30 * 60_000, result[key(ch)]!!.now!!.startMs)
            assertEquals(now + 30 * 60_000, result[key(ch)]!!.now!!.stopMs)
            assertEquals("Next after midnight", result[key(ch)]!!.next!!.title)
            assertEquals("Global offset", result[key(second)]!!.now!!.title)
            assertNull(result[key(missing)]!!.now)
            val transitioned = storedSwedishRows(db, listOf(ch), context, now + 30 * 60_000) { true }
            assertEquals("Next after midnight", transitioned[key(ch)]!!.now!!.title)
            var checks = 0
            assertTrue(storedSwedishRows(db, listOf(ch), context, now) { ++checks == 1 }.isEmpty())
            assertEquals(2, checks)
            assertTrue(storedSwedishRows(db, listOf(ch), context.copy(profileId = 2), now) { false }.isEmpty())
            val remapped = storedSwedishRows(db, listOf(ch), context.copy(custom = SectionCustomizations(), offset = 0), now) { true }
            assertEquals("Wrong mapping", remapped[key(ch)]!!.now!!.title)
        } finally { db.close() }
    }

    @Test fun largeGuideRemainsBoundedAndSynchronizationNeverReturnsBlankExpiredOrOtherChannelTitles(): Unit = runBlocking {
        val db = database()
        try {
            val now = java.time.Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
            // 200,000 programmes, inserted in bounded batches; descriptions deliberately large.
            val started = android.os.SystemClock.elapsedRealtime()
            for (batch in 0 until 200) db.epgDao().upsertProgrammes((0 until 1000).map { i ->
                val id = batch * 1000L + i
                EpgProgrammeEntity(id = id + 1, sourceId = 999, epgChannelId = "guide${id / 200}",
                    startMs = now + (id % 200 - 100) * 1_800_000,
                    stopMs = now + (id % 200 - 99) * 1_800_000, title = "Stored $id", description = "synthetic description ".repeat(20))
            })
            val channels = (0L until 500).map { channel(it + 1, "guide$it") }
            val context = SwedishRowContext(1, listOf(101), SectionCustomizations(), 0)
            val readStart = android.os.SystemClock.elapsedRealtime()
            val rows = storedSwedishRows(db, channels, context, now) { true }
            val elapsed = android.os.SystemClock.elapsedRealtime() - readStart
            assertEquals(16, rows.size)
            channels.take(16).forEachIndexed { index, ch ->
                assertEquals("Stored ${index * 200 + 100}", rows[key(ch)]!!.now!!.title)
                assertEquals("Stored ${index * 200 + 101}", rows[key(ch)]!!.next!!.title)
            }
            assertEquals(32, rows.values.sumOf { listOfNotNull(it.now, it.next).size })
            val first = channels.first()
            db.epgDao().upsertProgrammes(listOf(EpgProgrammeEntity(id = 101, sourceId = 999, epgChannelId = "guide0",
                startMs = now, stopMs = now + 1_800_000, title = "Resynchronized")))
            assertEquals("Resynchronized", storedSwedishRows(db, listOf(first), context, now) { true }[key(first)]!!.now!!.title)
            val stale = channel(900, "stale")
            db.epgDao().upsertProgrammes(listOf(
                EpgProgrammeEntity(sourceId = 999, epgChannelId = "stale", startMs = now - 3_600_000, stopMs = now - 1, title = "Expired"),
                EpgProgrammeEntity(sourceId = 999, epgChannelId = "stale", startMs = now, stopMs = now + 1_800_000, title = " ")))
            val unavailable = storedSwedishRows(db, listOf(stale), context, now) { true }[key(stale)]!!
            assertNull(unavailable.now); assertNull(unavailable.next)
            android.util.Log.i("SwedishRowFixture", "Synthetic emulator: programmes=200000 channels=500 queried=16 summaries=32 readMs=$elapsed totalMs=${android.os.SystemClock.elapsedRealtime() - started}")
        } finally { db.close() }
    }
}
