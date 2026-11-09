package tv.own.owntv.features.sporthub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.home.*
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.format.formatBestDateTime
import tv.own.owntv.ui.theme.mpx
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Native optional public-data hub. Opening/focusing discovery never fetches every league. */
@Composable
internal fun SportHubScreen(profileId: Long, active: Boolean, liveVm: LiveViewModel, favorites: List<ChannelEntity>,
    onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit,
    onEntry: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit, initialRoute: HubRoute = HubRoute(HubPage.DISCOVER),
    restoreAfterPlayback: Boolean = false, onPlaybackEntry: (((() -> Boolean)?) -> Unit)? = null) {
    val store = koinInject<SportsPreferences>()
    val swedish = koinInject<SportsRepository>()
    val hockey = koinInject<ShlRepository>()
    val reader = koinInject<HubSourceReader>()
    val overrides = koinInject<ShlChannelOverrides>()
    val eventReader = koinInject<TvmatchenBroadcastSource>()
    val publicReader = koinInject<PublicHockeySource>()
    val revision by store.changes.collectAsStateWithLifecycle()
    val library by liveVm.homeLibraryContext.collectAsStateWithLifecycle()
    val flags = rememberCompanionFlags(hockey.preferences)
    val swedishEnabled = flags.first
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var selection by remember(profileId) { mutableStateOf<SportProfileSelection?>(null) }
    var history by rememberSaveable(profileId, initialRoute.key) { mutableStateOf(initialRoute.key) }
    val route = HubRoute.decode(history.substringAfterLast('\n')) ?: HubRoute(HubPage.DISCOVER)
    val league = route.league?.let(HubLeagues::find)
    var dayText by rememberSaveable(profileId) { mutableStateOf(LocalDate.now(HubStockholm).toString()) }
    val day = LocalDate.parse(dayText)
    var filter by rememberSaveable(profileId, route.league) { mutableStateOf("") }
    var channelQuery by rememberSaveable(profileId) { mutableStateOf("") }
    var sportFilter by rememberSaveable(profileId) { mutableStateOf(HubSport.ICE_HOCKEY.name) }
    var search by remember(profileId) { mutableStateOf(false) }
    var draft by remember(profileId) { mutableStateOf("") }
    var failure by remember(profileId) { mutableStateOf(false) }
    var saving by remember(profileId) { mutableStateOf(false) }
    var loading by remember(profileId) { mutableStateOf(false) }
    var watchLoading by remember(profileId) { mutableStateOf(false) }
    var refresh by remember(profileId) { mutableIntStateOf(0) }
    var reads by remember(profileId) { mutableStateOf<Map<String, HubRead>>(emptyMap()) }
    var originals by remember(profileId) { mutableStateOf<Map<String, SportSnapshot>>(emptyMap()) }
    var table by remember(profileId, route.league) { mutableStateOf<HockeyTable?>(null) }
    var candidates by remember(profileId, route.entity) { mutableStateOf<List<Pair<ChannelEntity, HubProgrammeProof?>>>(emptyList()) }
    var watchReadContext by remember(profileId) { mutableStateOf<List<Any?>?>(null) }
    var candidateContext by remember(profileId) { mutableStateOf<List<Any?>?>(null) }
    var manual by remember(profileId, route.entity) { mutableStateOf<FixtureChannelChoice?>(null) }
    var event by remember(profileId, route.entity) { mutableStateOf<BroadcastAssignment?>(null) }
    var confirm by remember(profileId, route.entity) { mutableStateOf<ChannelEntity?>(null) }
    var entry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    val currentProfile by rememberUpdatedState(profileId)
    val currentActive by rememberUpdatedState(active)
    val currentSelection by rememberUpdatedState(selection)
    val currentReads by rememberUpdatedState(reads)
    val first = remember(profileId) { FocusRequester() }
    val pages = key(profileId) { rememberSaveableStateHolder() }
    val savedPages = remember(profileId) { mutableSetOf<String>() }
    // Clock-only invalidation, lifecycle bounded. This never triggers HTTP or guide polling.
    val now by produceState(Instant.now(), active) {
        if (active) while (true) { value = Instant.now(); delay(60_000) }
    }
    val read = league?.let { reads[it.key] }
    val snapshot = read?.snapshot
    val match = hubCurrentMatch(snapshot, route.entity, now).takeIf { route.page == HubPage.MATCH }
    val prefs = selection?.value ?: SportPreferences()
    fun push(next: HubRoute) { history = hubPush(history, next); confirm = null }
    fun back() { confirm?.let { confirm = null; return }; hubPop(history)?.let { history = it } ?: onDismiss() }
    BackHandler(enabled = active, onBack = ::back)
    LaunchedEffect(profileId, revision) {
        selection = try { store.readSelection(profileId) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true; null }
    }
    LaunchedEffect(profileId, prefs.hubProviders, swedishEnabled) {
        reads = reads.filterValues { it.snapshot?.provider?.let { p ->
            if (p == HubProvider.SWEDISH) swedishEnabled else p in prefs.hubProviders
        } == true }
        if (!swedishEnabled) originals = emptyMap()
    }
    DisposableEffect(profileId) { onEntry { entry?.invoke() ?: false }; onDispose { onEntry(null) } }
    LaunchedEffect(history) {
        val parts = history.split('\n')
        val keep = parts.indices.map { parts.take(it + 1).joinToString("\n") }.toSet()
        (savedPages - keep).forEach { pages.removeState(it) }
        savedPages.clear(); savedPages.addAll(keep)
        if (!restoreAfterPlayback) { repeat(2) { withFrameNanos { } }; entry?.invoke() }
    }
    fun update(change: (SportPreferences) -> SportPreferences) {
        val owner = selection?.owner ?: return
        if (saving || !active) return
        saving = true
        scope.launch {
            try {
                if (!store.updateSelection(profileId, owner, change)) failure = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = true }
            finally { saving = false }
        }
    }
    LaunchedEffect(active, profileId, league, route.page, dayText, prefs.hubProviders, swedishEnabled, selection?.owner, refresh) {
        if (!active || league == null || route.page !in setOf(HubPage.LEAGUE, HubPage.TEAM, HubPage.MATCH, HubPage.CALENDAR, HubPage.TABLE)) return@LaunchedEffect
        val owner = selection?.owner ?: return@LaunchedEffect
        if (league.access !in setOf(HubAccess.VERIFIED_PUBLIC, HubAccess.EXISTING_READER) || !hubBindingCurrent(league,Instant.now())) {
            reads = reads + (league.key to HubRead(null, if (league.access == HubAccess.AUTH_REQUIRED) HubIssue.AUTH_REQUIRED else HubIssue.ACCESS_DENIED))
            return@LaunchedEffect
        }
        val allowed = { currentActive && currentProfile == profileId && currentSelection?.owner == owner &&
            (if (league.provider == HubProvider.SWEDISH) swedishEnabled else league.provider in currentSelection!!.value.hubProviders) }
        if (!allowed()) { reads = reads + (league.key to HubRead(null, HubIssue.DISABLED)); return@LaunchedEffect }
        loading = true
        try {
            val comp = hubSwedishCompetition(league.key)
            val value = if (comp != null) {
                swedish.cached(comp)?.takeIf { allowed() }?.let {
                    originals = originals + (league.key to it); reads = reads + (league.key to HubRead(it.toHub(), cached = true))
                }
                val fresh = swedish.refresh(comp, System.currentTimeMillis())
                if (fresh != null && allowed()) { originals = originals + (league.key to fresh); HubRead(fresh.toHub()) }
                else HubRead(null, HubIssue.UNAVAILABLE)
            } else {
                val clock = Instant.now()
                val window = if (route.page == HubPage.CALENDAR) HubWindow.stockholmDay(day) else
                    clock.atZone(java.time.ZoneOffset.UTC).toLocalDate().let { HubWindow(it.minusDays(7), it.plusDays(7)) }
                withContext(Dispatchers.Default) { reader.read(league, hubSeason(league, clock), clock, allowed, window) }
            }
            if (allowed()) {
                reads = reads + (league.key to value)
                HubSessionSnapshots.publish(value)
            }
            if (route.page == HubPage.TABLE && comp != null && allowed()) {
                table = value.snapshot?.let { swedish.standings(comp, it.season, System.currentTimeMillis()) }?.takeIf { allowed() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (allowed()) reads = reads + (league.key to HubRead(reads[league.key]?.snapshot, HubIssue.UNAVAILABLE, cached = true)) }
        finally { loading = false }
    }
    suspend fun cachedEvent(expected: HubMatch): BroadcastAssignment? {
        if (!hockey.preferences.getBoolean("shl-enabled", false) || !hockey.preferences.getBoolean("broadcast-enabled", false)) return null
        val original = originals[expected.league] ?: return null
        val fixture = original.fixtures.singleOrNull { it.id == expected.id } ?: return null
        val at = Instant.now()
        return withTimeoutOrNull(1_500) {
            val local = publicReader.cachedAssignments(original.fixtures, at.toEpochMilli(), true)[fixtureKey(fixture.broadcastFixture())]
            val other = eventReader.cachedAssignment(fixture.broadcastFixture())
            mergedHockeyAssignment(fixture.broadcastFixture(), local, other, at)
        }
    }
    fun grade(expected: HubMatch, channel: ChannelEntity, proof: HubProgrammeProof?, assignment: BroadcastAssignment?, choice: FixtureChannelChoice?, at: Instant): HubWatchConfidence =
        hubWatchConfidence(proof?.usable(expected, at) == true && !BroadcastResolver.streamingBrand(channel.name),
            hubEventConfidence(originals[expected.league]?.fixtures?.singleOrNull { it.id == expected.id }, assignment, channel.name, at),
            choice?.usable(profileId, hubChoiceFixture(expected, selection?.owner ?: -1), at.toEpochMilli(), channel.id, channel.sourceId, channel.name) == true)
    LaunchedEffect(active, profileId, route.key, match, library, channelQuery, selection?.owner, refresh, flags.second) {
        val identity = listOf(profileId, route.key, match, library, channelQuery, selection?.owner, flags.second)
        if (candidateContext != identity) {
            candidates = emptyList(); manual = null; event = null; confirm = null; candidateContext = identity
        }
        val expected = match ?: return@LaunchedEffect
        val owner = selection?.owner ?: return@LaunchedEffect
        if (!active || library.first != profileId || expected.status == HubMatchStatus.REPORTED_FINAL) return@LaunchedEffect
        watchLoading = true
        try {
            val guide = HubGuideMatcher(expected, originals[expected.league], prefs, snapshot?.matches.orEmpty(), Instant.now())
            val rows = withContext(Dispatchers.Default) { withTimeout(8_000) {
                liveVm.homeSportsChannels(channelQuery, favorites, profileId).map { channel ->
                    val programmes = liveVm.homeStoredProgrammes(channel, expected.startsAt.toEpochMilli() - 90 * 60_000,
                        expected.startsAt.toEpochMilli() + 6 * 3_600_000, profileId)
                    val proof = programmes.mapNotNull(guide::proof).singleOrNull()?.takeUnless { BroadcastResolver.streamingBrand(channel.name) }
                    channel to proof
                }
            } }
            val assignment = cachedEvent(expected)
            val choice = withContext(Dispatchers.IO) { overrides.read(profileId, hubChoiceFixture(expected, owner), System.currentTimeMillis()) }
            if (currentActive && currentProfile == profileId && currentSelection?.owner == owner && liveVm.homeLibraryContext.value == library) {
                event = assignment; manual = choice
                val at = Instant.now()
                val proofs = rows.associate { it.first.id to it.second }
                candidates = hubRankWatchChannels(rows.map { it.first }) {
                    grade(expected,it,proofs[it.id],assignment,choice,at)
                }.map { it to proofs[it.id] }
            }
        } catch (_: TimeoutCancellationException) { failure = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
        finally { watchLoading = false }
        if (currentActive && currentProfile == profileId && currentSelection?.owner == owner && candidateContext == identity) {
            watchReadContext = identity
        }
    }
    // Fullscreen disposes browse composition. Never let its temporary empty rows consume
    // the saved Watch Match focus before the schedule and authorized channels rehydrate.
    val returnReady = selection != null && library.first == profileId && !loading &&
        (league == null || read != null) && (route.page != HubPage.MATCH || match == null ||
        match.status == HubMatchStatus.REPORTED_FINAL || (candidateContext != null && watchReadContext == candidateContext))
    DisposableEffect(profileId, returnReady, entry) {
        onPlaybackEntry?.invoke(entry.takeIf { returnReady })
        onDispose { onPlaybackEntry?.invoke(null) }
    }
    fun play(channel: ChannelEntity, confidence: HubWatchConfidence) {
        val expected = match ?: return
        val owner = selection?.owner ?: return
        if (saving || !active) return
        saving = true
        scope.launch {
            try {
                val actual = liveVm.homeChannel(channel.id, profileId)?.takeIf { it.sourceId == channel.sourceId && it.name == channel.name }
                val current = hubCurrentMatch(currentReads[expected.league]?.snapshot, expected.id, Instant.now())
                val identity = store.readSelection(profileId)?.owner == owner && current == expected && currentActive && currentProfile == profileId
                if (actual == null || !identity) { failure = true; return@launch }
                if (confidence == HubWatchConfidence.GUIDE) {
                    val programmes = liveVm.homeStoredProgrammes(actual, expected.startsAt.toEpochMilli() - 90 * 60_000,
                        expected.startsAt.toEpochMilli() + 6 * 3_600_000, profileId)
                    val at = Instant.now()
                    val proofs = withContext(Dispatchers.Default) {
                        val guide = HubGuideMatcher(expected, originals[expected.league], prefs, currentReads[expected.league]?.snapshot?.matches.orEmpty(), at)
                        programmes.mapNotNull(guide::proof)
                    }
                    if (proofs.singleOrNull()?.usable(expected, at) != true) { failure = true; return@launch }
                } else if (confidence == HubWatchConfidence.EVENT) {
                    val assignment = cachedEvent(expected)
                    if (hubEventConfidence(originals[expected.league]?.fixtures?.singleOrNull { it.id == expected.id },assignment,actual.name,Instant.now()) != HubWatchConfidence.EVENT) {
                        failure = true; return@launch
                    }
                } else withContext(Dispatchers.IO) {
                    val fixture = hubChoiceFixture(expected, owner)
                    overrides.save(FixtureChannelChoice(profileId, fixtureKey(fixture), actual.id, actual.sourceId, actual.name,
                        expected.startsAt.plusSeconds(6 * 3600).toEpochMilli()), System.currentTimeMillis())
                }
                if (currentActive && currentProfile == profileId && currentSelection?.owner == owner && liveVm.homeChannel(actual.id, profileId) == actual) {
                    confirm = null
                    onPlay(actual, listOf(actual))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = true }
            finally { saving = false }
        }
    }
    val title = stringResource(when (route.page) {
        HubPage.DISCOVER -> R.string.hub_discover; HubPage.SPORTS -> R.string.hub_sports; HubPage.CALENDAR -> R.string.hub_calendar
        HubPage.MINE -> R.string.hub_my_teams; HubPage.SOURCES -> R.string.hub_sources
        HubPage.MATCH -> R.string.hub_match; HubPage.TEAM -> R.string.hub_team
        HubPage.TABLE -> R.string.hub_table; HubPage.LEAGUE -> league?.label ?: R.string.hub_discover
    })
    val sections = buildList {
        fun tile(key: String, label: Int, action: String = key, subtitle: String = "") = HubTile(key, resources.getString(label), subtitle, action)
        fun matches(rows: List<HubMatch>, key: String = "matches", interactive: Boolean = true) {
            add(HubSection(key, resources.getString(R.string.hub_matches), rows.take(120).map { m ->
                val result = if (prefs.hideScores) resources.getString(R.string.hub_scores_hidden) else m.score?.let {
                    resources.getString(R.string.hub_score, it.home, it.away)
                } ?: resources.getString(if (m.status == HubMatchStatus.REPORTED_FINAL) R.string.hub_result_missing else
                    if (m.status == HubMatchStatus.UNCONFIRMED) R.string.hub_unconfirmed else R.string.hub_scheduled)
                HubTile("match-${m.id}", resources.getString(R.string.hub_pair, m.home.name, m.away.name),
                    formatBestDateTime(context, "dMMM", m.startsAt.toEpochMilli()) + " · " + result,
                    if (interactive) HubRoute(HubPage.MATCH, m.league, m.id).key else null)
            }))
            if (rows.isEmpty()) add(HubSection("no-matches", "", listOf(HubTile("no-matches", resources.getString(R.string.hub_no_matches), resources.getString(R.string.hub_no_matches_detail)))))
        }
        when (route.page) {
            HubPage.DISCOVER -> {
                add(HubSection("discovery-controls", resources.getString(HubSport.valueOf(sportFilter).hubLabel()), listOf(
                    tile("choose-sport", R.string.hub_sports, HubRoute(HubPage.SPORTS).key), tile("sources", R.string.hub_sources))))
                add(HubSection("leagues", resources.getString(R.string.hub_leagues), HubLeagues.reviewed.filter { it.sport.name == sportFilter }.map { l ->
                    HubTile("league-${l.key}", resources.getString(l.label), resources.getString(if (l.access in setOf(HubAccess.VERIFIED_PUBLIC, HubAccess.EXISTING_READER) && hubBindingCurrent(l,now))
                        R.string.hub_available else R.string.hub_unverified), HubRoute(HubPage.LEAGUE, l.key).key)
                }))
                if (HubLeagues.reviewed.none { it.sport.name == sportFilter }) add(HubSection("unsupported", "", listOf(
                    HubTile("unsupported", resources.getString(R.string.hub_unverified), resources.getString(R.string.hub_no_source)))))
            }
            HubPage.SPORTS -> {
                add(HubSection("sports", resources.getString(R.string.hub_sports), HubSport.entries.map { sport ->
                    HubTile("sport-${sport.name}", resources.getString(sport.hubLabel()), "", "sport/${sport.name}", sport.name == sportFilter)
                }))
            }
            HubPage.MINE -> {
                val legacy = prefs.teamIds.mapNotNull { id -> SportsCatalog.team(id)?.let { t ->
                    val comp = listOf(SportsCatalog.shl, SportsCatalog.allsvenskan).firstOrNull { t in SportsCatalog.inCompetition(it) }
                    HubTile("follow-$id", t.name, resources.getString(R.string.hub_saved_team), comp?.let { HubRoute(HubPage.TEAM, it.id, id).key })
                } }
                add(HubSection("follows", resources.getString(R.string.hub_followed), legacy + prefs.hubTeams.map { t ->
                    HubTile("follow-${t.id}", t.name, resources.getString(R.string.hub_saved_team), HubRoute(HubPage.TEAM, t.league, t.id).key)
                }))
                if (legacy.isEmpty() && prefs.hubTeams.isEmpty()) add(HubSection("no-teams", "", listOf(tile("mine-find-teams", R.string.hub_choose_teams, "hub-discover"))))
            }
            HubPage.SOURCES -> add(HubSection("sources", resources.getString(R.string.hub_opt_in), listOf(HubProvider.OPENLIGA, HubProvider.EVENTS24).map { p ->
                HubTile("consent-${p.name}", resources.getString(p.hubLabel()), resources.getString(if (p in prefs.hubProviders) R.string.hub_enabled else R.string.hub_disabled),
                    "consent/${p.name}", p in prefs.hubProviders)
            } + HubTile("rights", resources.getString(R.string.hub_source_rights), resources.getString(R.string.hub_source_limits))))
            HubPage.LEAGUE, HubPage.TEAM -> {
                if (league != null) {
                    val team = snapshot?.teams?.singleOrNull { it.id == route.entity }
                        ?: prefs.hubTeams.firstOrNull { it.id == route.entity }?.let { HubTeam(it.id, it.name) }
                        ?: SportsCatalog.team(route.entity.orEmpty())?.let { HubTeam(it.id, it.name) }
                    val buttons = mutableListOf(tile("calendar", R.string.hub_calendar, HubRoute(HubPage.CALENDAR, league.key).key),
                        tile("table", R.string.hub_table, HubRoute(HubPage.TABLE, league.key).key), tile("sources", R.string.hub_sources), tile("retry", R.string.hub_retry))
                    if (route.page == HubPage.TEAM && team != null) buttons.add(tile("toggle-team", if (team.id in prefs.teamIds || prefs.hubTeams.any { it.id == team.id }) R.string.hub_unfollow else R.string.hub_follow))
                    if (route.page == HubPage.LEAGUE) buttons.add(tile("search", R.string.hub_search, subtitle = filter))
                    add(HubSection("league-actions", team?.name ?: resources.getString(league.label), buttons))
                    if (route.page == HubPage.LEAGUE) add(HubSection("teams", resources.getString(R.string.hub_teams_window),
                        snapshot?.teams.orEmpty().filter { hubTeamNameKey(it.name).contains(hubTeamNameKey(filter)) }.take(64).map { t ->
                            HubTile("team-${t.id}", t.name, if (t.id in prefs.teamIds || prefs.hubTeams.any { it.id == t.id }) resources.getString(R.string.hub_following) else "",
                                HubRoute(HubPage.TEAM, league.key, t.id).key)
                        }))
                    val games = if (route.page == HubPage.TEAM) hubTeamMatches(snapshot?.matches.orEmpty(), route.entity.orEmpty()) else snapshot?.matches.orEmpty()
                    matches(games.filter { it.startsAt >= now.minusSeconds(7 * 86_400) })
                }
            }
            HubPage.CALENDAR -> {
                add(HubSection("dates", day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), listOf(
                    tile("day-before", R.string.hub_day_before), tile("day-today", R.string.hub_today), tile("day-after", R.string.hub_day_after))))
                matches(hubDayMatches(snapshot?.matches.orEmpty(), day))
            }
            HubPage.TABLE -> {
                val actual = table?.takeIf { league?.provider == HubProvider.SWEDISH && it.seasonId == snapshot?.season }
                if (actual == null) add(HubSection("table-missing", "", listOf(HubTile("table-missing", resources.getString(R.string.hub_table_missing), resources.getString(R.string.hub_table_no_guess)))))
                else add(HubSection("table", formatBestDateTime(context, "dMMM", actual.fetchedAt), actual.rows.map { row ->
                    HubTile("standing-${row.rank}", resources.getString(R.string.hub_rank, row.rank, row.team),
                        if (prefs.hideScores) resources.getString(R.string.hub_scores_hidden) else resources.getQuantityString(R.plurals.hub_points, row.points, row.points))
                }))
            }
            HubPage.MATCH -> {
                if (match == null) add(HubSection("match-missing", "", listOf(HubTile("match-missing", resources.getString(R.string.hub_match_missing)))))
                else {
                    matches(listOf(match), "selected-match", interactive = false)
                    add(HubSection("match-teams", resources.getString(R.string.hub_teams), listOf(match.home, match.away).map { t ->
                        HubTile("match-team-${t.id}", t.name, "", HubRoute(HubPage.TEAM, match.league, t.id).key)
                    }))
                    if (match.status != HubMatchStatus.REPORTED_FINAL) {
                        add(HubSection("watch", resources.getString(R.string.hub_watch), listOf(tile("search-channel", R.string.hub_search_channel, subtitle = channelQuery),
                            tile("retry", R.string.hub_retry)) + candidates.map { (channel, proof) ->
                            val confidence = grade(match,channel,proof,event,manual,now)
                            HubTile("watch-${channel.id}", channel.name, resources.getString(when (confidence) {
                                HubWatchConfidence.GUIDE -> R.string.hub_verified_epg
                                HubWatchConfidence.EVENT -> R.string.hub_verified_event
                                HubWatchConfidence.MANUAL -> R.string.hub_remembered
                                HubWatchConfidence.LIKELY -> R.string.hub_likely
                                HubWatchConfidence.UNKNOWN -> R.string.hub_manual_unknown
                            }), "watch/${channel.id}")
                        }))
                        if (candidates.isEmpty()) add(HubSection("no-channels", "", listOf(HubTile("no-channels", resources.getString(R.string.hub_no_channels)))))
                    }
                }
            }
        }
        if (read?.issue == HubIssue.DISABLED && league != null) add(HubSection("enable", "", listOf(tile("enable-source",
            if (league.provider == HubProvider.SWEDISH) R.string.hub_settings else R.string.hub_sources,
            if (league.provider == HubProvider.SWEDISH) "settings" else "sources"))))
    }
    val status = buildList {
        if (failure) add(stringResource(R.string.hub_error))
        if (loading) add(stringResource(R.string.hub_loading))
        if (watchLoading) add(stringResource(R.string.hub_loading))
        if (league != null) add(stringResource(league.provider.hubLabel()))
        read?.issue?.let { issue -> add(stringResource(when (issue) {
            HubIssue.DISABLED -> R.string.hub_disabled; HubIssue.WAITING, HubIssue.RATE_LIMITED -> R.string.hub_rate_limited
            HubIssue.AUTH_REQUIRED -> R.string.hub_auth_required; HubIssue.ACCESS_DENIED -> R.string.hub_unverified
            HubIssue.INVALID_DATA -> R.string.hub_invalid_data; HubIssue.UNAVAILABLE -> R.string.hub_error
        })) }
        snapshot?.let { s ->
            add(stringResource(if (read?.freshness(now) == HubFreshness.FRESH) R.string.hub_fetched else R.string.hub_cached,
                formatBestDateTime(context, "dMMM", s.fetchedAt.toEpochMilli())))
            if (s.hasMore || s.coverage.isNotEmpty()) add(stringResource(R.string.hub_partial))
        }
        if (route.page == HubPage.DISCOVER) add(stringResource(R.string.hub_discovery_hint))
    }.joinToString(" · ")
    fun action(id: String) {
        when {
            id == "hub-back" -> back()
            id == "hub-discover" -> push(HubRoute(HubPage.DISCOVER))
            id == "hub-calendar" -> push(HubRoute(HubPage.CALENDAR, league?.key ?: HubLeagues.shl.key))
            id == "hub-mine" -> push(HubRoute(HubPage.MINE))
            id == "sources" -> push(HubRoute(HubPage.SOURCES))
            id == "settings" -> onSettings()
            id == "retry" -> refresh++
            id.startsWith("sport/") -> { sportFilter = id.substringAfter('/'); hubPop(history)?.let { history = it } }
            id.startsWith("consent/") -> {
                val provider = HubProvider.valueOf(id.substringAfter('/'))
                update { it.copy(hubProviders = if (provider in it.hubProviders) it.hubProviders - provider else it.hubProviders + provider) }
            }
            id == "day-before" || id == "day-after" || id == "day-today" -> {
                val next = if (id == "day-today") LocalDate.now(HubStockholm) else day.plusDays(if (id == "day-before") -1 else 1)
                if (league?.provider != HubProvider.EVENTS24 || HubWindow.stockholmDay(next).allowed(Instant.now())) dayText = next.toString()
            }
            id == "search" || id == "search-channel" -> { draft = if (id == "search") filter else channelQuery; search = true }
            id == "toggle-team" -> {
                val team = snapshot?.teams?.singleOrNull { it.id == route.entity }
                    ?: prefs.hubTeams.firstOrNull { it.id == route.entity }?.let { HubTeam(it.id,it.name) }
                    ?: SportsCatalog.team(route.entity.orEmpty())?.let { HubTeam(it.id, it.name) }
                if (team != null && league != null) update { p -> if (league.provider == HubProvider.SWEDISH) p.toggle(team.id)
                    else p.copy(hubTeams = if (p.hubTeams.any { it.id == team.id }) p.hubTeams.filterNot { it.id == team.id }
                        else p.hubTeams + HubFollow(team.id, team.name, league.key, league.sport)) }
            }
            id.startsWith("watch/") -> candidates.singleOrNull { it.first.id.toString() == id.substringAfter('/') }?.let { (channel, proof) ->
                val confidence = match?.let { grade(it,channel,proof,event,manual,Instant.now()) } ?: HubWatchConfidence.UNKNOWN
                if (confidence in setOf(HubWatchConfidence.GUIDE,HubWatchConfidence.EVENT)) play(channel, confidence) else confirm = channel
            }
            else -> HubRoute.decode(id)?.let(::push)
        }
    }
    pages.SaveableStateProvider(history) {
        NativeSportHubPage(title, status, sections, profileId, first, { entry = it }, onHeader, ::action)
    }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(confirm?.id) { if (confirm != null) { repeat(2) { withFrameNanos { } }; confirmFocus.requestSafely() } }
    fun cancelChoice() { confirm = null; scope.launch { repeat(2) { withFrameNanos { } }; entry?.invoke() } }
    confirm?.let { channel -> Dialog(onDismissRequest = ::cancelChoice, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(1000.mpx).clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(30.mpx), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            TvText(stringResource(R.string.hub_manual_warning), size = 30, bold = true)
            TvText(channel.name, size = 28, bold = true)
            TvText(stringResource(R.string.hub_confirm_manual), size = 23, color = MinTvMuted, lines = 5)
            if (failure) TvText(stringResource(R.string.hub_error), color = MinTvMuted)
            HomeButton(stringResource(R.string.hub_open_channel), { play(channel, HubWatchConfidence.MANUAL) },
                Modifier.focusRequester(confirmFocus).testTag("confirm-channel"), primary = true)
            HomeButton(stringResource(R.string.hub_cancel), ::cancelChoice)
        }
    } }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(search) { if (search) { repeat(2) { withFrameNanos { } }; searchFocus.requestSafely() } }
    if (search) Dialog(onDismissRequest = { search = false; scope.launch { repeat(2) { withFrameNanos { } }; entry?.invoke() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(900.mpx).clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(28.mpx), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
            TvText(stringResource(if (route.page == HubPage.MATCH) R.string.hub_search_channel else R.string.hub_search))
            BasicTextField(draft, { draft = it.take(80) }, modifier = Modifier.fillMaxWidth().background(MinTvPanel).padding(20.mpx).focusRequester(searchFocus),
                singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 24.sp))
            HomeButton(stringResource(R.string.hub_apply), {
                if (route.page == HubPage.MATCH) channelQuery = draft else filter = draft
                search = false; scope.launch { repeat(2) { withFrameNanos { } }; entry?.invoke() }
            })
        }
    }
}

internal fun HubProvider.hubLabel(): Int = when (this) {
    HubProvider.SWEDISH -> R.string.hub_source_swedish
    HubProvider.OPENLIGA -> R.string.hub_source_openliga
    HubProvider.EVENTS24 -> R.string.hub_source_events
    else -> R.string.hub_no_source
}
internal fun HubSport.hubLabel(): Int = when (this) {
    HubSport.ICE_HOCKEY -> R.string.hub_sport_hockey; HubSport.FOOTBALL -> R.string.hub_sport_football
    HubSport.BASKETBALL -> R.string.hub_sport_basketball; HubSport.TENNIS -> R.string.hub_sport_tennis
    HubSport.CRICKET -> R.string.hub_sport_cricket; HubSport.RACING -> R.string.hub_sport_racing
    HubSport.AMERICAN_FOOTBALL -> R.string.hub_sport_american; HubSport.MMA -> R.string.hub_sport_mma
    HubSport.BASEBALL -> R.string.hub_sport_baseball; HubSport.GOLF -> R.string.hub_sport_golf
    HubSport.RUGBY -> R.string.hub_sport_rugby; HubSport.RUGBY_LEAGUE -> R.string.hub_sport_rugby_league
    HubSport.AUSSIE_RULES -> R.string.hub_sport_aussie; HubSport.VOLLEYBALL -> R.string.hub_sport_volleyball
    HubSport.LACROSSE -> R.string.hub_sport_lacrosse
}
