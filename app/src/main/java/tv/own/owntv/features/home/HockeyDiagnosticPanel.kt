package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

private data class HockeyDiagnosticStatus(val league: Competition, val event: HockeyTrace?, val attempt: Long?, val success: Long?, val cachedCount: Int)

/** Observes only local preference changes while composed; no polling, HTTP or background refresh. */
@Composable
internal fun HockeyDiagnosticPanel(repository: ShlRepository, broadcasts: TvmatchenBroadcastSource) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<List<HockeyDiagnosticStatus>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(repository) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("hockey-trace:") == true || key in listOf("shl-enabled", "shl-access-blocked", "broadcast-enabled", "broadcast-blocked")) revision++
        }
        repository.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { repository.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var exported by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    LaunchedEffect(repository, revision) {
        status = withContext(Dispatchers.IO) { listOf(SportsCatalog.shl, SportsCatalog.allsvenskan).map { league ->
            val events = repository.diagnostics.events(league)
            HockeyDiagnosticStatus(league, events.lastOrNull { it.outcome !in listOf(HockeyOutcome.CACHE_HIT, HockeyOutcome.CACHE_MISS) },
                repository.diagnostics.lastAttempt(league), repository.diagnostics.lastSuccess(league), repository.diagnostics.cachedCount(league))
        } }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        TvText(stringResource(R.string.mintv_hockey_latest_update), size = 26, bold = true)
        status.forEach { (league, event, attempt, success, cachedCount) ->
            FocusPanel {
                TvText(league.name, bold = true)
                val outcome = when {
                    !repository.preferences.getBoolean("shl-enabled", false) -> HockeyOutcome.DISABLED
                    repository.preferences.getBoolean("shl-access-blocked", false) -> HockeyOutcome.ACCESS_BLOCKED
                    else -> event?.outcome
                }
                TvText(stringResource(hockeyOutcomeLabel(outcome)), lines = 3)
                TvText(stringResource(R.string.mintv_hockey_last_attempt, attempt?.let { stockholmDate(it) + " " + stockholmTime(it) }
                    ?: stringResource(R.string.mintv_companion_unavailable)), color = MinTvMuted, lines = 2)
                TvText(stringResource(R.string.mintv_hockey_last_success, success?.let { stockholmDate(it) + " " + stockholmTime(it) }
                    ?: stringResource(R.string.mintv_companion_unavailable)), color = MinTvMuted, lines = 2)
                TvText(stringResource(R.string.mintv_hockey_cached_count, cachedCount), color = MinTvMuted)
                event?.http?.let { TvText(stringResource(R.string.mintv_hockey_http_stage, it, event.stage.name), color = MinTvMuted) }
                event?.retryAt?.let { TvText(stringResource(R.string.mintv_sports_retry_at, stockholmTime(it)), color = MinTvMuted) }
            }
        }
        val lookup by broadcasts.diagnostics.collectAsStateWithLifecycle()
        val outcome = when {
            !repository.preferences.getBoolean("shl-enabled", false) || !repository.preferences.getBoolean("broadcast-enabled", false) -> BroadcastLookupOutcome.DISABLED
            broadcasts.blocked -> BroadcastLookupOutcome.ACCESS_BLOCKED
            else -> lookup.outcome
        }
        FocusPanel {
            TvText(stringResource(R.string.mintv_broadcast_diagnostics), bold = true)
            TvText(stringResource(broadcastOutcomeLabel(outcome)), lines = 3)
            lookup.at?.let { TvText(stringResource(R.string.mintv_updated, updateLabel(it, System.currentTimeMillis())), color = MinTvMuted) }
            TvText(stringResource(R.string.mintv_broadcast_stage, lookup.stage.name), color = MinTvMuted)
            lookup.http?.let { TvText(stringResource(R.string.mintv_hockey_http_stage, it, lookup.stage.name), color = MinTvMuted) }
            lookup.retryAt?.let { TvText(stringResource(R.string.mintv_sports_retry_at, stockholmTime(it)), color = MinTvMuted) }
        }
        HomeButton(stringResource(R.string.mintv_hockey_export), { scope.launch {
            val path = withContext(Dispatchers.IO) { SportsDiagnostics.export(context, repository.diagnostics.report()) }
            exported = path != null; exportFailed = path == null
        } })
        if (exported) TvText(stringResource(R.string.mintv_hockey_export_saved), color = MinTvTeal, lines = 2)
        if (exportFailed) TvText(stringResource(R.string.mintv_hockey_export_failed), color = MinTvMuted, lines = 2)
    }
}

