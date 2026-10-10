package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

internal data class SportsLoadState(val loading: Boolean = false, val issue: HockeyIssue? = null, val cached: Boolean = false)
internal fun sportsIssue(error: Exception, now: Long) = (error as? HockeyDataException)?.issue
    ?: HockeyIssue(HockeyFailureKind.CACHE, HockeyStage.CACHE, now)
internal fun sportsActive(resumed: Boolean, homeDestination: Boolean) = resumed && homeDestination
internal fun sportsSnapshotFresh(snapshot: SportSnapshot?, state: SportsLoadState?, now: Long) =
    snapshot != null && state?.issue == null && now - snapshot.fetchedAt in 0 until 6 * 60 * 60_000L
internal fun retryReady(issue: HockeyIssue?, now: Long) = issue?.retryable == true && now >= (issue.retryAt ?: 0)

@Composable
internal fun sportsStatusText(competition: Competition, enabled: Boolean, state: SportsLoadState): String = stringResource(when {
    !competition.scheduleAvailable -> R.string.mintv_schedule_unavailable
    !enabled || state.issue?.kind == HockeyFailureKind.DISABLED -> R.string.mintv_shl_disabled
    state.loading -> R.string.mintv_companion_loading
    state.issue != null -> when (state.issue.kind) {
        HockeyFailureKind.ACCESS -> R.string.mintv_sports_access
        HockeyFailureKind.NETWORK, HockeyFailureKind.HTTP -> R.string.mintv_sports_network
        HockeyFailureKind.FORMAT -> R.string.mintv_sports_format
        HockeyFailureKind.RATE_LIMITED -> R.string.mintv_sports_limited
        HockeyFailureKind.CACHE -> R.string.mintv_sports_storage
        HockeyFailureKind.DISABLED -> R.string.mintv_shl_disabled
    }
    else -> R.string.mintv_sports_no_scheduled
})

/** One useful TV-sized state; HTTP detail belongs only in Settings, never a match card. */
@Composable
internal fun SportsStatusPanel(competition: Competition, enabled: Boolean, state: SportsLoadState,
    now: Long, timestamp: Long?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.mpx), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.mpx)) {
        TvText(sportsStatusText(competition, enabled, state), size = 27, lines = 3)
        if (timestamp != null) TvText(stringResource(R.string.mintv_cached_updated, updateLabel(timestamp, now)), color = MinTvMuted, size = 21)
        if (enabled && competition.scheduleAvailable && state.issue?.retryable == true) {
            if (retryReady(state.issue, now)) HomeButton(stringResource(R.string.mintv_sports_retry), onRetry)
            else TvText(stringResource(R.string.mintv_sports_retry_at, stockholmTime(state.issue.retryAt!!)), size = 21, color = MinTvMuted)
        }
    }
}
