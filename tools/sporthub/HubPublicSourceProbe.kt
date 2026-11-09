package tv.own.owntv.home

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.time.Instant
import tv.own.owntv.features.sporthub.*

/** Explicit local probe only. Not in the CI suite: CI must not consume a third party's live quota. */
class HubPublicSourceProbe {
    @Test fun actualNhlReaderOnDisposableAndroid(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSportProbe") == "true")
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop","ro.kernel.qemu"))
        assertEquals("1", process.inputStream.bufferedReader().use { it.readText().trim() });assertEquals(0,process.waitFor())
        val app = instrumentation.targetContext
        assertEquals("se.jonasschroder.mintv.qa",app.packageName)
        val result = HubSourceReader(PublicHubTransport(),FileHubCache(app)).read(HubLeagues.nhl,"2026-27",Instant.now(),{true})
        val summary=JSONObject().put("provider",HubProvider.EVENTS24.name).put("league",HubLeagues.nhl.key)
            .put("verifiedAt",Instant.now().toString()).put("cached",result.cached).put("issue",result.issue?.name ?: JSONObject.NULL)
            .put("rows",result.snapshot?.matches?.size ?: 0).put("coverage",result.snapshot?.coverage?.joinToString().orEmpty())
            .put("fetchedAt",result.snapshot?.fetchedAt?.toString() ?: JSONObject.NULL).put("partial",result.snapshot?.hasMore ?: false)
        val out=File(app.getExternalFilesDir(null),"sport-hub-live-proof.json");out.writeText(summary.toString(2))
        assertNotNull("Actual Android reader failed; only sanitized status was retained",result.snapshot)
        assertNull(result.issue);assertTrue(result.snapshot!!.matches.isNotEmpty())
    }
}
