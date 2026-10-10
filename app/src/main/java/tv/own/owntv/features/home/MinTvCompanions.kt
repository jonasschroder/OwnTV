package tv.own.owntv.features.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import java.text.NumberFormat
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.theme.mpx

/** Kept outside the Home lazy list: navigation and scroll survive entering match details. */
@Composable
internal fun ShlCompanion(visible: Boolean, active: Boolean, liveVm: LiveViewModel, profileId: Long?,
    favorites: List<ChannelEntity>, onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit, contentStart: Dp,
    onExternal: (Intent) -> Unit,
    onMatchcenterEntry: ((() -> Boolean)?) -> Unit,
    homeContent: @Composable (card: @Composable () -> Unit, matchcenterOpen: Boolean) -> Unit) {
    val repository = koinInject<ShlRepository>()
    val broadcasts: BroadcastMetadataSource = NoBroadcastMetadata
    var enabled by remember { mutableStateOf(repository.preferences.getBoolean("shl-enabled", false)) }
    var expansion by remember { mutableStateOf(repository.preferences.getBoolean("shl-expand", true)) }
    var screen by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(MatchcenterSection.MATCHES) }
    var selectedId by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var picker by rememberSaveable(profileId) { mutableStateOf(false) }
    var upcoming by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<ShlSnapshot?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var standings by remember { mutableStateOf<List<ShlStanding>>(emptyList()) }
    var standingsAt by remember { mutableLongStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var query by rememberSaveable(profileId) { mutableStateOf("") }
    var channels by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var confirmed by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var matchedGame by remember(profileId) { mutableStateOf<ShlGame?>(null) }
    var matching by remember { mutableStateOf(false) }
    var matchFailed by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf<BroadcastAssignment?>(null) }
    val entryFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val currentEntryScreen by rememberUpdatedState(screen)
    val currentEntryFocus by rememberUpdatedState(if (picker) searchFocus else if (selectedId != null) detailFocus else tabFocus)
    DisposableEffect(tabFocus) {
        onMatchcenterEntry {
            currentEntryScreen && (runCatching { currentEntryFocus.requestFocus() }.isSuccess ||
                runCatching { backFocus.requestFocus() }.isSuccess)
        }
        onDispose { onMatchcenterEntry(null) }
    }

    val gameFocus = remember { mutableMapOf<String, FocusRequester>() }
    var restoreTarget by remember { mutableStateOf<FocusRequester?>(null) }
    val matchList = rememberLazyListState()
    val tableList = rememberLazyListState()
    val today = stockholmDay(now)
    val todaysGames = data?.games.orEmpty().filter { it.on(today) }.sortedBy { !it.fbk }
    val next = data?.games?.firstOrNull { it.fbk && it.faceoff >= now }
    val expanded = enabled && expansion && todaysGames.isNotEmpty()
    val available = active && (visible || screen)
    val selected = selectedId?.let { id -> data?.games?.firstOrNull { it.id == id } }

    LaunchedEffect(available, enabled) {
        if (available && enabled) while (true) { now = System.currentTimeMillis(); delay(30_000) }
    }
    LaunchedEffect(available, enabled) {
        if (!available || !enabled) return@LaunchedEffect
        data = repository.cached()
        var retry = 60_000L
        while (true) {
            loading = data == null
            try { data = repository.refresh(System.currentTimeMillis()); failed = false; retry = 60_000 }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; retry = (retry * 2).coerceAtMost(15 * 60_000) }
            finally { loading = false }
            delay(retry)
        }
    }
    LaunchedEffect(available, enabled, expanded, screen, section, data?.seasonId, today) {
        if (!available || !enabled || !(expanded || screen && section in listOf(MatchcenterSection.TABLE, MatchcenterSection.FARJESTAD))) return@LaunchedEffect
        val season = data?.seasonId ?: return@LaunchedEffect
        while (true) {
            try { standings = repository.standings(season, System.currentTimeMillis()); standingsAt = repository.standingsFetchedAt }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { standings = emptyList(); standingsAt = 0 }
            delay(6 * 60 * 60_000L)
        }
    }
    val scheduleFresh = !failed && data?.let { now - it.fetchedAt in 0 until 10 * 60_000L } == true
    val gameToMatch = if (selectedId != null) selected else todaysGames.firstOrNull { it.fbk }.takeIf { expanded }
    LaunchedEffect(available, enabled, gameToMatch, scheduleFresh) {
        broadcast = null
        if (available && enabled && scheduleFresh && gameToMatch != null) {
            val fixture = BroadcastFixture.from(gameToMatch)
            broadcast = BroadcastResolver.usable(fixture, broadcasts.assignment(fixture), Instant.ofEpochMilli(now))
        }
    }
    val validBroadcast = gameToMatch?.let { BroadcastResolver.usable(BroadcastFixture.from(it), broadcast, Instant.ofEpochMilli(now)) }
    LaunchedEffect(available, profileId, gameToMatch, query, favorites, scheduleFresh, validBroadcast) {
        channels = emptyList(); confirmed = emptyList(); matchedGame = null; matchFailed = false
        if (!available || gameToMatch == null || profileId == null) { matching = false; return@LaunchedEffect }
        val game = gameToMatch
        matching = true
        try {
            delay(300)
            val found = (liveVm.homeSportsChannels(if (picker) query else "", favorites, profileId) +
                validBroadcast?.channels.orEmpty().filter { it.linear }.flatMap { liveVm.homeSportsChannels(it.name, favorites, profileId) })
                .distinctBy { it.id }.take(128)
            val matches = withContext(Dispatchers.IO) {
                found.filter { channel -> scheduleFresh && (validBroadcast?.channels.orEmpty().any {
                    BroadcastResolver.matches(it, channel.name)
                } || liveVm.homeStoredProgrammes(channel, game.faceoff - 60 * 60_000, game.faceoff + 3 * 60 * 60_000).any { epg ->
                    ShlEpgMatcher.confirmed(game, epg.title, epg.description, epg.startMs, epg.stopMs,
                        data?.games.orEmpty().filter { kotlin.math.abs(it.faceoff - game.faceoff) < 3 * 60 * 60_000 })
                }) }
            }
            channels = found; confirmed = matches; matchedGame = game
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { matchFailed = true }
        finally { matching = false }
    }
    LaunchedEffect(restoreTarget, screen, selectedId, picker) {
        val target = restoreTarget ?: return@LaunchedEffect
        withFrameNanos { }; runCatching { target.requestFocus() }.onFailure { runCatching { backFocus.requestFocus() } }
        restoreTarget = null
    }
    LaunchedEffect(screen) {
        // Saved match IDs can exist before cached fixtures load. Back is always attached.
        if (screen && restoreTarget == null) {
            withFrameNanos { }; runCatching { backFocus.requestFocus() }
        }
    }
    fun close() { screen = false; selectedId = null; picker = false; query = ""; restoreTarget = entryFocus }
    fun back() {
        val previous = MatchcenterNavigation(section, selectedId, picker).back()
        if (previous == null) close() else {
            val old = selectedId
            selectedId = previous.gameId; picker = previous.channelPicker; query = ""
            restoreTarget = if (previous.gameId != null) detailFocus else gameFocus[old] ?: tabFocus
        }
    }
    fun choose(game: ShlGame) { selectedId = game.id; picker = false; query = ""; restoreTarget = detailFocus }
    fun open() { screen = true; restoreTarget = tabFocus }
    fun play(channel: ChannelEntity, list: List<ChannelEntity>) { close(); onPlay(channel, list) }
    val reliableChannels = confirmed.takeIf { matchedGame == gameToMatch && scheduleFresh && !matching }.orEmpty()

    Box(Modifier.fillMaxSize()) {
        homeContent({
            TvCard(::open, Modifier.fillMaxWidth().focusRequester(entryFocus)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TvText(stringResource(R.string.mintv_fbk_title), size = 26, color = MinTvTeal, bold = true)
                    TvText(stringResource(if (expanded) R.string.mintv_shl_open else R.string.mintv_matchcenter), size = 20)
                }
                if (!enabled) TvText(stringResource(R.string.mintv_shl_disabled), color = MinTvMuted)
                else if (data == null) TvText(stringResource(if (loading) R.string.mintv_companion_loading else R.string.mintv_companion_unavailable), color = MinTvMuted)
                else if (expanded) {
                    todaysGames.take(3).forEach { game ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                            TvText(pairLabel(game), Modifier.weight(1f), color = if (game.fbk) MinTvTeal else Color.White, bold = game.fbk)
                            TvText(game.result ?: stockholmTime(game.faceoff), lines = 1, bold = true)
                        }
                    }
                } else if (next != null) TvText(stringResource(R.string.mintv_shl_home_next, pairLabel(next), fixtureWhen(next, now)))
                else TvText(stringResource(R.string.mintv_shl_next_unknown), color = MinTvMuted)
                if (enabled && data != null) TvText(stringResource(if (failed) R.string.mintv_cached_updated else R.string.mintv_updated,
                    updateLabel(data!!.fetchedAt, now)), size = 18, color = MinTvMuted, lines = 1)
                if (expanded && validBroadcast != null) TvText(validBroadcast.channels.joinToString { it.name }, size = 20, color = MinTvMuted)
            }
        }, screen)
        if (screen) {
            BackHandler(onBack = ::back)
            Column(Modifier.fillMaxSize().background(MinTvNavy).padding(start = contentStart, end = 64.mpx, top = 112.mpx, bottom = 40.mpx)
                .focusGroup(), verticalArrangement = Arrangement.spacedBy(24.mpx)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        TvText(stringResource(R.string.mintv_shl_header), size = 42, bold = true, lines = 1)
                        TvText(stringResource(R.string.mintv_fbk_title), color = MinTvTeal, size = 22, lines = 1)
                    }
                    TvText(stringResource(R.string.mintv_updated, when {
                        section == MatchcenterSection.TABLE && standingsAt > 0 -> updateLabel(standingsAt, now)
                        data != null -> updateLabel(data!!.fetchedAt, now)
                        else -> stringResource(R.string.mintv_companion_unavailable)
                    }), size = 18, color = MinTvMuted, lines = 1)
                    HomeButton(stringResource(R.string.mintv_back), ::back, Modifier.focusRequester(backFocus))
                }
                if (selectedId == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), modifier = Modifier.focusRestorer().focusGroup()) {
                        MatchcenterSection.entries.forEachIndexed { index, tab ->
                            TvCard({ section = tab; page = 0 }, Modifier.width(if (tab == MatchcenterSection.SETTINGS) 230.mpx else 200.mpx)
                                .then(if (index == 0) Modifier.focusRequester(tabFocus) else Modifier), selected = tab == section) {
                                TvText(stringResource(when (tab) {
                                    MatchcenterSection.MATCHES -> R.string.mintv_matches
                                    MatchcenterSection.TABLE -> R.string.mintv_shl_table
                                    MatchcenterSection.FARJESTAD -> R.string.mintv_fbk_title
                                    MatchcenterSection.SETTINGS -> R.string.mintv_companion_settings
                                }), size = 23, lines = 1, bold = true)
                            }
                        }
                    }
                    when (section) {
                        MatchcenterSection.SETTINGS -> LazyColumn(verticalArrangement = Arrangement.spacedBy(20.mpx)) {
                            item { TvText(stringResource(R.string.mintv_shl_source_heading), size = 28, bold = true) }
                            item { FocusPanel { TvText(stringResource(R.string.mintv_shl_source_notice), color = MinTvMuted, lines = 6) } }
                            item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                                HomeButton(stringResource(if (enabled) R.string.mintv_shl_disable else R.string.mintv_shl_enable), {
                                    enabled = !enabled; repository.preferences.edit().putBoolean("shl-enabled", enabled).apply()
                                    if (!enabled) { data = null; standings = emptyList(); selectedId = null; broadcast = null }
                                })
                                HomeButton(stringResource(if (expansion) R.string.mintv_shl_compact_only else R.string.mintv_shl_expand), {
                                    expansion = !expansion; repository.preferences.edit().putBoolean("shl-expand", expansion).apply()
                                })
                            } }
                            item { data?.let { TvText(stringResource(R.string.mintv_shl_snapshot, stockholmDate(it.fetchedAt) + " " + stockholmTime(it.fetchedAt),
                                it.sourceUpdatedAt?.let { at -> stockholmDate(at) + " " + stockholmTime(at) } ?: stringResource(R.string.mintv_companion_unavailable)), color = MinTvMuted, lines = 4) } }
                            if (repository.preferences.getBoolean("shl-access-blocked", false)) item { TvText(stringResource(R.string.mintv_shl_access_blocked), color = MinTvMuted, lines = 4) }
                            item { FocusPanel { TvText(stringResource(R.string.mintv_broadcast_source_notice), color = MinTvMuted, lines = 4) } }
                        }
                        MatchcenterSection.TABLE -> LazyColumn(state = tableList, verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                            item { TvText(stringResource(R.string.mintv_shl_regular_table, if (standingsAt > 0) stockholmTime(standingsAt) else stringResource(R.string.mintv_companion_unavailable)), size = 20, color = MinTvMuted) }
                            if (standings.isEmpty()) item { TvText(stringResource(R.string.mintv_companion_unavailable)) }
                            items(standings, key = { it.rank }) { standing ->
                                FocusPanel(highlighted = ShlTeams.identity(standing.team) == "farjestad") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
                                    TvText(NumberFormat.getIntegerInstance().format(standing.rank), Modifier.width(52.mpx), bold = true, lines = 1)
                                    TvText(shortTeam(standing.team), Modifier.weight(1f), bold = true, lines = 1)
                                    TvText(stringResource(R.string.mintv_points, NumberFormat.getIntegerInstance().format(standing.points)), color = MinTvTeal, lines = 1)
                                }
                                }
                            }
                        }
                        else -> {
                            val future = data?.games.orEmpty().filter { it.faceoff >= now }.filter { section != MatchcenterSection.FARJESTAD || it.fbk }
                            val todayList = todaysGames.filter { section != MatchcenterSection.FARJESTAD || it.fbk }
                            val useFuture = upcoming || todayList.isEmpty()
                            val lastPage = (future.size - 1).coerceAtLeast(0) / 14
                            val currentPage = page.coerceIn(0, lastPage)
                            val games = if (useFuture) future.drop(currentPage * 14).take(14) else todayList
                            LazyColumn(state = matchList, verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                                item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                                    if (todayList.isNotEmpty()) HomeButton(stringResource(if (useFuture) R.string.mintv_shl_today else R.string.mintv_shl_upcoming), { upcoming = !useFuture; page = 0 })
                                    else TvText(stringResource(R.string.mintv_shl_upcoming), size = 22, color = MinTvMuted)
                                    if (useFuture && currentPage > 0) HomeButton(stringResource(R.string.mintv_shl_previous_page), { page = currentPage - 1 })
                                    if (useFuture && currentPage < lastPage) HomeButton(stringResource(R.string.mintv_shl_next_page), { page = currentPage + 1 })
                                    if (failed) TvText(stringResource(R.string.mintv_cached), size = 18, color = MinTvMuted)
                                } }
                                if (games.isEmpty()) item { TvText(stringResource(if (!enabled) R.string.mintv_shl_disabled else if (loading) R.string.mintv_companion_loading else R.string.mintv_shl_next_unknown)) }
                                items(games, key = { it.id }) { game ->
                                    val focus = remember(game.id) { gameFocus.getOrPut(game.id) { FocusRequester() } }
                                    MatchScorecard(game, now, { choose(game) }, Modifier.fillMaxWidth().focusRequester(focus))
                                }
                            }
                        }
                    }
                } else if (selected == null) TvText(stringResource(R.string.mintv_companion_unavailable))
                else if (picker) {
                    TvText(pairLabel(selected), size = 30, bold = true)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                        item { TvText(stringResource(R.string.mintv_channel_search), color = MinTvMuted) }
                        item { CompanionInput(query, Modifier.focusRequester(searchFocus)) { query = it.take(80) } }
                        if (matching) item { TvText(stringResource(R.string.mintv_companion_loading)) }
                        if (matchFailed) item { TvText(stringResource(R.string.mintv_companion_unavailable)) }
                        if (reliableChannels.isNotEmpty()) item { TvText(stringResource(R.string.mintv_confirmed_channels), color = MinTvTeal, bold = true) }
                        items(reliableChannels.take(24), key = { it.id }) { channel -> ChannelCard(channel.name, channel.displayLogoUrl,
                            stringResource(R.string.mintv_source_number, NumberFormat.getIntegerInstance().format(channel.sourceId)), { play(channel, reliableChannels) }, Modifier.fillMaxWidth()) }
                        item { TvText(stringResource(R.string.mintv_shl_manual), size = 20, color = MinTvMuted) }
                        items(channels.filterNot { it in reliableChannels }.take(24), key = { it.id }) { channel -> ChannelCard(channel.name, channel.displayLogoUrl,
                            stringResource(R.string.mintv_source_number, NumberFormat.getIntegerInstance().format(channel.sourceId)), { play(channel, channels) }, Modifier.fillMaxWidth()) }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                        item { TvText(pairLabel(selected), size = 38, bold = true); TvText(fixtureWhen(selected, now), size = 26, color = MinTvMuted) }
                        item { TvText(selected.result?.let { stringResource(R.string.mintv_result_snapshot, it) } ?: stringResource(R.string.mintv_schedule), color = MinTvMuted) }
                        validBroadcast?.let { assignment -> item { TvText(assignment.channels.joinToString { it.name }, size = 26, bold = true)
                            TvText(stringResource(R.string.mintv_updated, updateLabel(assignment.checkedAt.toEpochMilli(), now)), size = 18, color = MinTvMuted) } }
                        item {
                            val direct = reliableChannels.singleOrNull()
                            HomeButton(stringResource(if (direct != null) R.string.mintv_watch_match else R.string.mintv_shl_choose), {
                                if (direct != null) play(direct, reliableChannels) else { picker = true; restoreTarget = searchFocus }
                            }, Modifier.focusRequester(detailFocus))
                        }
                        if (validBroadcast != null && reliableChannels.isEmpty() && !matching) item { TvText(stringResource(R.string.mintv_channel_missing), color = MinTvMuted) }
                        item { HomeButton(stringResource(R.string.mintv_consult_tvmatchen), {
                            // Only explicit user browser hand-off. No URL inference, page fetch or stream URL.
                            onExternal(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tvmatchen.nu/")))
                        }) }
                        item { TvText(stringResource(R.string.mintv_external_manual_notice), color = MinTvMuted, size = 20) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchScorecard(game: ShlGame, now: Long, onClick: () -> Unit, modifier: Modifier) {
    TvCard(onClick, modifier, selected = game.fbk) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                TvText(pairLabel(game), size = 29, bold = true)
                TvText(fixtureWhen(game, now), size = 22, color = MinTvMuted, lines = 1)
            }
            TvText(game.result ?: stockholmTime(game.faceoff), size = 32, bold = true, lines = 1)
            TvText(stringResource(if (game.result != null) R.string.mintv_snapshot_status else R.string.mintv_schedule), size = 18, color = MinTvMuted, lines = 1)
        }
    }
}

@Composable
internal fun CompanionInput(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onChange, singleLine = true, textStyle = tv.own.owntv.ui.theme.stageText(24, 500).copy(color = Color.White),
        modifier = modifier.fillMaxWidth().background(Color(0xFF1B2B36)).then(Modifier
            .onFocusChanged { focused = it.isFocused }.border(2.mpx, if (focused) MinTvTeal else MinTvMuted)).padding(20.mpx))
}

@Composable
private fun pairLabel(game: ShlGame): String = stringResource(R.string.mintv_shl_pair, shortTeam(game.home), shortTeam(game.away))

@Composable
private fun fixtureWhen(game: ShlGame, now: Long): String {
    val day = stockholmDay(game.faceoff)
    val date = when (day) {
        stockholmDay(now) -> stringResource(R.string.mintv_today)
        stockholmDay(now).plusDays(1) -> stringResource(R.string.mintv_tomorrow)
        else -> stockholmDate(game.faceoff)
    }
    return stringResource(R.string.mintv_fixture_time, date, stockholmTime(game.faceoff))
}
