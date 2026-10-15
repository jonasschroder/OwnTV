package tv.own.owntv.features.home

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HomeSynopsisTest {
    @Test fun synopsisReadsOnlyAuthorizedVisibleProgrammeAndRechecksRevokedAccess() = runTest {
        var reads = 0
        assertNull(homeProgrammeSynopsis({ false }, { reads++; "not accessible" }))
        assertEquals(0, reads)
        assertEquals(800, homeProgrammeSynopsis({ true }, { reads++; "x".repeat(5_000) })?.length)
        assertEquals(1, reads)
        var activeProfile = 7L
        assertNull(homeProgrammeSynopsis({ activeProfile == 7L }, { activeProfile = 8L; "old profile" }))
        assertNull(homeProgrammeSynopsis({ true }, { "  " }))
    }
    @Test fun nonCooperativeCancelledSynopsisCannotPublishAfterLeavingHome() = runTest {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var published: String? = null
        val job = launch {
            published = homeProgrammeSynopsis({ true }) {
                withContext(NonCancellable) { started.complete(Unit); release.await(); "stale programme" }
            }
        }
        started.await(); job.cancel(); release.complete(Unit); job.join()
        assertNull(published)
    }
}
