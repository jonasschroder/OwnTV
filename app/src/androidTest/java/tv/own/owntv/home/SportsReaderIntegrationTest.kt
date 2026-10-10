package tv.own.owntv.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.features.home.*

/** Real Android persistence + production OkHttp/parser. Transport serves saved public HTML only. */
@RunWith(AndroidJUnit4::class)
class SportsReaderIntegrationTest {
    @Test fun officialPagesSurviveRecreationFailuresAndAccessBlocks(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val name = "sports-reader-test-" + java.util.UUID.randomUUID()
        val directory = File(app.cacheDir, name).apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir() = directory
            override fun getSharedPreferences(key: String, mode: Int) = app.getSharedPreferences(name, mode)
        }
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        var status = 200
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val path = request.url.encodedPath
            assertEquals("stats.swehockey.se", request.url.host)
            assertEquals("MinTV-ExperimentalSHL/0.2", request.header("User-Agent"))
            paths += path
            val code = if (status == 200 && path == "/robots.txt") 404 else status
            val fixture = when (path) {
                "/" -> "index"
                "/ScheduleAndResults/Schedule/20961" -> "shl-schedule"
                "/ScheduleAndResults/Standings/20961" -> "shl-table"
                "/ScheduleAndResults/Schedule/20962" -> "ha-schedule"
                "/ScheduleAndResults/Standings/20962" -> "ha-table"
                else -> null
            }
            val body = if (code == 200 && fixture != null) instrumentation.context.assets.open("$fixture.html").bufferedReader().use { it.readText() } else ""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic public page").body(body.toResponseBody()).build()
        }.build()
        val now = Instant.parse("2026-10-10T15:06:00Z").toEpochMilli()
        suspend fun failure(block: suspend () -> Any?): HockeyIssue = try { block(); error("Expected failure") }
            catch (e: HockeyDataException) { e.issue }
        try {
            val repository = ShlRepository(context, client)
            assertEquals(HockeyFailureKind.DISABLED, failure { repository.refresh(now) }.kind)
            assertTrue(paths.isEmpty())
            repository.preferences.edit().putBoolean("shl-enabled", true).commit()
            val shl = repository.refresh(now)
            val ha = repository.refresh(now, SportsCatalog.allsvenskan)
            assertEquals(14, repository.standings(shl.seasonId, now).rows.size)
            assertEquals(14, repository.standings(ha.seasonId, now, SportsCatalog.allsvenskan).rows.size)
            assertEquals(6, paths.size)
            val restored = ShlRepository(context, client)
            assertEquals(shl, restored.refresh(now + 1000))
            assertEquals(14, restored.standings(shl.seasonId, now + 1000).rows.size)
            assertEquals(6, paths.size)
            status = 503
            val issue = failure { restored.refresh(now + 60 * 60_000) }
            assertEquals(HockeyFailureKind.HTTP, issue.kind); assertEquals(503, issue.httpCode)
            assertEquals(shl, restored.cached(SportsCatalog.shl, now + 60 * 60_000))
            val calls = paths.size
            failure { ShlRepository(context, client).refresh(now + 60 * 60_000 + 1000) }
            assertEquals(calls, paths.size)
            status = 200
            restored.refresh(issue.retryAt!!)
            assertEquals(14, restored.cachedStandings(SportsCatalog.shl, shl.seasonId)!!.rows.size)
            assertEquals(ha, restored.cached(SportsCatalog.allsvenskan, now + 62 * 60_000))
            status = 403
            val denied = failure { restored.standings(shl.seasonId, now + 6 * 60 * 60_000 + 1) }
            assertEquals(HockeyFailureKind.ACCESS, denied.kind); assertEquals(403, denied.httpCode)
            val stoppedAt = paths.size
            failure { ShlRepository(context, client).refresh(now + 7 * 60 * 60_000, SportsCatalog.allsvenskan) }
            assertEquals(stoppedAt, paths.size)
            assertEquals(14, restored.cachedStandings(SportsCatalog.shl, shl.seasonId)!!.rows.size)
        } finally {
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            directory.deleteRecursively(); app.deleteSharedPreferences(name)
        }
        Unit
    }
}
