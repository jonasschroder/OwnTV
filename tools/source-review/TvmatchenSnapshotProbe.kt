package tv.own.owntv.features.home

import java.io.File
import java.time.Instant
import org.junit.Test

/** Manual diagnostic only: downloaded public snapshots outside Git, no HTTP or private provider data. */
class TvmatchenSnapshotProbe {
    @Test fun parseBoundedPublicSnapshots() {
        val directory = File(requireNotNull(System.getenv("MINTV_SOURCE_REVIEW_DIR")))
        val now = Instant.parse("2026-10-26T12:00:00Z")
        val index = runCatching { TvmatchenHtml.fixtureIndex(File(directory, "tvmatchen-shl.html").readText(), now) }
        println("PUBLIC_SNAPSHOT index=" + index.fold({ "validated_count=${it.size}" }, { "rejected_${it.javaClass.simpleName}_line_${it.stackTrace.firstOrNull { frame -> frame.fileName == "TvmatchenHtml.kt" }?.lineNumber}" }))
        val examples = listOf(
            BroadcastFixture("SHL", "Brynäs IF", "Färjestad BK", java.time.LocalDate.parse("2026-10-29"), Instant.parse("2026-10-29T18:00:00Z")) to "/match/brynas-if-farjestad-bk-1896219",
            BroadcastFixture("SHL", "Växjö Lakers", "Färjestad BK", java.time.LocalDate.parse("2026-10-31"), Instant.parse("2026-10-31T14:15:00Z")) to "/match/vaxjo-lakers-farjestad-bk-1893256")
        examples.forEachIndexed { i, (fixture, path) ->
            val snapshot = File(directory, "tvmatchen-example-$i.html")
            if (snapshot.exists()) {
                val result = runCatching { TvmatchenHtml.assignment(snapshot.readText(), path, fixture, now) }
                println("PUBLIC_SNAPSHOT fixture=$i " + result.fold({ "channels=" + it?.channels }, { "rejected_${it.javaClass.simpleName}_line_${it.stackTrace.firstOrNull { frame -> frame.fileName == "TvmatchenHtml.kt" }?.lineNumber}" }))
            } else println("PUBLIC_SNAPSHOT fixture=$i unavailable_within_bound")
        }
    }
}