internal fun hockeyOutcomeLabel(outcome: HockeyOutcome?): Int = when (outcome) {
    HockeyOutcome.DISABLED -> R.string.mintv_hockey_status_disabled
    HockeyOutcome.ACCESS_BLOCKED -> R.string.mintv_hockey_status_blocked
    HockeyOutcome.BACKOFF -> R.string.mintv_hockey_status_backoff
    HockeyOutcome.RATE_LIMITED -> R.string.mintv_hockey_status_limited
    HockeyOutcome.TIMEOUT -> R.string.mintv_hockey_status_timeout
    HockeyOutcome.NETWORK_ERROR -> R.string.mintv_hockey_status_network
    HockeyOutcome.FORMAT_ERROR -> R.string.mintv_hockey_status_format
    HockeyOutcome.SEASON_MISSING -> R.string.mintv_hockey_status_season
    HockeyOutcome.VALIDATION_REJECTED -> R.string.mintv_hockey_status_rejected
    HockeyOutcome.CACHE_WRITE_FAILED -> R.string.mintv_hockey_status_cache_write
    HockeyOutcome.CACHE_REJECTED -> R.string.mintv_hockey_status_cache_invalid
    HockeyOutcome.EMPTY_SUCCESS -> R.string.mintv_hockey_status_empty
    HockeyOutcome.VALID_SUCCESS, HockeyOutcome.PUBLISHED -> R.string.mintv_hockey_status_success
    HockeyOutcome.HTTP_RESPONSE -> R.string.mintv_hockey_status_http
    HockeyOutcome.CANCELLED -> R.string.mintv_hockey_status_cancelled
    HockeyOutcome.REQUESTED -> R.string.mintv_sports_loading
    else -> R.string.mintv_hockey_status_not_requested
}

internal fun broadcastOutcomeLabel(outcome: BroadcastLookupOutcome): Int = when (outcome) {
    BroadcastLookupOutcome.OUTSIDE_WINDOW -> R.string.mintv_broadcast_outside_window
    BroadcastLookupOutcome.INVALID_FIXTURE -> R.string.mintv_hockey_status_rejected
    BroadcastLookupOutcome.NO_LINEAR_CHANNEL -> R.string.mintv_broadcast_no_linear
    BroadcastLookupOutcome.NOT_FOUND -> R.string.mintv_broadcast_not_found
    BroadcastLookupOutcome.AMBIGUOUS -> R.string.mintv_broadcast_ambiguous
    BroadcastLookupOutcome.FIELDS_REJECTED -> R.string.mintv_broadcast_fields_rejected
    BroadcastLookupOutcome.DISABLED -> R.string.mintv_v0111_broadcast_disabled
    BroadcastLookupOutcome.ACCESS_BLOCKED -> R.string.mintv_broadcast_blocked
    BroadcastLookupOutcome.BACKOFF -> R.string.mintv_hockey_status_backoff
    BroadcastLookupOutcome.RATE_LIMITED -> R.string.mintv_hockey_status_limited
    BroadcastLookupOutcome.CACHE_HIT, BroadcastLookupOutcome.VERIFIED -> R.string.mintv_hockey_status_success
    BroadcastLookupOutcome.HTTP_ERROR -> R.string.mintv_hockey_status_http
    BroadcastLookupOutcome.NETWORK_ERROR -> R.string.mintv_hockey_status_network
    BroadcastLookupOutcome.FORMAT_ERROR -> R.string.mintv_hockey_status_format
    BroadcastLookupOutcome.CACHE_ERROR -> R.string.mintv_hockey_status_cache_write
    BroadcastLookupOutcome.CANCELLED -> R.string.mintv_hockey_status_cancelled
    BroadcastLookupOutcome.NOT_REQUESTED -> R.string.mintv_hockey_status_not_requested
}
