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
import tv.own.owntv.features.sporthub.*
import java.time.Instant

internal sealed interface SportRailItem {
    val key: String
    val kickoff: Instant
    data class Swedish(val fixture: SportFixture): SportRailItem {
        override val key get() = fixture.id
        override val kickoff get() = fixture.kickoff
    }
    data class Public(val fixture: HubOverviewMatch): SportRailItem {
        override val key get() = "hub:" + fixture.match.id
        override val kickoff get() = fixture.match.startsAt
    }
}
internal fun sportRailItems(legacy: List<SportFixture>, extra: List<HubOverviewMatch>): List<SportRailItem> =
    if (extra.isEmpty()) legacy.distinctBy { it.id }.map { SportRailItem.Swedish(it) }
    else (legacy.map { SportRailItem.Swedish(it) } + extra.map { SportRailItem.Public(it) }).distinctBy { it.key }.sortedBy { it.kickoff }

internal data class NativeSportSnapshot(val profile: Long?, val now: Long, val preferences: SportPreferences,
    val hero: SportFixture?, val today: List<SportFixture>, val upcoming: List<SportFixture>,
    val channels: List<ChannelEntity>, val broadcastLabels: Map<String, String> = emptyMap(),
    val heroChannel: ChannelEntity? = null, val loading: Boolean = false, val enabled: Boolean = true, val issue: HockeyIssue? = null,
    val playableFixtures: Map<String, ChannelEntity> = emptyMap(), val publicSourceUsed: Boolean = false,
    val channelsLoading: Boolean = false,
    val broadcastAvailability: Map<String, HockeyChannelAvailability> = emptyMap())
internal class NativeSportActions(val details: (SportFixture) -> Unit, val channel: (ChannelEntity) -> Unit,
    val browse: () -> Unit, val watch: () -> Unit, val settings: () -> Unit,
    val fixtureWatch: ((SportFixture, ChannelEntity) -> Unit)? = null,
    val chooseChannel: ((SportFixture) -> Unit)? = null,
    val teams: () -> Unit = settings)

