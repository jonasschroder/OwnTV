package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.sporthub.*
import tv.own.owntv.ui.format.formatBestDateTime
import tv.own.owntv.ui.theme.mpx
import java.time.Instant

/** NHL/football/etc use the same first-class hero, never an unrelated SHL setup message. */
@Composable
internal fun NativePublicSportHero(value: HubOverviewMatch?, teams: List<HubFollow>, preferences: SportPreferences,
    now: Long, nav: NativeRowsNavigation, onMatch: ((HubMatch) -> Unit)?, onMine: (() -> Unit)?, onExplore: (() -> Unit)?) {
    val game=value?.match
    val league=game?.league?.let(HubLeagues::find)
    Row(Modifier.fillMaxWidth().heightIn(min=268.mpx).clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(26.mpx).testTag("sport-public-hero"),
        horizontalArrangement=Arrangement.spacedBy(32.mpx),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.mpx)) {
            TvText(league?.let { stringResource(it.label) } ?: stringResource(R.string.hub_my_teams),size=20,color=MinTvTeal,bold=true)
            TvText(game?.let { stringResource(R.string.hub_pair,it.home.name,it.away.name) } ?: teams.firstOrNull()?.name.orEmpty(),size=42,bold=true,lines=2)
            if (game != null) TvText(formatBestDateTime(LocalContext.current,"dMMM",game.startsAt.toEpochMilli()),size=23,color=MinTvMuted)
            else TvText(stringResource(R.string.hub_on_demand),size=23,color=MinTvMuted,lines=3)
            if (game?.score != null && !preferences.hideScores) TvText(stringResource(R.string.hub_score,game.score.home,game.score.away),size=32,bold=true)
        }
        Column(Modifier.width(660.mpx),verticalArrangement=Arrangement.spacedBy(18.mpx)) {
            TvText(stringResource(R.string.hub_manual_unknown),size=22,color=MinTvMuted,lines=3)
            if (value != null && (value.source.freshness(Instant.ofEpochMilli(now)) != HubFreshness.FRESH || value.source.snapshot?.coverage?.isNotEmpty() == true))
                TvText(stringResource(R.string.hub_partial),size=18,color=MinTvMuted,lines=3)
            Row(horizontalArrangement=Arrangement.spacedBy(12.mpx)) {
                SmartAction(stringResource(if (game != null) R.string.hub_match else R.string.hub_my_teams),
                    { if (game != null) onMatch?.invoke(game) else onMine?.invoke() },nav.item("sport-hero-action").testTag("sport-hero-action"),primary=true)
                if (game != null) SmartAction(stringResource(R.string.hub_my_teams),{onMine?.invoke()},nav.item("sport-hero-teams").testTag("sport-hero-teams"),compact=true)
                onExplore?.let { SmartAction(stringResource(R.string.hub_explore),it,nav.item("sport-hero-explore").testTag("sport-hero-explore"),compact=true) }
            }
        }
    }
}
