package tv.own.owntv.features.update

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException

class UpdateExperienceTest {
    private val candidate = UpdatePolicy.Candidate("qa", "se.jonasschroder.mintv.qa", 1000099, "0.16.0-test-fixture",
        "synthetic.apk", "0".repeat(64), 42_000_000, 28, setOf("arm64-v8a"), "Synthetic notes", "qa-1000099")
    @Test fun reviewDownloadVerifyAndSystemConsentHaveDistinctActions() {
        val review = MinTvUpdater.State.Available(candidate)
        assertEquals(UpdateAction.DOWNLOAD, updateExperience(review).action)
        assertEquals(UpdateAction.INSTALL, updateExperience(review, cached = true).action)
        for (state in listOf(MinTvUpdater.State.Checking, MinTvUpdater.State.Downloading(25, 10_500_000, 42_000_000), MinTvUpdater.State.Verifying, MinTvUpdater.State.Installing)) {
            assertTrue(updateExperience(state).busy)
            assertEquals(UpdateAction.NONE, updateExperience(state).action)
        }
        assertEquals(UpdateAction.PERMISSION, updateExperience(MinTvUpdater.State.Permission).action)
        assertEquals(UpdateAction.CONFIRM, updateExperience(MinTvUpdater.State.Confirmation).action)
    }
    @Test fun reopeningVerifiedOrActiveWorkDoesNotStartAnotherCheck() {
        for (state in listOf(MinTvUpdater.State.Available(candidate), MinTvUpdater.State.Available(candidate, interrupted = true),
            MinTvUpdater.State.Downloading(40), MinTvUpdater.State.Verifying, MinTvUpdater.State.Permission,
            MinTvUpdater.State.Confirmation, MinTvUpdater.State.Installing, MinTvUpdater.State.Installed)) {
            assertFalse(checkUpdateOnEntry(state))
        }
        assertTrue(checkUpdateOnEntry(MinTvUpdater.State.Idle))
        assertTrue(checkUpdateOnEntry(MinTvUpdater.State.Failed(MinTvUpdater.Problem.NETWORK)))
    }
    @Test fun interruptionRequiresExplicitRestartAndNeverClaimsRangeResume() {
        val state = MinTvUpdater.State.Available(candidate, interrupted = true)
        assertEquals(UpdateStep.REVIEW, updateExperience(state).step)
        assertEquals(UpdateAction.DOWNLOAD, updateExperience(state).action)
        assertEquals(UpdateAction.INSTALL, updateExperience(state, cached = true).action)
        assertFalse(checkUpdateOnEntry(state))
    }
    @Test fun storageBudgetPreservesFullTemporaryAndVerifiedCopies() {
        assertEquals(42_000_000L * 2 + 32 * 1024 * 1024, updateRequiredSpace(candidate.bytes))
        assertTrue(runCatching { updateRequiredSpace(0) }.isFailure)
        assertTrue(runCatching { updateRequiredSpace(UpdatePolicy.MAX_APK + 1) }.isFailure)
    }
    @Test fun failuresAndInstalledReceiptNeverInstallAutomatically() {
        for (problem in MinTvUpdater.Problem.entries) assertEquals(UpdateAction.CHECK, updateExperience(MinTvUpdater.State.Failed(problem)).action)
        assertEquals(UpdateStep.DONE, updateExperience(MinTvUpdater.State.Installed).step)
        assertEquals(UpdateAction.CHECK, updateExperience(MinTvUpdater.State.Installed).action)
    }
    @Test fun rapidOkWhileQueuedOrDownloadingStartsOneTransfer() = runTest {
        var state: MinTvUpdater.State = MinTvUpdater.State.Available(candidate)
        val finish = CompletableDeferred<Unit>(); var starts = 0
        val gate = UpdateTransferActions(this, { state }) { starts++; state = MinTvUpdater.State.Downloading(0); finish.await() }
        gate.request(); gate.request(); runCurrent(); gate.request(); runCurrent()
        assertEquals(1, starts)
        finish.complete(Unit); runCurrent()
        state = MinTvUpdater.State.Confirmation; gate.request(); runCurrent(); assertEquals(1, starts)
    }
    @Test fun cancelledTransferNeedsAnotherExplicitRequest() = runTest {
        var state: MinTvUpdater.State = MinTvUpdater.State.Available(candidate); var starts = 0
        val gate = UpdateTransferActions(this, { state }) {
            starts++; state = MinTvUpdater.State.Available(candidate, interrupted = true); throw CancellationException()
        }
        gate.request(); runCurrent(); assertEquals(1, starts)
        runCurrent(); assertEquals(1, starts)
        gate.request(); runCurrent(); assertEquals(2, starts)
    }
    @Test fun changedStateBeforeQueuedActionPreventsTransfer() = runTest {
        var state: MinTvUpdater.State = MinTvUpdater.State.Available(candidate); var starts = 0
        val gate = UpdateTransferActions(this, { state }) { starts++ }
        gate.request(); state = MinTvUpdater.State.Idle; runCurrent(); assertEquals(0, starts)
    }
    @Test fun externallyInstalledSameOrNewerVersionCannotRestoreAnOldInstallPrompt() {
        for (installed in listOf(candidate.code, candidate.code + 1)) for (allowed in listOf(false, true)) {
            val state = restoredUpdateState(candidate, installed, downloaded = true, installAllowed = allowed, interrupted = true)
            assertEquals(MinTvUpdater.State.UpToDate, state)
            assertEquals(UpdateAction.CHECK, updateExperience(state, cached = true).action)
        }
    }
    @Test fun cacheWithoutAuthenticatedMetadataNeverRestoresPermissionOrInstallation() {
        assertEquals(MinTvUpdater.State.Idle, restoredUpdateState(null, candidate.code - 1, true, false, true))
    }
    @Test fun newerVerifiedCacheStillUsesExplicitPermissionAndInstallActions() {
        assertEquals(MinTvUpdater.State.Permission, restoredUpdateState(candidate, candidate.code - 1, true, false, false))
        assertEquals(MinTvUpdater.State.Available(candidate), restoredUpdateState(candidate, candidate.code - 1, true, true, true))
    }
    @Test fun restoredPartialDownloadRequiresAnotherExplicitRestart() {
        val state = restoredUpdateState(candidate, candidate.code - 1, false, false, true)
        assertEquals(MinTvUpdater.State.Available(candidate, interrupted = true), state)
        assertEquals(UpdateAction.DOWNLOAD, updateExperience(state).action)
    }

}
