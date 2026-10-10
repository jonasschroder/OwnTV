package tv.own.owntv.features.home

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.edit
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.theme.mpx

/** Kept outside the Home lazy list: navigation and scroll survive entering match details. */
@Composable
internal fun ShlCompanion(visible: Boolean, active: Boolean, liveVm: LiveViewModel, profileId: Long?,
    favorites: List<ChannelEntity>, onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit, contentStart: Dp,
    onSources: () -> Unit,
    onMatchcenterEntry: ((() -> Boolean)?) -> Unit,
    homeContent: @Composable (card: @Composable () -> Unit, matchcenterOpen: Boolean) -> Unit) {
    val repository = koinInject<ShlRepository>()
    val broadcasts = koinInject<TvmatchenBroadcastSource>()
    val overrides = koinInject<ShlChannelOverrides>()
    val libraryContext by liveVm.homeLibraryContext.collectAsStateWithLifecycle()
    val libraryCount by liveVm.homeChannelCount.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var broadcastEnabled by remember { mutableStateOf(repository.preferences.getBoolean("broadcast-enabled", false)) }
    var enabled by remember { mutableStateOf(repository.preferences.getBoolean("shl-enabled", false)) }
    var expansion by remember { mutableStateOf(repository.preferences.getBoolean("shl-expand", true)) }
    var screen by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(MatchcenterSection.MATCHES) }
    var selectedId by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var picker by rememberSaveable(profileId) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(profileId) { mutableStateOf(false) }
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
    val knownBroadcasts = remember { mutableStateMapOf<String, BroadcastAssignment>() }
    val resolvedCards = remember(profileId) { mutableStateMapOf<String, Pair<Long, List<ChannelEntity>>>() }
    val directCards = remember(profileId) { mutableStateMapOf<String, ChannelEntity>() }
    var hasChannels by remember(profileId) { mutableStateOf<Boolean?>(null) }
    var chosenChannel by remember(profileId) { mutableStateOf<ChannelEntity?>(null) }
    var manualChoice by remember(profileId) { mutableStateOf<FixtureChannelChoice?>(null) }
    var matchedBroadcast by remember { mutableStateOf<BroadcastAssignment?>(null) }
    var choiceVersion by remember { mutableIntStateOf(0) }
    var candidatesComplete by remember { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val pickerFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val currentEntryScreen by rememberUpdatedState(screen)
    val currentEntryFocus by rememberUpdatedState(if (picker) { if (searchOpen) searchFocus else pickerFocus } else if (selectedId != null) detailFocus else tabFocus)
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
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(searchOpen, imeVisible) {
        if (searchOpen && keyboardWasVisible && !imeVisible) { searchOpen = false; query = ""; restoreTarget = pickerFocus }
        keyboardWasVisible = searchOpen && imeVisible
    }
    LaunchedEffect(profileId, libraryContext, libraryCount, favorites) { resolvedCards.clear(); directCards.clear() }

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
    val gameToMatch = if (!broadcastContentVisible(visible, screen, section, selected != null, upcoming)) null
        else if (selectedId != null) selected else todaysGames.firstOrNull { it.fbk }.takeIf { expanded }
    LaunchedEffect(available, enabled, broadcastEnabled, gameToMatch, scheduleFresh) {
        broadcast = null
        if (available && enabled && broadcastEnabled && scheduleFresh && gameToMatch != null) {
            val fixture = BroadcastFixture.from(gameToMatch)
            while (true) {
                broadcast = BroadcastResolver.usable(fixture, broadcasts.assignment(fixture), Instant.ofEpochMilli(System.currentTimeMillis()))
                broadcast?.let { value ->
                    knownBroadcasts[fixtureKey(fixture)] = value
                    while (knownBroadcasts.size > 16) knownBroadcasts.remove(knownBroadcasts.minBy { it.value.checkedAt }.key)
                }
                if (broadcasts.blocked) { knownBroadcasts.clear(); resolvedCards.clear(); directCards.clear() }
                delay(ExperimentalBroadcastReader.interval)
            }
        }
    }
    val validBroadcast = if (broadcastEnabled && enabled) gameToMatch?.let { BroadcastResolver.usable(BroadcastFixture.from(it), broadcast, Instant.ofEpochMilli(now)) } else null
    LaunchedEffect(available, profileId, libraryContext, libraryCount) {
        if (available && profileId != null) hasChannels = liveVm.homeHasChannels(profileId)
    }
    LaunchedEffect(available, profileId, gameToMatch, query, favorites, scheduleFresh, validBroadcast, libraryContext, libraryCount, choiceVersion) {
        channels = emptyList(); confirmed = emptyList(); matchedGame = null; matchFailed = false
        chosenChannel = null; manualChoice = null; matchedBroadcast = null; candidatesComplete = false
        if (!available || gameToMatch == null || profileId == null) { matching = false; return@LaunchedEffect }
        val game = gameToMatch
        matching = true
        try {
            delay(300)
            hasChannels = liveVm.homeHasChannels(profileId)
            val manual = overrides.read(profileId, BroadcastFixture.from(game), now)
            chosenChannel = manual?.let { value -> liveVm.homeChannel(value.channelId, profileId)?.takeIf {
                value.usable(profileId, BroadcastFixture.from(game), now, it.id, it.sourceId, it.name)
            } }
            manualChoice = manual.takeIf { chosenChannel != null }
            val pool = liveVm.homeMatchCandidates("", favorites, profileId, validBroadcast?.channels.orEmpty().map { it.name })
            val discovered = liveVm.homeBroadcastCandidates(validBroadcast?.channels.orEmpty(), profileId)
            val suggestions = if (query.isBlank()) pool else liveVm.homeMatchCandidates(query, emptyList(), profileId, emptyList())
            val found = if (validBroadcast != null) discovered.channels else pool.channels
            val epgSupport = withContext(Dispatchers.IO) {
                found.filter { channel -> scheduleFresh &&
                    liveVm.homeStoredProgrammes(channel, game.faceoff - 60 * 60_000, game.faceoff + 3 * 60 * 60_000).any { epg ->
                    ShlEpgMatcher.confirmed(game, epg.title, epg.description, epg.startMs, epg.stopMs,
                        data?.games.orEmpty().filter { kotlin.math.abs(it.faceoff - game.faceoff) < 3 * 60 * 60_000 })
                } }
            }
            val matches = if (validBroadcast != null) discovered.channels.sortedBy { it !in epgSupport } else epgSupport
            channels = suggestions.channels; confirmed = matches; matchedGame = game
            matchedBroadcast = validBroadcast
            candidatesComplete = if (validBroadcast != null) !discovered.truncated else !pool.truncated
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
    fun close() { screen = false; selectedId = null; picker = false; searchOpen = false; query = ""; restoreTarget = entryFocus }
    fun back() {
        keyboard?.hide()
        val previous = MatchcenterNavigation(section, selectedId, picker, searchOpen).back()
        if (previous == null) close() else {
            val old = selectedId
            selectedId = previous.gameId; picker = previous.channelPicker; searchOpen = previous.searchOpen; query = ""
            restoreTarget = if (previous.channelPicker) pickerFocus else if (previous.gameId != null) detailFocus else gameFocus[old] ?: tabFocus
        }
    }
    fun choose(game: ShlGame) { selectedId = game.id; picker = false; searchOpen = false; query = ""; restoreTarget = detailFocus }
    fun open() { screen = true; restoreTarget = tabFocus }
    fun play(channel: ChannelEntity, list: List<ChannelEntity>) {
        // Shell saves this destination during fullscreen; Back returns to the match/picker.
        // An unavailable/external-player handoff also keeps the user's selection intact.
        onPlay(channel, list)
    }
    val reliableChannels = confirmed.takeIf { matchedGame == gameToMatch && matchedBroadcast == validBroadcast && scheduleFresh && !matching }.orEmpty()
    val manualChannel = chosenChannel.takeIf { matchedGame == gameToMatch && !matching && now < (manualChoice?.expiresAt ?: 0L) }
    val directChannel = manualChannel ?: reliableChannels.singleOrNull().takeIf { candidatesComplete }
    LaunchedEffect(matchedGame, matching, directChannel, reliableChannels, validBroadcast, manualChoice) {
        val game = matchedGame ?: return@LaunchedEffect
        if (!matching && game == gameToMatch) {
            val key = fixtureKey(BroadcastFixture.from(game))
            val expiry = if (directChannel == manualChannel && manualChannel != null) manualChoice?.expiresAt ?: 0L
                else validBroadcast?.expiresAt?.toEpochMilli() ?: Long.MAX_VALUE
            resolvedCards[key] = minOf(System.currentTimeMillis() + 2 * 60_000L, expiry) to reliableChannels
            if (directChannel == null) directCards.remove(key) else directCards[key] = directChannel
            while (resolvedCards.size > 16) {
                val old = resolvedCards.minBy { it.value.first }.key
                resolvedCards.remove(old); directCards.remove(old)
            }
        }
    }
    val pickerRows = (listOfNotNull(manualChannel) + reliableChannels + channels).distinctBy { it.id }.take(48)
    fun pick(channel: ChannelEntity) {
        val game = selected ?: return
        val profile = profileId ?: return
        overrides.save(FixtureChannelChoice(profile, fixtureKey(BroadcastFixture.from(game)), channel.id,
            channel.sourceId, channel.name, game.faceoff + 4 * 60 * 60_000L), System.currentTimeMillis())
        choiceVersion++; play(channel, pickerRows)
    }
    LaunchedEffect(picker, matching, hasChannels, searchOpen, pickerRows.firstOrNull()?.id) {
        if (screen && picker && !searchOpen && !matching && hasChannels != null) {
            withFrameNanos { }; runCatching { pickerFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        homeContent({
            Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
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
                if (expanded && validBroadcast != null) TvText(validBroadcast.channels.filter { it.linear }.joinToString { it.name }, size = 23, color = MinTvTeal)
            }
            if (expanded && directChannel != null) HomeButton(stringResource(R.string.mintv_watch_match), { play(directChannel, reliableChannels.ifEmpty { listOf(directChannel) }) })
            }
        }, screen)
        if (screen) {
            BackHandler(onBack = ::back)
            Column(Modifier.fillMaxSize().background(MinTvNavy).padding(start = contentStart, end = 48.mpx, top = 100.mpx, bottom = 32.mpx)
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
                                    enabled = !enabled; repository.preferences.edit { putBoolean("shl-enabled", enabled) }
                                    if (!enabled) { data = null; standings = emptyList(); selectedId = null; broadcast = null }
                                })
                                HomeButton(stringResource(if (expansion) R.string.mintv_shl_compact_only else R.string.mintv_shl_expand), {
                                    expansion = !expansion; repository.preferences.edit { putBoolean("shl-expand", expansion) }
                                })
                            } }
                            item { data?.let { TvText(stringResource(R.string.mintv_shl_snapshot, stockholmDate(it.fetchedAt) + " " + stockholmTime(it.fetchedAt),
                                it.sourceUpdatedAt?.let { at -> stockholmDate(at) + " " + stockholmTime(at) } ?: stringResource(R.string.mintv_companion_unavailable)), color = MinTvMuted, lines = 4) } }
                            if (repository.preferences.getBoolean("shl-access-blocked", false)) item { TvText(stringResource(R.string.mintv_shl_access_blocked), color = MinTvMuted, lines = 4) }
                            item { FocusPanel { TvText(stringResource(R.string.mintv_broadcast_source_notice), color = MinTvMuted, lines = 4) } }
                            item { HomeButton(stringResource(if (broadcastEnabled) R.string.mintv_broadcast_disable else R.string.mintv_broadcast_enable), {
                                broadcastEnabled = !broadcastEnabled
                                repository.preferences.edit { putBoolean("broadcast-enabled", broadcastEnabled) }
                                if (!broadcastEnabled) { broadcast = null; knownBroadcasts.clear(); resolvedCards.clear(); directCards.clear() }
                            }) }
                            if (broadcasts.blocked) item { FocusPanel { TvText(stringResource(R.string.mintv_broadcast_blocked), color = MinTvMuted, lines = 4) } }
                        }
                        MatchcenterSection.TABLE -> LazyColumn(state = tableList, verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                            val showPlayed = standings.isNotEmpty() && standings.all { it.played != null }
                            val showDifference = standings.isNotEmpty() && standings.all { it.goalDifference != null }
                            item { StandingRow(stringResource(R.string.mintv_position), stringResource(R.string.mintv_team),
                                if (showPlayed) stringResource(R.string.mintv_games_played) else null,
                                if (showDifference) stringResource(R.string.mintv_goal_difference) else null, stringResource(R.string.mintv_table_points)) }
                            if (standings.isEmpty()) item { TvText(stringResource(R.string.mintv_companion_unavailable)) }
                            items(standings, key = { it.rank }) { standing ->
                                FocusPanel(highlighted = ShlTeams.identity(standing.team) == "farjestad") {
                                StandingRow(NumberFormat.getIntegerInstance().format(standing.rank), shortTeam(standing.team),
                                    standing.played?.takeIf { showPlayed }?.let { NumberFormat.getIntegerInstance().format(it) },
                                    standing.goalDifference?.takeIf { showDifference }?.let { NumberFormat.getIntegerInstance().format(it) },
                                    NumberFormat.getIntegerInstance().format(standing.points))
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
                                if (section == MatchcenterSection.FARJESTAD) item {
                                    standings.singleOrNull { ShlTeams.identity(it.team) == "farjestad" }?.let {
                                        TvText(stringResource(R.string.mintv_fbk_position, NumberFormat.getIntegerInstance().format(it.rank),
                                            NumberFormat.getIntegerInstance().format(it.points)), size = 27, color = MinTvTeal, bold = true)
                                    }
                                }
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
                                    val key = fixtureKey(BroadcastFixture.from(game))
                                    val assignment = if (enabled && broadcastEnabled) BroadcastResolver.usable(BroadcastFixture.from(game), knownBroadcasts[key], Instant.ofEpochMilli(now)) else null
                                    val cached = resolvedCards[key]?.takeIf { now < it.first && scheduleFresh }
                                    val direct = if (game == gameToMatch) directChannel else directCards[key].takeIf { cached != null }
                                    MatchScorecard(game, now, assignment?.channels?.filter { it.linear }?.joinToString { it.name }, { choose(game) },
                                        direct?.let { channel -> { play(channel, cached?.second.orEmpty().ifEmpty { listOf(channel) }) } }, Modifier.fillMaxWidth().focusRequester(focus))
                                }
                            }
                        }
                    }
                } else if (selected == null) TvText(stringResource(R.string.mintv_companion_unavailable))
                else if (picker) {
                    TvText(pairLabel(selected), size = 30, bold = true)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                        if (hasChannels == false) {
                            item { TvText(stringResource(R.string.mintv_no_channels), size = 27, lines = 3) }
                            item { HomeButton(stringResource(R.string.mintv_add_source), onSources, Modifier.focusRequester(pickerFocus)) }
                        } else if (hasChannels == true) {
                        if (matching) item { TvText(stringResource(R.string.mintv_companion_loading)) }
                        if (matchFailed) item { TvText(stringResource(R.string.mintv_companion_unavailable)) }
                        if (reliableChannels.isNotEmpty()) item { TvText(stringResource(R.string.mintv_confirmed_channels), color = MinTvTeal, bold = true) }
                        items(pickerRows, key = { it.id }) { channel ->
                            ChannelCard(channel.name, channel.displayLogoUrl,
                                stringResource(if (channel in reliableChannels) R.string.mintv_confirmed_source else R.string.mintv_unconfirmed_source,
                                    NumberFormat.getIntegerInstance().format(channel.sourceId)), { pick(channel) },
                                Modifier.fillMaxWidth().then(if (channel == pickerRows.firstOrNull() && !searchOpen) Modifier.focusRequester(pickerFocus) else Modifier),
                                selected = channel == manualChannel)
                        }
                        if (pickerRows.isEmpty() && !matching) item { TvText(stringResource(R.string.mintv_no_channel_matches), color = MinTvMuted) }
                        item { HomeButton(stringResource(if (searchOpen) R.string.mintv_close_search else R.string.mintv_channel_search), {
                            if (searchOpen) back() else { searchOpen = true; restoreTarget = searchFocus }
                        }, if (pickerRows.isEmpty() && !searchOpen) Modifier.focusRequester(pickerFocus) else Modifier) }
                        if (searchOpen) item { CompanionInput(query, Modifier.focusRequester(searchFocus)) { query = it.take(80) } }
                        item { TvText(stringResource(R.string.mintv_shl_manual), size = 20, color = MinTvMuted) }
                        } else item { TvText(stringResource(R.string.mintv_companion_loading)) }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                        item { TvText(pairLabel(selected), size = 38, bold = true); TvText(fixtureWhen(selected, now), size = 26, color = MinTvMuted) }
                        selected.result?.let { result -> item { TvText(stringResource(R.string.mintv_result_snapshot, result), size = 27, color = MinTvMuted) } }
                        validBroadcast?.let { assignment -> item { TvText(assignment.channels.filter { it.linear }.joinToString { it.name }, size = 30, color = MinTvTeal, bold = true)
                            TvText(stringResource(R.string.mintv_updated, updateLabel(assignment.checkedAt.toEpochMilli(), now)), size = 18, color = MinTvMuted) } }
                        if (manualChannel != null) item { TvText(stringResource(R.string.mintv_saved_channel, manualChannel.name), size = 25) }
                        item {
                            HomeButton(stringResource(if (hasChannels == false) R.string.mintv_add_source else if (directChannel != null) R.string.mintv_watch_match else R.string.mintv_shl_choose), {
                                if (hasChannels == false) onSources()
                                else if (directChannel != null) play(directChannel, reliableChannels.ifEmpty { listOf(directChannel) })
                                else { picker = true; searchOpen = false; restoreTarget = pickerFocus }
                            }, Modifier.focusRequester(detailFocus))
                        }
                        if (hasChannels == false) item { TvText(stringResource(R.string.mintv_no_channels), color = MinTvMuted, lines = 3) }
                        if (validBroadcast != null && reliableChannels.isEmpty() && !matching) item { TvText(stringResource(R.string.mintv_channel_missing), color = MinTvMuted) }
                        if (validBroadcast == null && manualChannel == null && !matching && reliableChannels.isEmpty() && hasChannels == true) item { TvText(stringResource(R.string.mintv_broadcast_unavailable), color = MinTvMuted) }
                        if (directChannel != null) item { HomeButton(stringResource(R.string.mintv_change_channel), { picker = true; searchOpen = false; restoreTarget = pickerFocus }) }
                        if (manualChannel != null) item { HomeButton(stringResource(R.string.mintv_forget_channel), {
                            profileId?.let { overrides.clear(it, BroadcastFixture.from(selected)) }; choiceVersion++
                        }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchScorecard(game: ShlGame, now: Long, broadcaster: String?, onClick: () -> Unit, onWatch: (() -> Unit)?, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
    TvCard(onClick, Modifier.weight(1f), selected = game.fbk) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                TvText(pairLabel(game), size = 34, bold = true)
                TvText(fixtureWhen(game, now), size = 25, color = MinTvMuted, lines = 1)
                broadcaster?.takeIf { it.isNotBlank() }?.let { TvText(it, size = 26, color = MinTvTeal, lines = 1) }
            }
            TvText(game.result ?: stockholmTime(game.faceoff), size = 32, bold = true, lines = 1)
            if (game.result != null) TvText(stringResource(R.string.mintv_saved_result), size = 20, color = MinTvMuted, lines = 1)
        }
    }
    onWatch?.let { HomeButton(stringResource(R.string.mintv_watch_match), it) }
    }
}

@Composable
private fun StandingRow(position: String, team: String, played: String?, difference: String?, points: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
        TvText(position, Modifier.width(70.mpx), size = 25, bold = true, lines = 1)
        TvText(team, Modifier.weight(1f), size = 27, bold = true, lines = 1)
        played?.let { TvText(it, Modifier.width(120.mpx), size = 25, lines = 1) }
        difference?.let { TvText(it, Modifier.width(160.mpx), size = 25, lines = 1) }
        TvText(points, Modifier.width(100.mpx), size = 27, color = MinTvTeal, bold = true, lines = 1)
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
