package tv.own.owntv.features.update

import kotlinx.coroutines.launch

/** Presentation policy only; authentication, compatibility and installer authorization remain in the manager. */
internal enum class UpdateAction { CHECK, DOWNLOAD, INSTALL, PERMISSION, CONFIRM, NONE }
internal enum class UpdateStep { DISCOVER, REVIEW, DOWNLOAD, VERIFY, PERMISSION, CONFIRM, DONE, ISSUE }
internal data class UpdateExperience(val step: UpdateStep, val action: UpdateAction, val busy: Boolean = false)
internal fun updateExperience(state: MinTvUpdater.State, cached: Boolean = false): UpdateExperience = when (state) {
    MinTvUpdater.State.Idle, MinTvUpdater.State.UpToDate -> UpdateExperience(UpdateStep.DISCOVER, UpdateAction.CHECK)
    MinTvUpdater.State.Checking -> UpdateExperience(UpdateStep.DISCOVER, UpdateAction.NONE, true)
    is MinTvUpdater.State.Available -> UpdateExperience(UpdateStep.REVIEW, if (cached) UpdateAction.INSTALL else UpdateAction.DOWNLOAD)
    is MinTvUpdater.State.Downloading -> UpdateExperience(UpdateStep.DOWNLOAD, UpdateAction.NONE, true)
    MinTvUpdater.State.Verifying -> UpdateExperience(UpdateStep.VERIFY, UpdateAction.NONE, true)
    MinTvUpdater.State.Permission -> UpdateExperience(UpdateStep.PERMISSION, UpdateAction.PERMISSION)
    MinTvUpdater.State.Confirmation -> UpdateExperience(UpdateStep.CONFIRM, UpdateAction.CONFIRM)
    MinTvUpdater.State.Installing -> UpdateExperience(UpdateStep.CONFIRM, UpdateAction.NONE, true)
    MinTvUpdater.State.Installed -> UpdateExperience(UpdateStep.DONE, UpdateAction.CHECK)
    MinTvUpdater.State.Cancelled, is MinTvUpdater.State.Failed -> UpdateExperience(UpdateStep.ISSUE, UpdateAction.CHECK)
}
internal fun checkUpdateOnEntry(state: MinTvUpdater.State) = state == MinTvUpdater.State.Idle ||
    state == MinTvUpdater.State.UpToDate || state is MinTvUpdater.State.Failed || state == MinTvUpdater.State.Cancelled
internal fun updateRequiredSpace(bytes: Long): Long {
    require(bytes in 1..UpdatePolicy.MAX_APK)
    return bytes * 2 + 32 * 1024 * 1024L
}

/** Coalesce repeated remote OK presses even while the manager waits for its resume/check mutex. */
internal class UpdateTransferActions(private val scope: kotlinx.coroutines.CoroutineScope,
    private val state: () -> MinTvUpdater.State, private val transfer: suspend () -> Unit) {
    private var pending: kotlinx.coroutines.Job? = null
    fun request() {
        if (state() !is MinTvUpdater.State.Available || pending?.isActive == true) return
        pending = scope.launch { if (state() is MinTvUpdater.State.Available) transfer() }
    }
}
