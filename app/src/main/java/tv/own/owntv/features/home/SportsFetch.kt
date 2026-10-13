package tv.own.owntv.features.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

/** Includes cache/mutex waits and the complete fetch. Never leaves a cancelled screen loading. */
internal suspend fun <T> fetchSports(
    now: Long,
    stage: HockeyStage,
    hasCache: () -> Boolean,
    operation: suspend () -> T?,
    accept: suspend (T) -> Boolean,
    state: (SportsLoadState) -> Unit,
    timeoutMillis: Long = 35_000,
    onIssue: suspend (HockeyIssue) -> Unit = {},
): SportsLoadState {
    var result = SportsLoadState(cached = hasCache())
    var publishing = false
    var publicationFailed = false
    state(result.copy(loading = true))
    SportsDiagnostics.record(SportsDiagnostics.Event.LOADING, stage)
    try {
        val value = withTimeout(timeoutMillis) { operation() }
        publishing = true
        result = SportsLoadState(cached = if (value != null) accept(value) else hasCache())
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive() // Parent/lifecycle cancellation is not a source error.
        result = SportsLoadState(issue = HockeyIssue(HockeyFailureKind.TIMEOUT, stage, now,
            retryAt = now + 2 * 60_000), cached = hasCache())
        onIssue(result.issue!!)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        SportsDiagnostics.failure(error, stage)
        if (publishing) { publicationFailed = true; throw error } // Surface publication failures instead of misclassifying them as source errors.
        val issue = sportsIssue(error, now)
        result = SportsLoadState(issue = issue.copy(retryAt = issue.retryAt ?:
            (now + 2 * 60_000).takeIf { issue.retryable }), cached = hasCache())
        if (error !is HockeyDataException) onIssue(result.issue!!)
    } finally {
        SportsDiagnostics.record(when {
            !currentCoroutineContext().isActive -> SportsDiagnostics.Event.CANCELLED
            result.issue != null || publicationFailed -> SportsDiagnostics.Event.FAILURE
            else -> SportsDiagnostics.Event.DATA
        }, stage)
        state(result)
    }
    return result
}
