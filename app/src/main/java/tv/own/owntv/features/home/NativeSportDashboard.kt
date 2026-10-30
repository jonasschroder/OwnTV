package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.features.live.source.logicalChannelKeys

internal data class NativeSportSnapshot(val profile: Long?, val now: Long, val preferences: SportPreferences,
    val hero: SportFixture?, val today: List<SportFixture>, val upcoming: List<SportFixture>,
    val channels: List<ChannelEntity>, val broadcastLabels: Map<String, String> = emptyMap(),
    val heroChannel: ChannelEntity? = null, val loading: Boolean = false, val enabled: Boolean = true, val issue: HockeyIssue? = null,
    val playableFixtures: Map<String, ChannelEntity> = emptyMap(), val publicSourceUsed: Boolean = false,
    val channelsLoading: Boolean = false)
internal class NativeSportActions(val details: (SportFixture) -> Unit, val channel: (ChannelEntity) -> Unit,
    val browse: () -> Unit, val watch: () -> Unit, val settings: () -> Unit,
    val fixtureWatch: ((SportFixture, ChannelEntity) -> Unit)? = null)

/** Presentation only; every fixture, result and playable assignment comes from existing owners. */
@Composable
internal fun NativeSportDashboard(data: NativeSportSnapshot, actions: NativeSportActions, first: FocusRequester,
    onEntry: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit, modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val rows = buildList {
        add(NativeRow("sport-hero", if (data.hero != null) listOf("sport-hero-action") else emptyList(), horizontalRail = false))
        if (data.today.isNotEmpty()) add(NativeRow("sport-today", data.today.take(16).map { "sport-today-${it.id}" }))
        if (data.upcoming.isNotEmpty()) add(NativeRow("sport-upcoming", data.upcoming.take(12).map { "sport-upcoming-${it.id}" }))
        if (data.publicSourceUsed) add(NativeRow("sport-attribution", listOf("sport-attribution-link"), horizontalRail = false))
        if (data.channels.isNotEmpty()) add(NativeRow("sport-channels", data.channels.take(48).map { "sport-channel-${it.id}" }))
    }
    val nav = rememberNativeRowsNavigation(rows, data.profile, first, onEntry, onHeader)
    BoxWithConstraints(modifier.fillMaxSize().background(DesignLabBackground)) {
        val width = (maxWidth - 128.mpx - 54.mpx) / 4
        LazyColumn(state = nav.list, contentPadding = PaddingValues(horizontal = 64.mpx, vertical = 28.mpx),
            verticalArrangement = Arrangement.spacedBy(18.mpx), modifier = Modifier.fillMaxSize().then(nav.modifier).focusGroup()) {
            item(key = "sport-hero") {
                Column(verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                    TvText(stringResource(R.string.mintv_nav_sport), size = 34, bold = true)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(26.mpx),
                        horizontalArrangement = Arrangement.spacedBy(32.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                            TvText(data.hero?.competition?.name ?: stringResource(R.string.mintv_my_hockey), size = 20, color = MinTvTeal, bold = true)
                            TvText(data.hero?.let { pairLabel(it) } ?: sportsStatusText(SportsCatalog.shl, data.enabled, SportsLoadState(data.loading, data.issue)),
                                size = if (data.hero == null) 30 else 42, bold = true, lines = 2)
                            data.hero?.let { TvText(fixtureWhen(it, data.now), size = 23, color = MinTvMuted) }
                        }
                        Column(Modifier.width(420.mpx).background(MinTvPanel.copy(alpha = .72f), RoundedCornerShape(16.mpx)).padding(20.mpx),
                            verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                            if (data.hero != null) TvText(data.broadcastLabels[data.hero.id] ?: data.heroChannel?.name
                                ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 22, color = MinTvMuted, lines = 3)
                            if (data.channels.isEmpty()) TvText(stringResource(if (data.channelsLoading) R.string.mintv_companion_loading
                                else R.string.mintv_v011_no_hockey_channels), size = 19, color = MinTvMuted, lines = 2)
                            data.hero?.let { game ->
                                SmartAction(stringResource(if (data.heroChannel != null) R.string.mintv_watch_match else R.string.mintv_v0111_match_details),
                                    if (data.heroChannel != null) actions.watch else ({ actions.details(game) }),
                                    nav.item("sport-hero-action").testTag("sport-hero-action"), primary = true)
                            }
                        }
                    }
                }
            }
            if (data.today.isNotEmpty()) item(key = "sport-today") {
                SportFixtureRail(stringResource(R.string.mintv_today), data.today.take(16), "sport-today", data, nav, width, actions)
            }
            if (data.upcoming.isNotEmpty()) item(key = "sport-upcoming") {
                SportFixtureRail(stringResource(R.string.mintv_shl_upcoming), data.upcoming.take(12), "sport-upcoming", data, nav, width * 1.3f, actions)
            }
            if (data.publicSourceUsed) item(key = "sport-attribution") {
                HomeButton(stringResource(R.string.mintv_public_hockey_source),
                    { runCatching { uri.openUri("https://tvprogramidag.se") } }, nav.item("sport-attribution-link"))
            }
            if (data.channels.isNotEmpty()) item(key = "sport-channels") {
                Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    TvText(stringResource(R.string.mintv_v011_hockey_channels), size = 27, bold = true)
                    LazyRow(state = nav.rails.getValue("sport-channels"), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
                        items(data.channels.take(48), key = { it.id }) { channel ->
                            HomeButton(channel.name, { actions.channel(channel) }, nav.item("sport-channel-${channel.id}").width(width), logo = channel.displayLogoUrl)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SportFixtureRail(label: String, fixtures: List<SportFixture>, id: String, data: NativeSportSnapshot,
    nav: NativeRowsNavigation, width: androidx.compose.ui.unit.Dp, actions: NativeSportActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        TvText(label, size = 27, bold = true)
        LazyRow(state = nav.rails.getValue(id), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
            items(fixtures, key = { it.id }) { game ->
                val playable = data.playableFixtures[game.id]?.takeIf { game.status == MatchStatus.SCHEDULED }
                val activate = { if (playable != null && actions.fixtureWatch != null) actions.fixtureWatch.invoke(game, playable) else actions.details(game) }
                HomeSurface(activate, nav.item("$id-${game.id}")
                    .then(if (playable != null) Modifier.logicalChannelKeys(activate, { actions.details(game) }) else Modifier).width(width).heightIn(min = (if (id == "sport-upcoming") 108 else 178).mpx).testTag("$id-${game.id}")) {
                    Column(Modifier.fillMaxWidth().heightIn(min = (if (id == "sport-upcoming") 80 else 150).mpx).neutralArtwork().padding(8.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                        TvText(fixtureWhen(game, data.now), size = 18, color = MinTvMuted, lines = 1)
                        TvText(pairLabel(game), size = if (id == "sport-upcoming") 23 else 28, bold = true, lines = if (id == "sport-upcoming") 1 else 2)
                        TvText(game.result?.takeUnless { data.preferences.hideScores } ?: data.broadcastLabels[game.id]
                            ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 19, color = MinTvMuted, lines = 2)
                        if (game.result != null && !data.preferences.hideScores) TvText(stringResource(R.string.mintv_saved_result), size = 16, color = MinTvMuted)
                    }
                }
            }
        }
    }
}
