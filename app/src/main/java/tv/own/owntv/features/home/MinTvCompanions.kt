package tv.own.owntv.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import java.text.NumberFormat
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
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
internal fun MinSportCompanion(visible: Boolean, active: Boolean, liveVm: LiveViewModel, profileId: Long?,
    favorites: List<ChannelEntity>, onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit, contentStart: Dp,
    onSources: () -> Unit,
    reservedTop: Dp = 144.mpx,
    homeCardModifier: Modifier = Modifier,
    onMatchcenterEntry: ((() -> Boolean)?) -> Unit,
    homeContent: @Composable (card: @Composable () -> Unit, matchcenterOpen: Boolean) -> Unit) {
    val repository = koinInject<ShlRepository>()
    val sports = koinInject<SportsRepository>()
    val teamStore = koinInject<SportsPreferences>()
    val teamRevision by teamStore.changes.collectAsStateWithLifecycle()
    var selection by remember(profileId) { mutableStateOf<SportPreferences?>(null) }
    var teamsOpen by rememberSaveable(profileId) { mutableStateOf(false) }
    LaunchedEffect(profileId, teamRevision) {
        try { selection = profileId?.let { teamStore.read(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { selection = SportPreferences(); SportsDiagnostics.failure(error, HockeyStage.CACHE) }
    }
    val selectedTeams = selection ?: SportPreferences()
    val requiredCompetitions = SportsCatalog.required(selectedTeams)
    var competitionId by rememberSaveable(profileId) { mutableStateOf(SportsCatalog.shl.id) }
    val selectedCompetition = requiredCompetitions.firstOrNull { it.id == competitionId } ?: requiredCompetitions.firstOrNull() ?: SportsCatalog.shl
    val broadcasts = koinInject<TvmatchenBroadcastSource>()
    val overrides = koinInject<ShlChannelOverrides>()
    val libraryContext by liveVm.homeLibraryContext.collectAsStateWithLifecycle()
    val libraryCount by liveVm.homeChannelCount.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var broadcastEnabled by remember { mutableStateOf(repository.preferences.getBoolean("broadcast-enabled", false)) }
    var enabled by remember { mutableStateOf(repository.preferences.getBoolean("shl-enabled", false)) }
    var screen by rememberSaveable(profileId) { mutableStateOf(false) }
    var section by rememberSaveable(profileId) { mutableStateOf(MatchcenterSection.FARJESTAD) }
    var selectedId by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var picker by rememberSaveable(profileId) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(profileId) { mutableStateOf(false) }
    var browsing by rememberSaveable(profileId) { mutableStateOf(false) }
    var categoryId by rememberSaveable(profileId) { mutableStateOf<Long?>(null) }
    var categoryPage by rememberSaveable(profileId) { mutableIntStateOf(0) }
    var categories by remember(profileId) { mutableStateOf<List<LiveViewModel.HomeSportsCategory>>(emptyList()) }
    var categoryRows by remember(profileId) { mutableStateOf<LiveViewModel.HomeChannelCandidates?>(null) }
    var upcoming by rememberSaveable(profileId) { mutableStateOf(false) }
    var page by rememberSaveable(profileId) { mutableIntStateOf(0) }
    var data by remember(profileId) { mutableStateOf<Map<String, SportSnapshot>>(emptyMap()) }
    var localMatches by remember(profileId) { mutableStateOf<List<EpgHockeyMatch>>(emptyList()) }
    var localChannels by remember(profileId) { mutableStateOf<Map<Long, ChannelEntity>>(emptyMap()) }
    var localLoading by remember(profileId) { mutableStateOf(false) }
    var localFailed by remember(profileId) { mutableStateOf(false) }
    val loadStates = remember(profileId) { mutableStateMapOf<String, SportsLoadState>() }
    var retryRevision by remember(profileId) { mutableIntStateOf(0) }
    var table by remember(profileId, selectedCompetition.id) { mutableStateOf<HockeyTable?>(null) }
    val standings = table?.takeIf { it.seasonId == data[selectedCompetition.id]?.seasonId }?.rows.orEmpty()
    val standingsAt = table?.takeIf { it.seasonId == data[selectedCompetition.id]?.seasonId }?.fetchedAt ?: 0L
    var tableState by remember(profileId, selectedCompetition.id) { mutableStateOf(SportsLoadState()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var query by rememberSaveable(profileId) { mutableStateOf("") }
    var channels by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var confirmed by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var matchedGame by remember(profileId) { mutableStateOf<SportFixture?>(null) }
    var matching by remember(profileId) { mutableStateOf(false) }
    var matchFailed by remember(profileId) { mutableStateOf(false) }
    var broadcast by remember(profileId) { mutableStateOf<BroadcastAssignment?>(null) }
    val knownBroadcasts = remember { mutableStateMapOf<String, BroadcastAssignment>() }
    val resolvedCards = remember(profileId) { mutableStateMapOf<String, Pair<Long, List<ChannelEntity>>>() }
    val directCards = remember(profileId) { mutableStateMapOf<String, ChannelEntity>() }
    var hasChannels by remember(profileId) { mutableStateOf<Boolean?>(null) }
    var chosenChannel by remember(profileId) { mutableStateOf<ChannelEntity?>(null) }
    var manualChoice by remember(profileId) { mutableStateOf<FixtureChannelChoice?>(null) }
    var matchedBroadcast by remember(profileId) { mutableStateOf<BroadcastAssignment?>(null) }
    var choiceVersion by remember(profileId) { mutableIntStateOf(0) }
    var candidatesComplete by remember(profileId) { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val pickerFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val currentEntryScreen by rememberUpdatedState(screen)
    val currentActive by rememberUpdatedState(active)
    val currentProfile by rememberUpdatedState(profileId)
    val currentEntryFocus by rememberUpdatedState(if (picker) { if (searchOpen) searchFocus else pickerFocus } else if (selectedId != null) detailFocus else tabFocus)
    DisposableEffect(tabFocus) {
        onMatchcenterEntry {
            currentEntryScreen && (currentEntryFocus.requestSafely() || backFocus.requestSafely())
        }
        onDispose { onMatchcenterEntry(null) }
    }

    val gameFocus = remember(profileId) { mutableMapOf<String, FocusRequester>() }
    var restoreTarget by remember(profileId) { mutableStateOf<FocusRequester?>(null) }
    val matchList = rememberLazyListState()
    val tableList = rememberLazyListState()
    val today = stockholmDay(now)
    val currentData = data.filterKeys { id -> requiredCompetitions.any { it.id == id } }
    val externalGames = remember(currentData) { currentData.values.flatMap { it.fixtures }.sortedBy { it.faceoff } }
    val allGames = remember(externalGames, localMatches) { (externalGames + localMatches.map { it.fixture }).distinctBy { it.id }.sortedBy { it.faceoff } }
    LaunchedEffect(profileId) { SportsDiagnostics.record(SportsDiagnostics.Event.PROFILE_CHANGED) }
    LaunchedEffect(screen) { SportsDiagnostics.record(if (screen) SportsDiagnostics.Event.MATCHCENTER else SportsDiagnostics.Event.HOME) }
    DisposableEffect(profileId, allGames) {
        gameFocus.keys.retainAll(allGames.mapTo(HashSet()) { it.id })
        onDispose { }
    }
    val mine = personalFixtures(allGames, selectedTeams, now)
    val todaysGames = allGames.filter { it.on(today) }.sortedWith(compareBy<SportFixture> { !it.follows(selectedTeams) }.thenBy { it.faceoff })
    val available = active && (visible && selectedTeams.prominent || screen) && selection != null && selectedTeams.teamIds.isNotEmpty()
    val scheduleAvailable = available && sportsScheduleVisible(visible, screen, section, selectedId != null, teamsOpen)
    val selected = selectedId?.let { id -> allGames.firstOrNull { it.id == id } }
    val fetchedAt = currentData[selectedCompetition.id]?.fetchedAt
    val selectedLoad = loadStates[selectedCompetition.id] ?: SportsLoadState()
    val homeCompetition = homeSportsCompetition(requiredCompetitions, selectedCompetition)
    val homeIssue = requiredCompetitions.firstNotNullOfOrNull { loadStates[it.id]?.issue }
    val homeLoading = requiredCompetitions.any { loadStates[it.id]?.loading == true }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(searchOpen, imeVisible) {
        if (searchOpen && keyboardWasVisible && !imeVisible) { searchOpen = false; query = ""; restoreTarget = pickerFocus }
        keyboardWasVisible = searchOpen && imeVisible
    }
    LaunchedEffect(profileId, libraryContext, libraryCount, favorites) { resolvedCards.clear(); directCards.clear() }

    val clockActive = available
    LaunchedEffect(clockActive) {
        if (clockActive) while (true) { now = System.currentTimeMillis(); delay(30_000) }
    }
    // The guide reads only stored EPG. It remains useful when every external source is disabled,
    // blocked or unavailable; a bounded refresh only runs while this content is in the foreground.
    val localGuideVisible = available && !teamsOpen && (!screen || section in listOf(MatchcenterSection.FARJESTAD, MatchcenterSection.MATCHES) || selectedId != null)
    LaunchedEffect(localGuideVisible, profileId, libraryContext, libraryCount, favorites, selectedTeams, externalGames) {
        if (!localGuideVisible || profileId == null) { localLoading = false; return@LaunchedEffect }
        while (true) {
            localLoading = true; localFailed = false
            try {
                val at = System.currentTimeMillis()
                val known = externalGames.filter { game -> sportsSnapshotFresh(currentData[game.competition.id], loadStates[game.competition.id], at) }
                val loaded = withContext(Dispatchers.IO) { withTimeout(8_000) {
                    val pool = liveVm.homeMatchCandidates("", favorites, profileId, listOf("TV12"))
                    val collector = HockeyEpgCollector(selectedTeams, at, known)
                    for (channel in pool.channels.take(64)) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        for (programme in liveVm.homeHockeyProgrammes(channel, at, profileId)) collector.add(channel.id, programme)
                    }
                    collector.result(pool.truncated || pool.channels.size > 64) to pool.channels.take(64).associateBy { it.id }
                } }
                localChannels = loaded.second; localMatches = loaded.first
            } catch (timeout: TimeoutCancellationException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                localFailed = true; SportsDiagnostics.failure(timeout, HockeyStage.CACHE)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { localFailed = true; SportsDiagnostics.failure(error, HockeyStage.CACHE) }
            finally { localLoading = false }
            delay(2 * 60_000L)
        }
    }
    val requestedCompetitions = if (scheduleAvailable && enabled)
        sportsRequestCompetitions(selectedTeams, active, visible, screen, enabled, selectedCompetition) else emptyList()
    LaunchedEffect(profileId, available, requiredCompetitions, requestedCompetitions, retryRevision) {
        data = data.filterKeys { id -> requiredCompetitions.any { it.id == id } }
        val competitions = requestedCompetitions
        if (!available) return@LaunchedEffect
        // Hydrate every relevant league before any network wait. A slow first league must not
        // conceal another league's verified cache for up to the request deadline.
        for (competition in requiredCompetitions.filter { it.scheduleAvailable }) {
            sports.cached(competition)?.let { cached ->
                data = data + (competition.id to cached)
                loadStates[competition.id] = SportsLoadState(cached = true)
            }
        }
        if (!enabled) requiredCompetitions.filter { it.scheduleAvailable }.forEach {
            repository.notScheduled(it, false, System.currentTimeMillis())
        }
        // One visible refresh; repository TTL/retry gates survive Home/Matchcenter changes.
        // No automatic retry loop or work after the screen leaves the foreground.
        for (competition in competitions) {
            val started = System.currentTimeMillis()
            fetchSports(started, HockeyStage.SCHEDULE, { data[competition.id] != null }, {
                sports.refresh(competition, started)
            }, { value -> data = data + (competition.id to value); repository.published(competition, value.fixtures.size, System.currentTimeMillis()); value.fetchedAt < started },
                { loadStates[competition.id] = it }, onIssue = { repository.failed(competition, it) })
        }
    }

    val tableRequest = data[selectedCompetition.id]?.seasonId?.takeIf {
        available && enabled && screen && !teamsOpen && selectedId == null &&
            section == MatchcenterSection.TABLE
    }?.let { selectedCompetition to it }
    LaunchedEffect(profileId, tableRequest, retryRevision) {
        val (competition, season) = tableRequest ?: return@LaunchedEffect
        val started = System.currentTimeMillis()
        sports.cachedStandings(competition, season)?.let { table = it }
        fetchSports(started, HockeyStage.TABLE, { table != null }, {
            sports.standings(competition, season, started)
        }, { value -> table = value; value.fetchedAt < started }, { tableState = it }, onIssue = { repository.failed(competition, it) })
    }

    val freshCompetitions = currentData.filter { (id, value) -> sportsSnapshotFresh(value, loadStates[id], now) }.keys
    val gameToMatch = if (teamsOpen || !broadcastContentVisible(visible, screen, section, selected != null, upcoming)) null
        else if (selectedId != null) selected else mine.firstOrNull { game ->
            game.status == MatchStatus.SCHEDULED && (game.faceoff >= now || localMatches.any {
                it.fixture.id == game.id && it.channelStops.values.any { stop -> stop > now } })
        }
    val scheduleFresh = gameToMatch?.competition?.id in freshCompetitions
    LaunchedEffect(available, enabled, broadcastEnabled, gameToMatch, scheduleFresh) {
        broadcast = null
        if (available && enabled && broadcastEnabled && scheduleFresh && gameToMatch != null) {
            val fixture = gameToMatch.broadcastFixture()
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
    val validBroadcast = if (broadcastEnabled && enabled) gameToMatch?.let { BroadcastResolver.usable(it.broadcastFixture(), broadcast, Instant.ofEpochMilli(now)) } else null
    LaunchedEffect(active, profileId, libraryContext, libraryCount) {
        if (active && profileId != null) hasChannels = liveVm.homeHasChannels(profileId)
    }
    LaunchedEffect(active, screen, picker, selectedId, query, profileId, libraryContext, libraryCount, favorites) {
        if (!active || !screen || !picker || selectedId != null || profileId == null) return@LaunchedEffect
        matching = true
        try {
            hasChannels = liveVm.homeHasChannels(profileId)
            channels = liveVm.homeMatchCandidates(query, favorites, profileId, emptyList()).channels
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { matchFailed = true; SportsDiagnostics.failure(error, HockeyStage.CACHE) }
        finally { matching = false }
    }
    LaunchedEffect(available, profileId, gameToMatch, query, favorites, scheduleFresh, validBroadcast, libraryContext, libraryCount, choiceVersion, localMatches) {
        channels = emptyList(); confirmed = emptyList(); matchedGame = null; matchFailed = false
        chosenChannel = null; manualChoice = null; matchedBroadcast = null; candidatesComplete = false
        if (!available || gameToMatch == null || profileId == null) { matching = false; return@LaunchedEffect }
        val game = gameToMatch
        matching = true
        try {
            delay(300)
            hasChannels = liveVm.homeHasChannels(profileId)
            val manual = overrides.read(profileId, game.broadcastFixture(), now)
            chosenChannel = manual?.let { value -> liveVm.homeChannel(value.channelId, profileId)?.takeIf {
                value.usable(profileId, game.broadcastFixture(), now, it.id, it.sourceId, it.name)
            } }
            manualChoice = manual.takeIf { chosenChannel != null }
            val pool = liveVm.homeMatchCandidates("", favorites, profileId, validBroadcast?.channels.orEmpty().map { it.name })
            val discovered = liveVm.homeBroadcastCandidates(validBroadcast?.channels.orEmpty(), profileId)
            val suggestions = if (query.isBlank()) pool else liveVm.homeMatchCandidates(query, emptyList(), profileId, emptyList())
            val found = if (validBroadcast != null) discovered.channels else pool.channels
            val epgSupport = withContext(Dispatchers.IO) {
                val parser = HockeyEpgParser(selectedTeams, System.currentTimeMillis(),
                    externalGames.filter { it.competition == game.competition && scheduleFresh })
                found.filter { channel -> scheduleFresh &&
                    liveVm.homeStoredProgrammes(channel, game.faceoff - 60 * 60_000, game.faceoff + 3 * 60 * 60_000).any { epg ->
                    parser.parse(epg)?.fixture?.id == game.id
                } }
            }
            val matches = if (validBroadcast != null) discovered.channels.sortedBy { it !in epgSupport } else epgSupport
            val local = localMatches.singleOrNull { it.fixture.id == game.id }
            val localSupport = local?.channelIds.orEmpty().filter { (local?.channelStops?.get(it) ?: 0) > System.currentTimeMillis() }
                .mapNotNull { liveVm.homeChannel(it, profileId) }
            channels = suggestions.channels.filter { !BroadcastResolver.eventPlaceholder(it.name) || it in epgSupport }
            confirmed = (localSupport + matches).distinctBy { it.id }; matchedGame = game
            matchedBroadcast = validBroadcast
            candidatesComplete = if (local != null) !local.truncated else if (validBroadcast != null) !discovered.truncated else !pool.truncated
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { matchFailed = true }
        finally { matching = false }
    }
    LaunchedEffect(restoreTarget, screen, selectedId, picker) {
        val target = restoreTarget ?: return@LaunchedEffect
        withFrameNanos { }; withFrameNanos { }
        if (!target.requestSafely()) backFocus.requestSafely()
        restoreTarget = null
    }
    LaunchedEffect(screen) {
        // Saved match IDs can exist before cached fixtures load. Back is always attached.
        if (screen && restoreTarget == null) {
            withFrameNanos { }; backFocus.requestSafely()
        }
    }
    fun close() { screen = false; selectedId = null; picker = false; searchOpen = false; query = ""; restoreTarget = entryFocus }
    fun back() {
        keyboard?.hide()
        if (browsing) {
            if (categoryId != null) { categoryId = null; categoryPage = 0 } else browsing = false
            restoreTarget = pickerFocus
            return
        }
        val previous = MatchcenterNavigation(section, selectedId, picker, searchOpen).back()
        if (previous == null) close() else {
            val old = selectedId
            selectedId = previous.gameId; picker = previous.channelPicker; searchOpen = previous.searchOpen; query = ""
            restoreTarget = if (previous.channelPicker) pickerFocus else if (previous.gameId != null) detailFocus else gameFocus[old] ?: tabFocus
        }
    }
    fun choose(game: SportFixture) { selectedId = game.id; picker = false; searchOpen = false; browsing = false; query = ""; restoreTarget = detailFocus }
    fun open() { screen = true; restoreTarget = tabFocus }
    fun play(channel: ChannelEntity, list: List<ChannelEntity>, expected: SportFixture? = gameToMatch) {
        // Shell saves this destination during fullscreen; Back returns to the match/picker.
        // An unavailable/external-player handoff also keeps the user's selection intact.
        val profile = profileId ?: return
        if (!active) return
        val game = expected
        scope.launch {
            val actual = liveVm.homeChannel(channel.id, profile) ?: return@launch
            if (tv.own.owntv.core.customize.CustomizeKeys.channel(actual) != tv.own.owntv.core.customize.CustomizeKeys.channel(channel)) return@launch
            val proof = localMatches.singleOrNull { it.fixture.id == game?.id }
            val manualForGame = game?.let { target -> overrides.read(profile, target.broadcastFixture(), System.currentTimeMillis())
                ?.usable(profile, target.broadcastFixture(), System.currentTimeMillis(), actual.id, actual.sourceId, actual.name) } == true
            if (game != null && proof != null && channel.id in proof.channelIds && !manualForGame) {
                try {
                    val at = System.currentTimeMillis()
                    val known = externalGames.filter { sportsSnapshotFresh(currentData[it.competition.id], loadStates[it.competition.id], at) }
                    val parser = HockeyEpgParser(selectedTeams, at, known)
                    val valid = withContext(Dispatchers.IO) { withTimeout(8_000) {
                        liveVm.homeHockeyProgrammes(actual, at, profile).any { parser.parse(it)?.fixture?.id == game.id }
                    } }
                    if (!valid) {
                        localMatches = localMatches.map { if (it.fixture.id == game.id) it.copy(channelIds = it.channelIds - channel.id) else it }
                            .filter { it.channelIds.isNotEmpty() }
                        return@launch
                    }
                } catch (timeout: TimeoutCancellationException) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive(); localFailed = true
                    SportsDiagnostics.failure(timeout, HockeyStage.CACHE); return@launch
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { localFailed = true; SportsDiagnostics.failure(error, HockeyStage.CACHE); return@launch }
            }
            if (currentActive && currentProfile == profile && liveVm.homeChannel(actual.id, profile) != null) onPlay(actual, list)
        }
    }
    val localEvidence = localMatches.singleOrNull { it.fixture.id == gameToMatch?.id }
    val reliableChannels = confirmed.takeIf { matchedGame == gameToMatch && matchedBroadcast == validBroadcast &&
        (scheduleFresh || localEvidence != null) && !matching }.orEmpty().filter { channel ->
        localEvidence == null || (localEvidence.channelStops[channel.id] ?: 0) > now
    }
    val manualChannel = chosenChannel.takeIf { matchedGame == gameToMatch && !matching && now < (manualChoice?.expiresAt ?: 0L) }
    val directChannel = manualChannel ?: reliableChannels.singleOrNull().takeIf { candidatesComplete }
    fun browseChannels() {
        if (gameToMatch != null) selectedId = gameToMatch.id else selectedId = null
        screen = true; picker = true; browsing = false; searchOpen = false; query = ""; restoreTarget = pickerFocus
    }
    LaunchedEffect(matchedGame, matching, directChannel, reliableChannels, validBroadcast, manualChoice) {
        val game = matchedGame ?: return@LaunchedEffect
        if (!matching && game == gameToMatch) {
            val key = fixtureKey(game.broadcastFixture())
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
    val pickerRows = (if (browsing && categoryId != null) categoryRows?.channels.orEmpty()
        else listOfNotNull(manualChannel) + reliableChannels + channels.takeIf { reliableChannels.isEmpty() || searchOpen }.orEmpty())
        .distinctBy { it.id }.take(48)
    LaunchedEffect(active, screen, picker, browsing, categoryId, categoryPage, profileId, libraryContext) {
        categoryRows = null
        if (active && screen && picker && browsing && profileId != null) {
            categories = liveVm.homeSportsCategories(profileId)
            categoryId?.let { categoryRows = liveVm.homeCategoryChannels(it, categoryPage, profileId) }
        }
    }
    fun pick(channel: ChannelEntity) {
        val game = selected ?: run { play(channel, pickerRows); return }
        val profile = profileId ?: return
        overrides.save(FixtureChannelChoice(profile, fixtureKey(game.broadcastFixture()), channel.id,
            channel.sourceId, channel.name, game.faceoff + 4 * 60 * 60_000L), System.currentTimeMillis())
        choiceVersion++; play(channel, pickerRows)
    }

    Box(Modifier.fillMaxSize()) {
        homeContent({
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
            HomeSportsCard(selectedTeams.teamIds.isNotEmpty(), {
                if (directChannel != null) play(directChannel, reliableChannels.ifEmpty { listOf(directChannel) })
                else if (reliableChannels.isNotEmpty()) browseChannels()
                else if (selectedTeams.teamIds.isEmpty()) teamsOpen = true else open()
            }, Modifier.weight(1f).then(homeCardModifier).focusRequester(entryFocus), watch = directChannel != null || reliableChannels.isNotEmpty()) {
                if (selectedTeams.prominent) {
                    val shown = manualChannel ?: reliableChannels.firstOrNull()
                    HockeyHomeSummary(gameToMatch, now, shown?.name, when {
                        manualChannel != null -> HockeyChannelEvidence.MANUAL
                        shown == null -> HockeyChannelEvidence.UNKNOWN
                        validBroadcast != null -> HockeyChannelEvidence.BROADCAST_SCHEDULE
                        else -> HockeyChannelEvidence.SPECIFIC_EPG
                    }, localEvidence?.timeFromEpg == true, localLoading && localMatches.isEmpty(), localFailed,
                        currentData.values.minOfOrNull { it.fetchedAt }.takeIf { enabled })
                }
            }
            HomeButton(stringResource(R.string.mintv_hockey_other_channels), ::browseChannels, Modifier.width(250.mpx))
            }
        }, screen)
        if (teamsOpen && profileId != null) SportsTeamPicker(profileId) { teamsOpen = false; restoreTarget = entryFocus }
        if (screen) {
            BackHandler(onBack = ::back)
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14202C), MinTvNavy)))
                .padding(start = contentStart, end = 48.mpx, top = reservedTop, bottom = 32.mpx)
                .focusGroup(), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        TvText(stringResource(if (section == MatchcenterSection.SETTINGS) R.string.mintv_companion_settings else R.string.mintv_my_hockey), size = 36, bold = true, lines = 1)
                        TvText(selectedTeams.teamIds.mapNotNull { SportsCatalog.team(it)?.name }.joinToString(stringResource(R.string.mintv_team_separator))
                            .ifBlank { stringResource(R.string.mintv_my_teams) }, color = MinTvMuted, size = 20, lines = 1)
                    }
                    val timestamp = if (section == MatchcenterSection.TABLE) standingsAt.takeIf { it > 0 } else fetchedAt
                    val showingCache = if (section == MatchcenterSection.TABLE) tableState.cached || tableState.issue != null else selectedLoad.cached || selectedLoad.issue != null
                    TvText(if (timestamp != null) stringResource(if (showingCache) R.string.mintv_cached_updated else R.string.mintv_updated, updateLabel(timestamp, now))
                        else sportsStatusText(selectedCompetition, enabled, if (section == MatchcenterSection.TABLE && tableState.issue != null) tableState else selectedLoad),
                        modifier = Modifier.widthIn(max = 440.mpx), size = 18, color = MinTvMuted, lines = 2)
                    HomeButton(stringResource(R.string.mintv_back), ::back, Modifier.focusRequester(backFocus))
                }
                if (selectedId == null && !picker) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                        requiredCompetitions.forEach { competition ->
                            HomeButton(if (competition == SportsCatalog.football) stringResource(R.string.mintv_swedish_football) else competition.name,
                                { competitionId = competition.id; page = 0 })
                        }
                        HomeButton(stringResource(R.string.mintv_choose_teams), { teamsOpen = true })
                    }
                    MatchcenterTabs(section, tabFocus) { section = it; page = 0 }
                    if (section == MatchcenterSection.FARJESTAD || section == MatchcenterSection.MATCHES) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
                            HomeButton(stringResource(R.string.mintv_hockey_other_channels), ::browseChannels)
                            gameToMatch?.let { game ->
                                TvText(pairLabel(game), Modifier.weight(1f), size = 23, bold = true)
                                if (directChannel != null) HomeButton(stringResource(R.string.mintv_watch_match), {
                                    play(directChannel, reliableChannels.ifEmpty { listOf(directChannel) })
                                })
                            }
                        }
                    }
                    if (section == MatchcenterSection.FARJESTAD) {
                        val membership = SportsCatalog.inCompetition(selectedCompetition).map { it.id }.toSet()
                        val followed = selectedTeams.teamIds.filter { it in membership }.mapNotNull { SportsCatalog.team(it) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                            items(followed, key = { it.id }) { team ->
                                Box(Modifier.width(340.mpx)) {
                                    FocusPanel(highlighted = true) {
                                        SportsTeamLabel(team.name)
                                        standings.singleOrNull { SportsCatalog.identity(it.team, selectedCompetition.sport)?.id == team.id }?.let { rank ->
                                            TvText(stringResource(R.string.mintv_fbk_position, NumberFormat.getIntegerInstance().format(rank.rank),
                                                NumberFormat.getIntegerInstance().format(rank.points)), size = 20, color = MinTvTeal)
                                            if (standingsAt > 0) TvText(stringResource(R.string.mintv_cached_updated, updateLabel(standingsAt, now)), size = 16, color = MinTvMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    when (section) {
                        MatchcenterSection.SETTINGS -> LazyColumn(verticalArrangement = Arrangement.spacedBy(20.mpx)) {
                            item { HockeyDiagnosticPanel(repository) }
                            item { TvText(stringResource(R.string.mintv_shl_source_heading), size = 28, bold = true) }
                            item { FocusPanel { TvText(stringResource(R.string.mintv_shl_source_notice), color = MinTvMuted, lines = 6) } }
                            item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                                HomeButton(stringResource(if (enabled) R.string.mintv_shl_disable else R.string.mintv_shl_enable), {
                                    enabled = !enabled; repository.preferences.edit { putBoolean("shl-enabled", enabled) }
                                if (!enabled) { data = emptyMap(); table = null; selectedId = null; broadcast = null; loadStates.clear() }
                                })
                            } }
                            item { data[selectedCompetition.id]?.let { TvText(stringResource(R.string.mintv_shl_snapshot, stockholmDate(it.fetchedAt) + " " + stockholmTime(it.fetchedAt),
                                it.sourceUpdatedAt?.let { at -> stockholmDate(at) + " " + stockholmTime(at) } ?: stringResource(R.string.mintv_companion_unavailable)), color = MinTvMuted, lines = 4) } }
                            if (repository.preferences.getBoolean("shl-access-blocked", false)) item { TvText(stringResource(R.string.mintv_shl_access_blocked), color = MinTvMuted, lines = 4) }
                            item { FocusPanel { TvText(stringResource(R.string.mintv_broadcast_source_notice), color = MinTvMuted, lines = 4) } }
                            item { HomeButton(stringResource(if (broadcastEnabled) R.string.mintv_broadcast_disable else R.string.mintv_broadcast_enable), {
                                broadcastEnabled = !broadcastEnabled
                                repository.preferences.edit { putBoolean("broadcast-enabled", broadcastEnabled) }
                                if (!broadcastEnabled) { broadcast = null; knownBroadcasts.clear(); resolvedCards.clear(); directCards.clear() }
                            }) }
                            if (broadcasts.blocked) item { FocusPanel { TvText(stringResource(R.string.mintv_broadcast_blocked), color = MinTvMuted, lines = 4) } }
                            item { TvText(stringResource(R.string.mintv_sports_diagnostics), size = 28, bold = true) }
                            item { TvText(stringResource(R.string.mintv_sports_budget, repository.preferences.getInt("hockey-window-count", 0)), color = MinTvMuted, lines = 3) }
                            requiredCompetitions.filter { it.scheduleAvailable }.forEach { competition ->
                                item {
                                    TvText(competition.name, bold = true)
                                    repository.lastResponse(competition)?.let { response ->
                                        TvText(stringResource(R.string.mintv_sports_diagnostic, competition.name, response.stage.name,
                                            NumberFormat.getIntegerInstance().format(response.httpCode), stockholmDate(response.at) + " " + stockholmTime(response.at)), color = MinTvMuted, lines = 3)
                                    }
                                    for (isTable in listOf(false, true)) {
                                        val issue = repository.diagnostic(competition, isTable)
                                        TvText(if (issue == null) stringResource(R.string.mintv_sports_no_error)
                                            else stringResource(R.string.mintv_sports_diagnostic, competition.name, issue.stage.name,
                                                issue.httpCode?.let { NumberFormat.getIntegerInstance().format(it) } ?: stringResource(R.string.mintv_companion_unavailable),
                                                stockholmDate(issue.at) + " " + stockholmTime(issue.at)), color = MinTvMuted, lines = 3)
                                        if (issue != null) TvText(stringResource(R.string.mintv_sports_diagnostic_reason,
                                            issue.kind.name, issue.detail?.name ?: issue.stage.name), color = MinTvMuted, lines = 3)
                                    }
                                }
                            }
                        }
                        MatchcenterSection.TABLE -> if (standings.isEmpty()) Box(Modifier.fillMaxWidth()) {
                            SportsStatusPanel(selectedCompetition, enabled, if (data[selectedCompetition.id] != null) tableState else selectedLoad,
                                now, standingsAt.takeIf { it > 0 }, { retryRevision++ })
                        } else LazyColumn(state = tableList, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                            if (tableState.loading || tableState.issue != null) item {
                                SportsStatusPanel(selectedCompetition, enabled, tableState, now, standingsAt, { retryRevision++ }, Modifier.fillMaxWidth())
                            }
                            val showPlayed = standings.isNotEmpty() && standings.all { it.played != null }
                            val showDifference = standings.isNotEmpty() && standings.all { it.goalDifference != null }
                            item { StandingRow(stringResource(R.string.mintv_position), stringResource(R.string.mintv_team),
                                if (showPlayed) stringResource(R.string.mintv_games_played) else null,
                                if (showDifference) stringResource(R.string.mintv_goal_difference) else null, stringResource(R.string.mintv_table_points)) }
                            items(standings, key = { selectedCompetition.id + ":" + it.team }) { standing ->
                                FocusPanel(highlighted = SportsCatalog.identity(standing.team, selectedCompetition.sport)?.id in selectedTeams.teamIds) {
                                StandingRow(NumberFormat.getIntegerInstance().format(standing.rank), shortTeam(standing.team),
                                    standing.played?.takeIf { showPlayed }?.let { NumberFormat.getIntegerInstance().format(it) },
                                    standing.goalDifference?.takeIf { showDifference }?.let { NumberFormat.getIntegerInstance().format(it) },
                                    NumberFormat.getIntegerInstance().format(standing.points))
                                }
                            }
                        }
                        else -> {
                            val future = allGames.filter { it.competition == selectedCompetition }.filter { it.faceoff >= now }.filter { section != MatchcenterSection.FARJESTAD || it.follows(selectedTeams) }
                            val todayList = todaysGames.filter { it.competition == selectedCompetition }.filter { section != MatchcenterSection.FARJESTAD || it.follows(selectedTeams) }
                            val useFuture = upcoming || todayList.isEmpty()
                            val lastPage = (future.size - 1).coerceAtLeast(0) / 14
                            val currentPage = page.coerceIn(0, lastPage)
                            val recent = allGames.filter { it.competition == selectedCompetition && it.follows(selectedTeams) && it.score != null && it.faceoff < now }
                                .sortedByDescending { it.faceoff }.take(5)
                            val games = if (section == MatchcenterSection.FARJESTAD) (future.take(3) + recent).distinctBy { it.id }
                                else if (useFuture) future.drop(currentPage * 14).take(14) else todayList
                            if (games.isEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                                TvText(stringResource(if (localLoading) R.string.mintv_hockey_epg_loading else R.string.mintv_hockey_channel_unknown), size = 26, lines = 3)
                                if (localFailed) TvText(stringResource(R.string.mintv_hockey_epg_failed), color = MinTvMuted, lines = 2)
                                HomeButton(stringResource(R.string.mintv_hockey_channels), ::browseChannels)
                                if (enabled && selectedLoad.issue != null) TvText(stringResource(R.string.mintv_sports_fetch_failed), color = MinTvMuted, lines = 2)
                            } else LazyColumn(state = matchList, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                                if (selectedLoad.loading || selectedLoad.issue != null) item {
                                    SportsStatusPanel(selectedCompetition, enabled, selectedLoad, now, fetchedAt, { retryRevision++ }, Modifier.fillMaxWidth())
                                }
                                item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                                    if (todayList.isNotEmpty()) HomeButton(stringResource(if (useFuture) R.string.mintv_shl_today else R.string.mintv_shl_upcoming), { upcoming = !useFuture; page = 0 })
                                    else TvText(stringResource(R.string.mintv_shl_upcoming), size = 22, color = MinTvMuted)
                                    if (useFuture && currentPage > 0) HomeButton(stringResource(R.string.mintv_shl_previous_page), { page = currentPage - 1 })
                                    if (useFuture && currentPage < lastPage) HomeButton(stringResource(R.string.mintv_shl_next_page), { page = currentPage + 1 })
                                } }
                                items(games, key = { it.id }) { game ->
                                    val focus = remember(profileId, game.id) { gameFocus.getOrPut(game.id) { FocusRequester() } }
                                    val key = fixtureKey(game.broadcastFixture())
                                    val assignment = if (enabled && broadcastEnabled) BroadcastResolver.usable(game.broadcastFixture(), knownBroadcasts[key], Instant.ofEpochMilli(now)) else null
                                    val cached = resolvedCards[key]?.takeIf { now < it.first && game.competition.id in freshCompetitions }
                                    val epg = localMatches.singleOrNull { it.fixture.id == game.id }
                                    val epgChannels = epg?.channelIds.orEmpty().filter { (epg?.channelStops?.get(it) ?: 0) > now }.mapNotNull { localChannels[it] }
                                    val direct = if (game == gameToMatch) directChannel else epgChannels.singleOrNull().takeIf { epg?.truncated == false } ?: directCards[key].takeIf { cached != null }
                                    MatchScorecard(game, now, selectedTeams.hideScores, game.follows(selectedTeams), epgChannels.firstOrNull()?.name ?: assignment?.channels?.filter { it.linear }?.joinToString { it.name }, { choose(game) },
                                        direct?.let { channel -> { play(channel, epgChannels.ifEmpty { cached?.second.orEmpty().ifEmpty { listOf(channel) } }, game) } }, Modifier.fillMaxWidth().focusRequester(focus))
                                }
                            }
                        }
                    }
                } else if (picker) {
                    TvText(if (selected != null) pairLabel(selected) else stringResource(R.string.mintv_hockey_channels), size = 30, bold = true)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                        if (hasChannels == false) {
                            item { TvText(stringResource(R.string.mintv_no_channels), size = 27, lines = 3) }
                            item { HomeButton(stringResource(R.string.mintv_add_source), onSources, Modifier.focusRequester(pickerFocus)) }
                        } else if (hasChannels == true) {
                        if (matching) item { TvText(stringResource(R.string.mintv_companion_loading)) }
                        if (matchFailed) item { TvText(stringResource(R.string.mintv_companion_unavailable)) }
                        if (browsing && categoryId == null) {
                            items(categories, key = { it.id }) { category -> HomeButton(category.name, {
                                categoryId = category.id; categoryPage = 0; restoreTarget = pickerFocus
                            }, Modifier.fillMaxWidth().then(if (category == categories.firstOrNull()) Modifier.focusRequester(pickerFocus) else Modifier)) }
                            if (categories.isEmpty()) item { TvText(stringResource(R.string.mintv_no_channel_matches)) }
                        } else {
                        if (reliableChannels.isNotEmpty() && !browsing) item { TvText(stringResource(R.string.mintv_confirmed_channels), color = MinTvTeal, bold = true) }
                        items(pickerRows, key = { it.id }) { channel ->
                            ChannelCard(channel.name, channel.displayLogoUrl,
                                stringResource(if (channel in reliableChannels) R.string.mintv_confirmed_source else R.string.mintv_unconfirmed_source,
                                    NumberFormat.getIntegerInstance().format(channel.sourceId)), { pick(channel) },
                                Modifier.fillMaxWidth().then(if (channel == pickerRows.firstOrNull() && !searchOpen) Modifier.focusRequester(pickerFocus) else Modifier),
                                selected = channel == manualChannel)
                        }
                        if (pickerRows.isEmpty() && !matching) item { TvText(stringResource(R.string.mintv_no_channel_matches), color = MinTvMuted) }
                        if (browsing) item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                            if (categoryPage > 0) HomeButton(stringResource(R.string.mintv_shl_previous_page), { categoryPage--; restoreTarget = pickerFocus })
                            if (categoryRows?.truncated == true) HomeButton(stringResource(R.string.mintv_shl_next_page), { categoryPage++; restoreTarget = pickerFocus })
                            HomeButton(stringResource(R.string.mintv_browse_categories), { categoryId = null; categoryPage = 0; restoreTarget = pickerFocus })
                        } }
                        }
                        if (!browsing) item { HomeButton(stringResource(R.string.mintv_browse_channels), {
                            browsing = true; categoryId = null; categoryPage = 0; searchOpen = false; restoreTarget = pickerFocus
                        }, if (pickerRows.isEmpty() && !searchOpen) Modifier.focusRequester(pickerFocus) else Modifier) }
                        item { HomeButton(stringResource(if (searchOpen) R.string.mintv_close_search else R.string.mintv_channel_search), {
                            if (searchOpen) back() else { browsing = false; searchOpen = true; restoreTarget = searchFocus }
                        }) }
                        if (searchOpen) item { CompanionInput(query, Modifier.focusRequester(searchFocus)) { query = it.take(80) } }
                        item { TvText(stringResource(R.string.mintv_shl_manual), size = 20, color = MinTvMuted) }
                        } else item { TvText(stringResource(R.string.mintv_companion_loading)) }
                    }
                } else if (selected == null) TvText(stringResource(R.string.mintv_companion_unavailable))
                else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.mpx)) {
                        item { TvText(pairLabel(selected), size = 38, bold = true); TvText(fixtureWhen(selected, now), size = 26, color = MinTvMuted) }
                        selected.result?.takeUnless { selectedTeams.hideScores }?.let { result -> item { TvText(stringResource(R.string.mintv_result_snapshot, result), size = 27, color = MinTvMuted) } }
                        validBroadcast?.let { assignment -> item { TvText(assignment.channels.filter { it.linear }.joinToString { it.name }, size = 30, color = MinTvTeal, bold = true)
                            TvText(stringResource(R.string.mintv_updated, updateLabel(assignment.checkedAt.toEpochMilli(), now)), size = 18, color = MinTvMuted) } }
                        if (manualChannel != null) item { TvText(stringResource(R.string.mintv_saved_channel, manualChannel.name), size = 25) }
                        if (localEvidence != null) item {
                            TvText(stringResource(R.string.mintv_hockey_epg_evidence), color = MinTvTeal, size = 21)
                            if (localEvidence.timeFromEpg) TvText(stringResource(R.string.mintv_hockey_epg_time), color = MinTvMuted, size = 18)
                        }
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
                            profileId?.let { overrides.clear(it, selected.broadcastFixture()) }; choiceVersion++
                        }) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MatchScorecard(game: SportFixture, now: Long, hideScores: Boolean, favorite: Boolean, broadcaster: String?, onClick: () -> Unit, onWatch: (() -> Unit)?, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
    TvCard(onClick, Modifier.weight(1f), selected = favorite) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TvText(game.competition.name, size = 18, color = MinTvMuted, lines = 1)
            TvText(fixtureWhen(game, now), size = 20, color = MinTvMuted, lines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
            SportsTeamLabel(game.home, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TvText(game.result?.takeUnless { hideScores } ?: stockholmTime(game.faceoff), size = 32, bold = true, lines = 1)
                if (game.result != null && !hideScores) TvText(stringResource(R.string.mintv_saved_result), size = 16, color = MinTvMuted, lines = 1)
            }
            SportsTeamLabel(game.away, Modifier.weight(1f))
        }
        TvText(broadcaster?.takeIf(String::isNotBlank) ?: stringResource(R.string.mintv_shl_choose), size = 19, color = MinTvTeal, lines = 1)
    }
    onWatch?.let { HomeButton(stringResource(R.string.mintv_watch_match), it, primary = true) }
    }
}

