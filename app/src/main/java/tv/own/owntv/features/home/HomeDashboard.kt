package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.features.live.*
import tv.own.owntv.features.update.MinTvUpdater
import tv.own.owntv.ui.theme.mpx

/** No selection/focus state is allowed to manufacture a playback-history item. */
internal fun homeHistoryCandidate(state: HomeUiState, profile: Long?, sources: List<Long>): ChannelEntity? =
    state.recentLive.firstOrNull { state.profileId == profile && it.sourceId in sources && it.streamUrl.isNotBlank() }
internal fun ChannelEntity.homeEpgKey() = SwedishRowKey(id, sourceId, CustomizeKeys.channel(this))

internal fun dashboardIndices(rows: List<HomeSection>, showSport: Boolean, showTwitch: Boolean): List<Int> {
    val result = MutableList(10) { -1 }
    result[9] = 0 // header
    rows.forEachIndexed { index, section ->
        val item = index + 1
        when (section) {
            HomeSection.LIVE -> result[0] = item
            HomeSection.SPORT -> { if (showSport) result[3] = item; if (showTwitch) result[6] = item }
            HomeSection.TWITCH -> Unit // rendered beside hockey at the same physical row
            HomeSection.FAVORITES -> result[1] = item
            HomeSection.CONTINUE -> result[2] = item
            HomeSection.NOW -> result[8] = item
            HomeSection.MOVIES -> result[4] = item
            HomeSection.SERIES -> result[5] = item
            HomeSection.APPS -> result[7] = item
        }
    }
    return result
}

/** Real lazy dashboard frame shared by production and synthetic emulator fixtures. No data/player owner. */
@Composable
internal fun HomeDashboardFrame(list: LazyListState, rows: List<HomeSection>, contentStart: Dp, reservedTop: Dp,
    modifier: Modifier = Modifier, header: @Composable () -> Unit, section: @Composable (HomeSection) -> Unit) {
    LazyColumn(state = list,
        contentPadding = PaddingValues(start = contentStart, end = 56.mpx, top = 8.mpx, bottom = 60.mpx),
        verticalArrangement = Arrangement.spacedBy(26.mpx),
        modifier = modifier.homeContentViewport(reservedTop).background(MinTvNavy).focusGroup()) {
        item(key = Int.MIN_VALUE) { header() }
        rows.forEach { row -> item(key = row) { section(row) } }
    }
}

@Composable
internal fun HomeDashboardHeader(profile: String?, update: MinTvUpdater.State, onUpdate: () -> Unit,
    onCustomize: () -> Unit, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.mpx), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
        Column(Modifier.weight(1f)) {
            TvText(stringResource(R.string.app_name), size = 31, bold = true, lines = 1)
            profile?.let { TvText(it, size = 17, color = MinTvMuted, lines = 1) }
        }
        DashboardAction(stringResource(R.string.mintv_v08_customize), onCustomize)
        Column(horizontalAlignment = Alignment.End) {
            DashboardAction(stringResource(R.string.mintv_v08_version,
                stringResource(R.string.app_name), BuildConfig.VERSION_NAME), onUpdate, modifier)
            if (update is MinTvUpdater.State.Available) TvText(stringResource(R.string.mintv_v08_update_available),
                size = 17, color = MinTvTeal, lines = 1)
        }
    }
}

@Composable
internal fun DashboardAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    androidx.tv.material3.Button(onClick = onClick, modifier = modifier.heightIn(min = 50.mpx),
        contentPadding = PaddingValues(horizontal = 18.mpx, vertical = 10.mpx),
        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (primary) MinTvTeal else MinTvPanel,
            contentColor = if (primary) MinTvNavy else androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = androidx.compose.ui.graphics.Color(0xFF274555)),
        border = androidx.tv.material3.ButtonDefaults.border(focusedBorder = androidx.tv.material3.Border(
            androidx.compose.foundation.BorderStroke(3.mpx, MinTvTeal))),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f)) {
        TvText(label, size = 20, lines = 1, bold = primary, color = if (primary) MinTvNavy else androidx.compose.ui.graphics.Color.White)
    }
}
