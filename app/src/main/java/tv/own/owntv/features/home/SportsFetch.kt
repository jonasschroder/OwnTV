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
    suspend fun report(issue: HockeyIssue) {
        try { onIssue(issue) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { SportsDiagnostics.failure(error, HockeyStage.CACHE) }
    }
    state(result.copy(loading = true))
    SportsDiagnostics.record(SportsDiagnostics.Event.LOADING, stage)
    try {
        result = withTimeout(timeoutMillis) {
            val value = operation()
            publishing = true
            SportsLoadState(cached = if (value != null) accept(value) else hasCache())
        }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive() // Parent/lifecycle cancellation is not a source error.
        result = SportsLoadState(issue = HockeyIssue(HockeyFailureKind.TIMEOUT, if (publishing) HockeyStage.PUBLICATION else stage, now,
            retryAt = now + 2 * 60_000), cached = hasCache())
        report(result.issue!!)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        SportsDiagnostics.failure(error, if (publishing) HockeyStage.PUBLICATION else stage)
        val issue = if (publishing) HockeyIssue(HockeyFailureKind.CACHE, HockeyStage.PUBLICATION, now) else sportsIssue(error, now)
        result = SportsLoadState(issue = issue.copy(retryAt = issue.retryAt ?:
            (now + 2 * 60_000).takeIf { issue.retryable }), cached = hasCache())
        if (publishing || error !is HockeyDataException) report(result.issue!!)
    } finally {
        SportsDiagnostics.record(when {
            !currentCoroutineContext().isActive -> SportsDiagnostics.Event.CANCELLED
            result.issue != null -> SportsDiagnostics.Event.FAILURE
            else -> SportsDiagnostics.Event.DATA
        }, stage)
        state(result)
    }
    return result
}