/** Text monograms from verified club names, never unverified official logos or image downloads. */
@Composable
private fun SportsTeamLabel(name: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.mpx).background(Color(0xFF0D1722), CircleShape), contentAlignment = Alignment.Center) {
            TvText(shortTeam(name).take(2).uppercase(java.util.Locale.ROOT), size = 19, color = MinTvTeal, bold = true, lines = 1)
        }
        TvText(shortTeam(name), Modifier.weight(1f), size = 27, bold = true, lines = 2)
    }
}

@Composable
private fun StandingRow(position: String, team: String, played: String?, difference: String?, points: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
        TvText(position, Modifier.width(60.mpx), size = 23, bold = true, lines = 1)
        TvText(team, Modifier.weight(1f), size = 25, bold = true, lines = 1)
        played?.let { TvText(it, Modifier.width(100.mpx), size = 23, lines = 1) }
        difference?.let { TvText(it, Modifier.width(120.mpx), size = 23, lines = 1) }
        TvText(points, Modifier.width(80.mpx), size = 25, color = MinTvTeal, bold = true, lines = 1)
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
internal fun pairLabel(game: SportFixture): String = stringResource(R.string.mintv_shl_pair, shortTeam(game.home), shortTeam(game.away))

@Composable
internal fun fixtureWhen(game: SportFixture, now: Long): String {
    val day = stockholmDay(game.faceoff)
    val date = when (day) {
        stockholmDay(now) -> stringResource(R.string.mintv_today)
        stockholmDay(now).plusDays(1) -> stringResource(R.string.mintv_tomorrow)
        else -> stockholmDate(game.faceoff)
    }
    return stringResource(R.string.mintv_fixture_time, date, stockholmTime(game.faceoff))
}

private data class SportsCardResolution(val channels: List<ChannelEntity>, val direct: ChannelEntity?, val expiresAt: Long)

/** Uses only the current profile's local channels/EPG and an already checked metadata assignment. */
private suspend fun resolveSportsCard(game: SportFixture, allGames: List<SportFixture>, now: Long,
    fresh: Boolean, assignment: BroadcastAssignment?, profileId: Long, liveVm: LiveViewModel,
    overrides: ShlChannelOverrides, favorites: List<ChannelEntity>): SportsCardResolution {
    val fixture = game.broadcastFixture()
    val manual = overrides.read(profileId, fixture, now)
    val chosen = manual?.let { choice -> liveVm.homeChannel(choice.channelId, profileId)?.takeIf {
        choice.usable(profileId, fixture, now, it.id, it.sourceId, it.name)
    } }
    val pool = if (assignment != null) liveVm.homeBroadcastCandidates(assignment.channels, profileId)
        else liveVm.homeMatchCandidates("", favorites, profileId, emptyList())
    val confirmed = if (assignment != null) pool.channels else if (!fresh) emptyList() else withContext(Dispatchers.IO) {
        pool.channels.filter { channel -> liveVm.homeStoredProgrammes(channel, game.faceoff - 60 * 60_000,
            game.faceoff + 3 * 60 * 60_000).any { epg ->
            ShlEpgMatcher.confirmed(game.hockeyGame(), epg.title, epg.description, epg.startMs, epg.stopMs,
                allGames.filter { it.competition == game.competition && kotlin.math.abs(it.faceoff - game.faceoff) < 3 * 60 * 60_000 }.map { it.hockeyGame() })
        } }
    }
    val direct = chosen ?: confirmed.singleOrNull().takeIf { !pool.truncated }
    val expiry = if (chosen != null) manual.expiresAt else assignment?.expiresAt?.toEpochMilli() ?: Long.MAX_VALUE
    return SportsCardResolution(confirmed, direct, minOf(now + 2 * 60_000L, expiry))
}
