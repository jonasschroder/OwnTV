package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
/** A Home picker override is scoped to this actual watch; global automatic preferences cannot replace history. */
internal fun homeHeroChannel(watched: ChannelEntity?, alternatives: List<ChannelEntity>, explicitKey: String?): ChannelEntity? =
    watched?.let { alternatives.firstOrNull { explicitKey != null && it.homeEpgKey().stableKey == explicitKey } ?: it }
internal fun ChannelEntity.homeEpgKey() = SwedishRowKey(id, sourceId, CustomizeKeys.channel(this))
/** Lazy item state is persisted in a Bundle; the EPG cache's plain data-class key is not saveable. */
internal fun ChannelEntity.homeLazyItemKey(): String = homeEpgKey().let { "${it.sourceId}:${it.id}:${it.stableKey}" }

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
    modifier: Modifier = Modifier, statusWidth: Dp = 760.mpx,
    header: @Composable () -> Unit, section: @Composable (HomeSection) -> Unit) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(60.mpx) }
    BoxWithConstraints(modifier.background(MinTvNavy).focusGroup()) {
        val besideStatus = maxWidth - contentStart - statusWidth - 24.mpx >= 640.mpx
        val headerTop = if (besideStatus) 34.mpx else reservedTop
        val contentTop = maxOf(reservedTop, headerTop + headerHeight + 20.mpx)
        LazyColumn(state = list,
            contentPadding = PaddingValues(start = contentStart, end = 56.mpx, top = 8.mpx, bottom = 60.mpx),
            verticalArrangement = Arrangement.spacedBy(18.mpx),
            modifier = Modifier.fillMaxSize().homeContentViewport(contentTop)) {
            // A zero-height scroll anchor preserves existing section indices for the fixed header.
            item(key = Int.MIN_VALUE) { }
            rows.forEach { row -> item(key = row) { section(row) } }
        }
        Box(Modifier.fillMaxWidth().padding(start = contentStart, end = if (besideStatus) statusWidth + 24.mpx else 56.mpx, top = headerTop)
            .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) { header() }
    }
}

@Composable
internal fun HomeDashboardHeader(profile: String?, update: MinTvUpdater.State, onUpdate: () -> Unit,
    onCustomize: () -> Unit, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.mpx), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
        Column(Modifier.weight(1f)) {
            TvText(stringResource(R.string.app_name), size = 28, bold = true, lines = 1)
            profile?.let { TvText(it, size = 16, color = MinTvMuted, lines = 1) }
        }
        DashboardAction(stringResource(R.string.mintv_v08_customize_short), onCustomize, compact = true)
        Column(horizontalAlignment = Alignment.End) {
            DashboardAction(stringResource(R.string.mintv_v08_version_chip, BuildConfig.VERSION_NAME), onUpdate, modifier, compact = true)
            if (update is MinTvUpdater.State.Available) TvText(stringResource(R.string.mintv_v08_update_available),
                size = 17, color = MinTvTeal, lines = 1)
        }
    }
}

@Composable
internal fun DashboardAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, compact: Boolean = false) {
    // Explicit height overrides Material's larger default minimum; grow with the user's font scale.
    val height = (if (compact) 42 else 50).mpx * maxOf(1f, LocalDensity.current.fontScale)
    androidx.tv.material3.Button(onClick = onClick, modifier = modifier.height(height),
        contentPadding = PaddingValues(horizontal = if (compact) 14.mpx else 18.mpx, vertical = if (compact) 7.mpx else 10.mpx),
        shape = androidx.tv.material3.ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.mpx)),
        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (primary) MinTvTeal else MinTvPanel,
            contentColor = if (primary) MinTvNavy else androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = if (primary) androidx.compose.ui.graphics.Color(0xFFE7FFFF) else androidx.compose.ui.graphics.Color(0xFF274555)),
        border = androidx.tv.material3.ButtonDefaults.border(focusedBorder = androidx.tv.material3.Border(
            androidx.compose.foundation.BorderStroke(2.mpx, MinTvTeal))),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1f)) {
        TvText(label, size = if (compact) 17 else 20, lines = 1, bold = primary, color = if (primary) MinTvNavy else androidx.compose.ui.graphics.Color.White)
    }
}

/** Real stored Now/Next only; the tile remains playable when its guide is missing. */
@Composable
internal fun HomeNowChannelCard(name: String, logo: String?, quality: ChannelQuality?, info: SwedishRowInfo?,
    at: Long, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val guide = info?.at(at)
    TvCard(onPlay, modifier.heightIn(min = 220.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            logo?.let { coil3.compose.AsyncImage(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.size(42.mpx)) }
            TvText(name, Modifier.weight(1f), size = 24, bold = true, lines = 1)
            quality?.takeIf { it != ChannelQuality.UNKNOWN }?.let { TvText(it.name, size = 15, color = MinTvMuted, lines = 1) }
        }
        TvText(guide?.now?.title ?: stringResource(R.string.mintv_v08_empty_now), size = 22, lines = 2)
        guide?.now?.let { programme ->
            TvText(homeProgrammeTime(programme.startMs, programme.stopMs), size = 17, color = MinTvMuted, lines = 1)
            ProgrammeProgress(programme.progress(at))
        }
        guide?.next?.let { TvText(stringResource(R.string.mintv_next, it.title), size = 17, color = MinTvMuted, lines = 1) }
    }
}