/** Presentation only; every fixture, result and playable assignment comes from existing owners. */
@Composable
internal fun NativeSportDashboard(data: NativeSportSnapshot, actions: NativeSportActions, first: FocusRequester,
    onEntry: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit, modifier: Modifier = Modifier, onExplore: (() -> Unit)? = null,
    hubReads: Map<String,HubRead> = emptyMap(), onHubTeam: ((HubFollow) -> Unit)? = null, onHubMatch: ((HubMatch) -> Unit)? = null,
    onHubMine: (() -> Unit)? = null) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // The featured scheduled fixture already has its own primary/detail actions.
    val featured = data.hero?.takeIf { it.status == MatchStatus.SCHEDULED }?.id
    val groups = sportDashboardGroups(data.today, data.upcoming, data.preferences, featured)
    val mine = groups.mine
    val extra = if (onHubMatch != null && data.profile != null) hubOverviewMatches(hubReads,data.preferences,Instant.ofEpochMilli(data.now)) else emptyList()
    val followed = if (onHubTeam != null && data.profile != null) data.preferences.hubTeams else emptyList()
    val personalHero = data.hero == null && followed.isNotEmpty() && onHubMine != null
    val publicHero = if (personalHero) extra.firstOrNull { it.match.startsAt.toEpochMilli() >= data.now } ?: extra.firstOrNull() else null
    val railExtra = extra.filterNot { personalHero && it.match.id == publicHero?.match?.id }
    val mineItems = sportRailItems(mine.take(12),railExtra)
    val today = groups.today
    val upcoming = groups.upcoming
    val rows = buildList {
        add(NativeRow("sport-hero", (if (personalHero) listOf("sport-hero-action") + if (publicHero != null) listOf("sport-hero-teams") else emptyList()
            else if (data.hero != null) listOf("sport-hero-action", "sport-hero-details", "sport-hero-teams")
            else listOf("sport-empty-action", "sport-hero-teams")) + if (onExplore != null) listOf("sport-hero-explore") else emptyList(), horizontalRail = false))
        if (mineItems.isNotEmpty()) add(NativeRow("sport-mine", mineItems.map { "sport-mine-${it.key}" }))
        if (followed.isNotEmpty()) add(NativeRow("sport-followed", followed.map { "sport-followed-${it.id}" }))
        if (today.isNotEmpty()) add(NativeRow("sport-today", today.take(16).map { "sport-today-${it.id}" }))
        if (upcoming.isNotEmpty()) add(NativeRow("sport-upcoming", upcoming.take(12).map { "sport-upcoming-${it.id}" }))
        if (data.publicSourceUsed) add(NativeRow("sport-attribution", listOf("sport-attribution-link"), horizontalRail = false))
        if (data.channels.isNotEmpty()) add(NativeRow("sport-channels", data.channels.take(48).map { "sport-channel-${it.id}" }))
    }
    val nav = rememberNativeRowsNavigation(rows, data.profile, first, onEntry, onHeader)
    BoxWithConstraints(modifier.fillMaxSize().background(DesignLabBackground)) {
        val width = (maxWidth - 128.mpx - 54.mpx) / 4
        LazyColumn(state = nav.list, contentPadding = PaddingValues(horizontal = 64.mpx, vertical = 28.mpx),
            verticalArrangement = Arrangement.spacedBy(24.mpx), modifier = Modifier.fillMaxSize().then(nav.modifier).focusGroup()) {
            item(key = "sport-hero") {
                Column(verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                    TvText(stringResource(R.string.mintv_nav_sport), size = 34, bold = true)
                    if (personalHero) NativePublicSportHero(publicHero,followed,data.preferences,data.now,nav,onHubMatch,onHubMine,onExplore)
                    else
                    Row(Modifier.fillMaxWidth().heightIn(min = if (data.hero != null) 268.mpx else 120.mpx).clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(26.mpx),
                        horizontalArrangement = Arrangement.spacedBy(32.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                            TvText(data.hero?.competition?.name ?: stringResource(R.string.mintv_my_hockey), size = 20, color = MinTvTeal, bold = true)
                            TvText(data.hero?.let { pairLabel(it) } ?: sportsStatusText(SportsCatalog.shl, data.enabled, SportsLoadState(data.loading, data.issue)),
                                size = if (data.hero == null) 30 else 42, bold = true, lines = 2)
                            data.hero?.let { TvText(fixtureWhen(it, data.now), size = 23, color = MinTvMuted) }
                        }
                        Column(Modifier.width((if (onExplore != null) 740 else 560).mpx).background(MinTvPanel.copy(alpha = .72f), RoundedCornerShape(16.mpx)).padding(20.mpx),
                            verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                            val teams = data.preferences.teamIds.mapNotNull { SportsCatalog.team(it)?.name }
                            if (teams.isNotEmpty()) TvText(teams.joinToString(stringResource(R.string.mintv_team_separator)), size = 18, color = MinTvMuted, lines = 2)
                            if (data.hero != null) TvText(data.broadcastLabels[data.hero.id] ?: data.heroChannel?.name
                                ?: stringResource(R.string.mintv_v08_channel_unconfirmed), size = 22, color = MinTvMuted, lines = 3)
                            if (data.channels.isEmpty()) TvText(stringResource(if (data.channelsLoading) R.string.mintv_companion_loading
                                else R.string.mintv_v011_no_hockey_channels), size = 19, color = MinTvMuted, lines = 2)
                            data.hero?.let { game ->
                                val availability = data.broadcastAvailability[game.id] ?: if (data.heroChannel != null)
                                    HockeyChannelAvailability.PLAYABLE else HockeyChannelAvailability.UNKNOWN
                                if (availability != HockeyChannelAvailability.PLAYABLE) TvText(stringResource(when (availability) {
                                    HockeyChannelAvailability.MISSING -> R.string.v016_sport_channel_missing
                                    HockeyChannelAvailability.CHOICE_REQUIRED -> R.string.v016_sport_choose_source
                                    HockeyChannelAvailability.CHECKING -> R.string.mintv_companion_loading
                                    else -> R.string.v016_sport_manual_available
                                }), size = 18, color = MinTvMuted, lines = 3)
                                val choose = { actions.chooseChannel?.invoke(game) ?: actions.details(game) }
                                val play = if (data.heroChannel != null) actions.watch else choose
                                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    SmartAction(stringResource(if (data.heroChannel != null) R.string.mintv_watch_match else R.string.v016_choose_channel),
                                        play, nav.item("sport-hero-action").testTag("sport-hero-action")
                                            .then(if (data.heroChannel != null) Modifier.logicalChannelKeys(play, { choose() }) else Modifier), primary = true)
                                    SmartAction(stringResource(R.string.v016_match_info_short), { actions.details(game) },
                                        nav.item("sport-hero-details").testTag("sport-hero-details"), compact = true)
                                    SmartAction(stringResource(R.string.mintv_choose_teams), actions.teams,
                                        nav.item("sport-hero-teams").testTag("sport-hero-teams"), compact = true)
                                    onExplore?.let { SmartAction(stringResource(R.string.hub_explore), it,
                                        nav.item("sport-hero-explore").testTag("sport-hero-explore"), compact = true) }
                                }
                                if (data.heroChannel != null) TvText(stringResource(R.string.v016_sport_hold_channel), size = 17, color = MinTvMuted, lines = 2)
                            } ?: Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                // Local settings/channel browsing need not wait for remote schedule or
                                // catalogue hydration. Keep the entry key stable across loading -> empty.
                                SmartAction(stringResource(if (data.enabled) R.string.v016_choose_channel else R.string.v016_hockey_settings),
                                    if (data.enabled) actions.browse else actions.settings,
                                    nav.item("sport-empty-action").testTag("sport-empty-action"), primary = true)
                                SmartAction(stringResource(R.string.mintv_choose_teams), actions.teams,
                                    nav.item("sport-hero-teams").testTag("sport-hero-teams"), compact = true)
                                onExplore?.let { SmartAction(stringResource(R.string.hub_explore), it,
                                    nav.item("sport-hero-explore").testTag("sport-hero-explore"), compact = true) }
                            }
                        }
                    }
                }
            }
            if (mineItems.isNotEmpty()) item(key = "sport-mine") {
                SportFixtureRail(stringResource(R.string.mintv_my_teams), mine.take(12), "sport-mine", data, nav, width * 1.3f, actions,railExtra,onHubMatch)
            }
            if (followed.isNotEmpty()) item(key = "sport-followed") {
                Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    TvText(stringResource(R.string.hub_followed),size=27,bold=true)
                    LazyRow(state=nav.rails.getValue("sport-followed"),horizontalArrangement=Arrangement.spacedBy(18.mpx),contentPadding=PaddingValues(5.mpx)) {
                        items(followed,key={it.id}) { team -> HomeSurface({ onHubTeam?.invoke(team) },
                            nav.item("sport-followed-${team.id}").width(width).testTag("sport-followed-${team.id}")) {
                            TvText(team.name,size=24,bold=true,lines=2)
                            TvText(stringResource(R.string.hub_matches),size=19,color=MinTvMuted)
                        } }
                    }
                }
            }
            if (today.isNotEmpty()) item(key = "sport-today") {
                SportFixtureRail(stringResource(R.string.mintv_today), today.take(16), "sport-today", data, nav, width, actions)
            }
            if (upcoming.isNotEmpty()) item(key = "sport-upcoming") {
                SportFixtureRail(stringResource(R.string.mintv_shl_upcoming), upcoming.take(12), "sport-upcoming", data, nav, width * 1.3f, actions)
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
                            HomeSurface({ actions.channel(channel) }, nav.item("sport-channel-${channel.id}").width(width)
                                .testTag("sport-channel-${channel.id}")) {
                                Column(Modifier.fillMaxWidth().heightIn(min = 120.mpx).padding(8.mpx),
                                    verticalArrangement = Arrangement.spacedBy(14.mpx, androidx.compose.ui.Alignment.CenterVertically)) {
                                    channel.displayLogoUrl?.let { logo -> tv.own.owntv.ui.components.ChannelLogoTile(logo,
                                        Modifier.height(42.mpx).fillMaxWidth(.5f), fill = Color.Transparent) { } }
                                    TvText(channel.name, size = 24, bold = true, lines = 2)
                                    TvText(stringResource(R.string.v016_channel_play_hint), size = 18, color = MinTvTeal, lines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun SportFixtureRail(label: String, fixtures: List<SportFixture>, id: String, data: NativeSportSnapshot,
    nav: NativeRowsNavigation, width: androidx.compose.ui.unit.Dp, actions: NativeSportActions,
    extra: List<HubOverviewMatch> = emptyList(), onHub: ((HubMatch) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
        TvText(label, size = 27, bold = true)
        LazyRow(state = nav.rails.getValue(id), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
            items(sportRailItems(fixtures,extra), key = { it.key }) { item ->
                if (item is SportRailItem.Public) {
                    val game = item.fixture.match
                    HomeSurface({ onHub?.invoke(game) },nav.item("$id-${item.key}").width(width).heightIn(min=178.mpx).testTag("$id-${item.key}")) {
                        TvText(tv.own.owntv.ui.format.formatBestDateTime(androidx.compose.ui.platform.LocalContext.current,"dMMM",game.startsAt.toEpochMilli()),size=18,color=MinTvMuted)
                        TvText(stringResource(R.string.hub_pair,game.home.name,game.away.name),size=26,bold=true,lines=2)
                        TvText(if (data.preferences.hideScores) stringResource(R.string.hub_scores_hidden) else game.score?.let {
                            stringResource(R.string.hub_score,it.home,it.away)
                        } ?: stringResource(R.string.mintv_v08_channel_unconfirmed),size=19,color=MinTvMuted,lines=2)
                        if (item.fixture.source.freshness(Instant.ofEpochMilli(data.now)) != HubFreshness.FRESH || item.fixture.source.snapshot?.coverage?.isNotEmpty() == true)
                            TvText(stringResource(R.string.hub_partial),size=16,color=MinTvMuted,lines=2)
                    }
                } else if (item is SportRailItem.Swedish) {
                val game = item.fixture
                val playable = data.playableFixtures[game.id]?.takeIf { game.status == MatchStatus.SCHEDULED }
                val activate = { if (playable != null && actions.fixtureWatch != null) actions.fixtureWatch.invoke(game, playable) else actions.details(game) }
                HomeSurface(activate, nav.item("$id-${game.id}")
                    .then(if (playable != null) Modifier.logicalChannelKeys(activate, { actions.details(game) }) else Modifier).width(width).heightIn(min = (if (id == "sport-upcoming") 164 else 178).mpx).testTag("$id-${game.id}")) {
                    Column(Modifier.fillMaxWidth().heightIn(min = (if (id == "sport-upcoming") 136 else 150).mpx).neutralArtwork().padding(8.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
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
}
