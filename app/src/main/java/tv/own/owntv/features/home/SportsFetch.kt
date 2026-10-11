package tv.own.owntv.features.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** Includes cache/mutex waits and the complete fetch. Never leaves a cancelled screen loading. */
internal suspend fun <T> fetchSports(
    now: Long,
    stage: HockeyStage,
    hasCache: () -> Boolean,
    operation: suspend () -> T?,
    accept: (T) -> Boolean,
    state: (SportsLoadState) -> Unit,
    timeoutMillis: Long = 35_000,
): SportsLoadState {
    var result = SportsLoadState(cached = hasCache())
    state(result.copy(loading = true))
    try {
        val value = withTimeout(timeoutMillis) { operation() }
        result = SportsLoadState(cached = value?.let(accept) ?: hasCache())
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive() // Parent/lifecycle cancellation is not a source error.
        result = SportsLoadState(issue = HockeyIssue(HockeyFailureKind.TIMEOUT, stage, now,
            retryAt = now + 2 * 60_000), cached = hasCache())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val issue = sportsIssue(error, now)
        result = SportsLoadState(issue = issue.copy(retryAt = issue.retryAt ?:
            (now + 2 * 60_000).takeIf { issue.retryable }), cached = hasCache())
    } finally {
        state(result)
    }
    return result
}
