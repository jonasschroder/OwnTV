package tv.own.owntv.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

/** Compact production summary. Provenance labels never promote a manual choice to confirmation. */
@Composable
internal fun HockeyHomeSummary(game: SportFixture?, now: Long, channelName: String?, evidence: HockeyChannelEvidence,
    epgTime: Boolean, loading: Boolean, failed: Boolean, cachedAt: Long? = null) {
    if (game != null) {
        TvText(pairLabel(game), size = 25, bold = true, lines = 1)
        TvText(fixtureWhen(game, now), size = 21, color = MinTvMuted)
    } else if (loading) TvText(stringResource(R.string.mintv_hockey_epg_loading), color = MinTvMuted, size = 22)
    if (channelName != null) {
        TvText(channelName, color = MinTvTeal, size = 22, bold = true)
        TvText(stringResource(when (evidence) {
            HockeyChannelEvidence.MANUAL -> R.string.mintv_hockey_manual_evidence
            HockeyChannelEvidence.SPECIFIC_EPG -> R.string.mintv_hockey_epg_evidence
            HockeyChannelEvidence.BROADCAST_SCHEDULE -> R.string.mintv_hockey_broadcast_evidence
            else -> R.string.mintv_hockey_channel_unknown
        }), color = MinTvMuted, size = 17)
    } else if (!loading || game != null) TvText(stringResource(R.string.mintv_hockey_channel_unknown), color = MinTvMuted, size = 18)
    if (epgTime) TvText(stringResource(R.string.mintv_hockey_epg_time), color = MinTvMuted, size = 17)
    if (failed) TvText(stringResource(R.string.mintv_hockey_epg_failed), color = MinTvMuted, size = 18)
    if (cachedAt != null) TvText(stringResource(R.string.mintv_cached_updated, updateLabel(cachedAt, now)), color = MinTvMuted, size = 18, lines = 1)
}
