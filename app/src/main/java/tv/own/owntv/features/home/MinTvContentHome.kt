package tv.own.owntv.features.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.features.discovery.*
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.live.*
import tv.own.owntv.features.update.MinTvUpdater
import tv.own.owntv.features.update.UpdateDialog
import tv.own.owntv.home.MinTvExternalApps
import tv.own.owntv.home.MinTvIntents
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private val HomeNavy = MinTvNavy
private val HomeTeal = MinTvTeal

/** Home owns presentation only. Playback, EPG, history, OAuth and verified updates keep their owners. */
@Composable
internal fun MinTvContentHome(
    vm: HomeViewModel, liveVm: LiveViewModel, activeProfileId: Long?, previewEnabled: Boolean,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit, onPauseOrDispose: () -> Unit,
    onLiveTv: () -> Unit, onGuide: () -> Unit, onPlayMovie: (Long, Long) -> Unit,
    onOpenMovie: (DiscoveryCard) -> Unit, onPlayEpisode: (Long, Long, Long) -> Unit,
    onOpenSeries: (Long) -> Unit, onSources: () -> Unit, onManageSources: () -> Unit,
    onChildFocused: () -> Unit, restoreFocus: Boolean, onRestored: () -> Unit,
    firstRowFocusRequester: FocusRequester, onEntryHook: ((() -> Boolean)?) -> Unit,
    contentStart: Dp, reservedTop: Dp = 144.mpx, modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val library by liveVm.homeLibraryState.collectAsStateWithLifecycle()
    val libraryContext by liveVm.homeLibraryContext.collectAsStateWithLifecycle()
    val metadata by liveVm.swedishRowContext.collectAsStateWithLifecycle()
    val settings: SettingsRepository = koinInject()
    val db: OwnTVDatabase = koinInject()
    val channelStore: SwedishChannelStore = koinInject()
    val sectionStore: HomeSections = koinInject()
    val sports: SportsPreferences = koinInject()
    val updater: MinTvUpdater = koinInject()
    val updateState by updater.state.collectAsStateWithLifecycle()
    val previewsOn by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val foreground = previewEnabled && lifecycleState == Lifecycle.State.RESUMED
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val controller = liveVm.homePreview
    val previewState by liveVm.previewEngine.state.collectAsStateWithLifecycle()
    val blocked by liveVm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val providerNames by liveVm.providerNames.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val quickList = rememberLazyListState()
    val nowList = rememberLazyListState()
    val navigation = rememberHomeDpadNavigation(firstRowFocusRequester, 10, activeProfileId, firstSection = 0)
    var matchcenterEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var matchcenterOpen by remember { mutableStateOf(false) }
    var preferencesOpen by remember { mutableStateOf(false) }
    var updateOpen by remember { mutableStateOf(false) }
    var variantsOpen by remember { mutableStateOf(false) }
    var chooseTeams by remember { mutableStateOf(false) }
    var missingApp by remember { mutableStateOf<ExternalShortcut?>(null) }
    var launchFailed by remember { mutableStateOf(false) }
    var restoreModalFocus by remember { mutableStateOf(false) }
    var preferencesLoaded by remember(activeProfileId) { mutableStateOf(false) }
    var hidden by remember(activeProfileId) { mutableStateOf<Set<HomeSection>>(emptySet()) }
    var order by remember(activeProfileId) { mutableStateOf(defaultHomeOrder) }
    var choices by remember(activeProfileId) { mutableStateOf(SwedishChoices()) }
    var profileName by remember(activeProfileId) { mutableStateOf<String?>(null) }
    var sportsSelection by remember(activeProfileId) { mutableStateOf(SportPreferences()) }
    val sportsRevision by sports.changes.collectAsStateWithLifecycle()
    var quick by remember(activeProfileId, libraryContext, metadata) { mutableStateOf<List<Pair<String, ChannelEntity>>>(emptyList()) }
    var hero by remember(activeProfileId, libraryContext, metadata) { mutableStateOf<ChannelEntity?>(null) }
    var heroAlternatives by remember(activeProfileId, libraryContext, metadata) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var quickFocused by rememberSaveable(activeProfileId) { mutableStateOf<Long?>(null) }
    var nowFocused by rememberSaveable(activeProfileId) { mutableStateOf<Long?>(null) }
    var heroFocused by remember { mutableStateOf(false) }
    val modal = preferencesOpen || updateOpen || variantsOpen || chooseTeams || missingApp != null || launchFailed
    val usable = foreground && preferencesLoaded && !modal && !matchcenterOpen
    val favorites = remember(state.favoriteLive, state.profileId, activeProfileId, library) { state.favoriteLive.takeIf { state.profileId == activeProfileId && library.profileId == activeProfileId }.orEmpty()
        .filter { it.sourceId in library.sourceIds && library.visibleFavoriteIds?.contains(it.id) != false } }
    val watchedKey = homeHistoryCandidate(state, activeProfileId, libraryContext.second)?.homeEpgKey()
    var heroChoice by rememberSaveable(activeProfileId, watchedKey) { mutableStateOf<String?>(null) }
    val continuations = remember(state.continueMovies, state.continueSeries, state.profileId, activeProfileId) { homeContinuations(state, activeProfileId) }
    val discovery: DiscoveryRepository = koinInject()
    val moviesVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.MOVIES } } }
    val seriesVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.SERIES } } }
    val movieDiscovery = rememberDiscovery(MetadataType.MOVIE, moviesVisible, usable && HomeSection.MOVIES !in hidden)
    val seriesDiscovery = rememberDiscovery(MetadataType.TV, seriesVisible, usable && HomeSection.SERIES !in hidden)
    val movieRow = movieDiscovery.rows.firstOrNull { it.kind == DiscoveryKind.TOP }
    val seriesRow = seriesDiscovery.rows.firstOrNull { it.kind == DiscoveryKind.TOP }
    val showSport = HomeSection.SPORT !in hidden && sportsSelection.prominent
    val showTwitch = HomeSection.TWITCH !in hidden
    val installedApps = remember(context, foreground) { ExternalShortcut.entries.filter { MinTvExternalApps.launch(context, it.packages) != null } }
    val rows = normalizedHomeOrder(order).filter { section -> when (section) {
        HomeSection.LIVE -> true
        HomeSection.SPORT -> showSport || showTwitch
        HomeSection.TWITCH -> false
        HomeSection.FAVORITES, HomeSection.NOW -> section !in hidden && quick.isNotEmpty()
        HomeSection.CONTINUE -> section !in hidden && continuations.isNotEmpty()
        HomeSection.MOVIES -> section !in hidden && movieRow != null
        HomeSection.SERIES -> section !in hidden && seriesRow != null
        HomeSection.APPS -> section !in hidden && installedApps.isNotEmpty()
    } }
    val sectionItems = dashboardIndices(rows, showSport, showTwitch)
    val heroVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.LIVE } } }
    val companionsVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.SPORT } } }
    val quickVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.FAVORITES } } }
    val nowVisible by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.any { it.key == HomeSection.NOW } } }
    val visibleChannelKeys by remember(quick, hero, heroVisible, quickVisible, nowVisible, hidden) { derivedStateOf {
        val visible = buildList {
            if (heroVisible && HomeSection.LIVE !in hidden) hero?.let { add(it.homeEpgKey()) }
            listOf(quickVisible to quickList, nowVisible to nowList).forEach { (shown, rail) ->
                if (shown) {
                    val indices = rail.layoutInfo.visibleItemsInfo.map { it.index }
                    (indices + ((indices.lastOrNull() ?: -1) + 1..minOf(quick.lastIndex, (indices.lastOrNull() ?: -1) + 2)))
                        .distinct().mapNotNull { quick.getOrNull(it)?.second?.homeEpgKey() }.forEach(::add)
                }
            }
        }
        visible.distinct().take(SWEDISH_EPG_WINDOW)
    } }
    val guide = rememberHomeDashboardGuide(liveVm, activeProfileId, usable, visibleChannelKeys, hero)
    val heroGuide = hero?.let { guide.rows[it.homeEpgKey()]?.at(guide.at) }
    val previewAllowed = usable && previewsOn && heroVisible && heroFocused && HomeSection.LIVE !in hidden
    LaunchedEffect(activeProfileId) {
        activeProfileId?.let { hidden = sectionStore.hidden(it); order = sectionStore.order(it); choices = channelStore.choices(it); profileName = db.profileDao().getById(it)?.name; preferencesLoaded = true }
    }
    LaunchedEffect(activeProfileId, sportsRevision) { activeProfileId?.let { sportsSelection = sports.read(it) } }
    LaunchedEffect(library, foreground) { if (foreground && library.profileId == activeProfileId && library.status != HomeLibraryStatus.LOADING) vm.refresh(activeProfileId) }
    // Refresh only a small channel index after actual catalog/access invalidations. Never a VOD catalog read.
    val summariesActive = foreground && preferencesLoaded && !matchcenterOpen
    LaunchedEffect(activeProfileId, state.recentLive, favorites, libraryContext, metadata, choices, heroChoice, summariesActive) {
        if (!summariesActive) return@LaunchedEffect
        hero = null; heroAlternatives = emptyList(); quick = emptyList()
        val profile = activeProfileId ?: return@LaunchedEffect
        val expected = metadata?.takeIf { it.profileId == profile && libraryContext.first == profile && it.sourceIds == libraryContext.second } ?: return@LaunchedEffect
        try {
            val candidate = homeHistoryCandidate(state, profile, expected.sourceIds)
            val watched = candidate?.let { liveVm.homeChannel(it.id, profile) }?.takeIf { it.homeEpgKey() == candidate.homeEpgKey() }
            // A valid watch does not wait for the optional channel index when no override exists.
            if (liveVm.swedishContextCurrent(expected)) hero = watched
            val groups = channelStore.library(profile, expected.sourceIds, choices).groups
            val group = groups.firstOrNull { g -> g.variants.any { it.id == watched?.id } }
            val alternatives = group?.variants.orEmpty().take(12).mapNotNull { liveVm.homeChannel(it.id, profile) }
            val prioritized = (state.recentLive + favorites).mapNotNull { channel -> groups.firstOrNull { g -> g.variants.any { it.id == channel.id } } }
            val wanted = (prioritized + groups.filter { it.variants.firstOrNull()?.name?.collection == SwedishCollection.GENERAL })
                .distinctBy { it.identity }.take(12)
            val resolved = wanted.mapNotNull { g -> g.preferred(choices.preferred[g.identity], choices.showUhd)?.let { variant ->
                liveVm.homeChannel(variant.id, profile)?.let { g.label to it }
            } }
            if (liveVm.swedishContextCurrent(expected)) { hero = homeHeroChannel(watched, alternatives, heroChoice); heroAlternatives = alternatives; quick = resolved }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* An optional summary must never block opening Live TV. */ }
    }
    val pause by rememberUpdatedState(onPauseOrDispose)
    DisposableEffect(vm, controller) {
        vm.setPreviewEnabled(false); vm.stopPreview()
        onDispose { pause(); controller.setActive(false) }
    }
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { pause(); controller.setActive(false) } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.setActive(false) }
    }
    LaunchedEffect(previewAllowed, hero?.id, activeProfileId) {
        controller.setActive(false); controller.focus(hero.takeIf { previewAllowed }); controller.setActive(previewAllowed)
    }
    val currentSectionItems by rememberUpdatedState(sectionItems)
    val currentMatchcenter by rememberUpdatedState(matchcenterOpen)
    fun restoreEntry(): Boolean = if (currentMatchcenter) matchcenterEntry?.invoke() == true else
        navigation.focus[navigation.selected.takeIf { currentSectionItems.getOrNull(it) != -1 } ?: 0].requestSafely()
    DisposableEffect(firstRowFocusRequester) { onEntryHook { restoreEntry() }; onDispose { onEntryHook(null) } }
    ReconcileHomeNavigation(navigation, list, sectionItems, usable)
    LaunchedEffect(restoreFocus, restoreModalFocus, usable) {
        if (usable && (restoreFocus || restoreModalFocus)) {
            val focusChannel = when (navigation.selected) { 1 -> quickFocused; 8 -> nowFocused; else -> null }
            if (focusChannel != null) {
                val index = quick.indexOfFirst { it.second.id == focusChannel }.coerceAtLeast(0)
                (if (navigation.selected == 1) quickList else nowList).scrollToItem(index)
            }
            if (!matchcenterOpen) list.scrollToItem(sectionItems.getOrNull(navigation.selected)?.takeIf { it >= 0 } ?: 1)
            withFrameNanos { }; withFrameNanos { }; restoreEntry(); restoreModalFocus = false; onRestored()
        }
    }
    fun stop() { pause(); controller.setActive(false) }
    fun play(channel: ChannelEntity, channels: List<ChannelEntity>) {
        val profile = activeProfileId ?: return
        scope.launch {
            val actual = liveVm.homeChannel(channel.id, profile) ?: return@launch
            if (actual.homeEpgKey() == channel.homeEpgKey() && liveVm.homeLibraryContext.value.first == profile) onPlayChannel(actual, channels)
        }
    }
    fun openExternal(intent: Intent?) {
        stop(); vm.stopPreview()
        if (intent == null) { launchFailed = true; return }
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { launchFailed = true }
        catch (_: SecurityException) { launchFailed = true }
    }
    MinSportCompanion(companionsVisible && showSport && preferencesLoaded, foreground && preferencesLoaded && !modal, liveVm, activeProfileId, favorites,
        onPlayChannel, contentStart, { stop(); onSources() }, reservedTop,
        homeCardModifier = navigation.section(3), onMatchcenterEntry = { matchcenterEntry = it }, compact = true) { hockeyCard, screen ->
        LaunchedEffect(screen) { matchcenterOpen = screen; if (screen) stop() }
        if (!screen) HomeDashboardFrame(list, rows, contentStart, reservedTop,
            modifier.onPreviewKeyEvent { event ->
                // Only deliberate remote movement INSIDE the hero can arm its muted preview.
                if (heroFocused && previewAllowed && event.type == KeyEventType.KeyDown &&
                    event.key in listOf(Key.DirectionLeft, Key.DirectionRight)) controller.onRemoteNavigation()
                false
            }.then(homeDpadModifier(navigation, list, sectionItems)).onFocusChanged { if (it.hasFocus) onChildFocused() },
            header = { Box(Modifier.onFocusChanged { if (it.hasFocus) navigation.selected = 9 }.focusGroup()) { HomeDashboardHeader(profileName, updateState, { stop(); updateOpen = true },
                { stop(); preferencesOpen = true }, navigation.section(9)) } }) { section ->
            when (section) {
                HomeSection.LIVE -> {
                    val selected = hero
                    val hasVideo = previewAllowed && selected != null && previewState !in listOf(LivePreviewEngine.State.IDLE, LivePreviewEngine.State.ERROR)
                    val message = when {
                        !previewsOn -> R.string.mintv_preview_disabled
                        blocked -> R.string.content_preview_single_stream
                        previewState == LivePreviewEngine.State.ERROR -> R.string.mintv_preview_unsupported
                        hasVideo && previewState == LivePreviewEngine.State.LOADING -> R.string.mintv_preview_loading
                        else -> R.string.mintv_preview_hint
                    }
                    if (HomeSection.LIVE in hidden) DashboardAction(stringResource(R.string.mintv_home_live), { stop(); onLiveTv() }, navigation.section(0))
                    else HomeLiveHero(selected?.name, selected?.displayLogoUrl,
                        heroGuide?.now?.let { XtEpgEntry(it.title, guide.synopsis, it.startMs, it.stopMs) }, heroGuide?.next?.title, guide.at,
                        stringResource(message), { selected?.let { play(it, listOf(it) + quick.map { row -> row.second }) } ?: run { stop(); onLiveTv() } },
                        { stop(); onLiveTv() }, { stop(); onGuide() }, { preferencesOpen = true },
                        Modifier.onFocusChanged { heroFocused = it.hasFocus; if (it.hasFocus) navigation.selected = 0 }, navigation.section(0),
                        onVariants = { stop(); variantsOpen = true }.takeIf { heroAlternatives.size > 1 }) {
                        if (hasVideo) ExoPreviewSurface(liveVm.previewEngine, Modifier.fillMaxSize(), useTextureView = selected.drmConfig == null)
                        else if (selected?.displayLogoUrl != null) AsyncImage(selected.displayLogoUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.size(140.mpx))
                        else TvText(stringResource(R.string.app_name), size = 40, color = MinTvTeal, bold = true)
                    }
                }
                HomeSection.SPORT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                    if (showSport) Box(Modifier.weight(1.15f)) { hockeyCard() }
                    if (showTwitch) Box(Modifier.weight(1f).onFocusChanged { if (it.hasFocus) navigation.selected = 6 }.focusGroup()) { TwitchCompanion(companionsVisible, usable, navigation.section(6), compact = true) }
                }
                HomeSection.TWITCH -> Unit
                HomeSection.FAVORITES, HomeSection.NOW -> {
                    val nowRow = section == HomeSection.NOW
                    val rail = if (nowRow) nowList else quickList
                    val index = if (nowRow) 8 else 1
                    val storedFocus = if (nowRow) nowFocused else quickFocused
                    val entryId = storedFocus?.takeIf { id -> quick.any { it.second.id == id } } ?: quick.firstOrNull()?.second?.id
                    Column(Modifier.onFocusChanged { if (it.hasFocus) navigation.selected = index }.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TvText(stringResource(if (nowRow) R.string.mintv_v08_now else R.string.mintv_v08_quick_channels), size = 27, bold = true, lines = 1)
                            DashboardAction(stringResource(R.string.mintv_home_live), { stop(); onLiveTv() })
                        }
                        LazyRow(state = rail, contentPadding = PaddingValues(5.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx), modifier = Modifier.focusGroup()) {
                            itemsIndexed(quick, key = { _, item -> item.second.homeEpgKey() }) { i, (label, channel) ->
                                val info = guide.rows[channel.homeEpgKey()]?.at(guide.at)
                                val cardModifier = Modifier.width(if (nowRow) 330.mpx else 230.mpx)
                                    .then(if (channel.id == entryId) navigation.section(index) else Modifier)
                                    .onFocusChanged { if (it.hasFocus) { navigation.selected = index; if (nowRow) nowFocused = channel.id else quickFocused = channel.id } }
                                val watch = { play(channel, quick.map { it.second }) }
                                if (nowRow) HomeNowChannelCard(label, channel.displayLogoUrl, SwedishChannels.recognize(channel.name)?.quality,
                                    info, guide.at, watch, cardModifier)
                                else ChannelCard(label, channel.displayLogoUrl, null, watch, cardModifier)
                            }
                        }
                    }
                }
                HomeSection.CONTINUE -> HomeContinuationRow(continuations, navigation.section(2)) { item ->
                    stop(); vm.stopPreview()
                    when (item.kind) {
                        tv.own.owntv.core.launcher.LauncherContinuationKind.MOVIE -> onPlayMovie(item.sourceItemId, item.positionMs)
                        tv.own.owntv.core.launcher.LauncherContinuationKind.EPISODE -> onPlayEpisode(0L, item.targetItemId, item.positionMs)
                        else -> Unit
                    }
                }
                HomeSection.MOVIES, HomeSection.SERIES -> {
                    val movie = section == HomeSection.MOVIES
                    val row = (if (movie) movieRow else seriesRow)!!
                    DiscoveryPosterRow(row, if (movie) MetadataType.MOVIE else MetadataType.TV,
                        if (movie) movieDiscovery.rankedAt else seriesDiscovery.rankedAt, { card ->
                            scope.launch { activeProfileId?.let { if (discovery.available(card, it)) { stop(); vm.stopPreview(); if (movie) onOpenMovie(card) else onOpenSeries(card.id) } } }
                        }, first = navigation.section(if (movie) 4 else 5))
                }
                HomeSection.APPS -> Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    installedApps.forEachIndexed { i, app ->
                        DashboardAction(stringResource(app.label), { openExternal(MinTvExternalApps.launch(context, app.packages)) }, if (i == 0) navigation.section(7) else Modifier)
                    }
                }
            }
        }
    }
    if (updateOpen) UpdateDialog({ updateOpen = false; restoreModalFocus = true })
    if (variantsOpen) HomeVariantDialog(heroAlternatives, providerNames, { channel ->
        heroChoice = channel.homeEpgKey().stableKey
        activeProfileId?.let { profile -> scope.launch {
            val name = SwedishChannels.recognize(channel.name)
            val groups = channelStore.library(profile, libraryContext.second, choices).groups
            val group = groups.firstOrNull { g -> g.variants.any { it.id == channel.id } }
            if (name != null && group != null) {
                choices = choices.copy(preferred = choices.preferred + (group.identity to channel.homeEpgKey().stableKey))
                channelStore.save(profile, choices)
            }
        } }
        variantsOpen = false; restoreModalFocus = true
    }, { variantsOpen = false; restoreModalFocus = true })
    if (preferencesOpen) HomeLayoutDialog(hidden, order, previewsOn, { section ->
        activeProfileId?.let { profile -> scope.launch { val next = if (section in hidden) hidden - section else hidden + section; sectionStore.save(profile, next); hidden = next } }
    }, { next -> activeProfileId?.let { profile -> scope.launch { sectionStore.saveOrder(profile, next); order = normalizedHomeOrder(next) } } },
        { activeProfileId?.let { profile -> scope.launch { sectionStore.save(profile, emptySet()); sectionStore.saveOrder(profile, defaultHomeOrder); hidden = emptySet(); order = defaultHomeOrder } } },
        { controller.setActive(false); scope.launch { settings.setLivePreviewEnabled(!previewsOn) } }, { chooseTeams = true },
        { preferencesOpen = false; stop(); if (library.status == HomeLibraryStatus.NO_SOURCE) onSources() else onManageSources() },
        { preferencesOpen = false; openExternal(MinTvIntents.settings()) },
        { preferencesOpen = false; restoreModalFocus = true })
    if (chooseTeams && activeProfileId != null) SportsTeamPicker(activeProfileId) { chooseTeams = false }
    if (launchFailed || missingApp != null) Dialog(onDismissRequest = { launchFailed = false; missingApp = null; restoreModalFocus = true }) {
        Column(Modifier.width(700.mpx).background(HomeNavy).padding(30.mpx), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
            TvText(stringResource(R.string.mintv_external_failed))
            HomeButton(stringResource(R.string.mintv_close), { launchFailed = false; missingApp = null; restoreModalFocus = true })
        }
    }
}

@Composable
private fun HomeVariantDialog(channels: List<ChannelEntity>, sources: Map<Long, String>, onSelect: (ChannelEntity) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
        Column(Modifier.width(760.mpx).heightIn(max = 820.mpx).background(HomeNavy).padding(28.mpx).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            TvText(stringResource(R.string.mintv_v08_source_quality), size = 28, bold = true)
            channels.forEachIndexed { index, channel -> HomeButton(stringResource(R.string.mintv_v08_variant, channel.name, sources[channel.sourceId].orEmpty()), { onSelect(channel) }, if (index == 0) Modifier.focusRequester(first) else Modifier) }
            HomeButton(stringResource(R.string.mintv_close), onDismiss, if (channels.isEmpty()) Modifier.focusRequester(first) else Modifier)
        }
    }
}

@Composable
private fun HomeLayoutDialog(hidden: Set<HomeSection>, order: List<HomeSection>, previews: Boolean,
    toggle: (HomeSection) -> Unit, reorder: (List<HomeSection>) -> Unit, reset: () -> Unit,
    togglePreview: () -> Unit, teams: () -> Unit, sources: () -> Unit, systemSettings: () -> Unit, dismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = dismiss) {
        LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
        Column(Modifier.width(820.mpx).heightIn(max = 850.mpx).background(HomeNavy, RoundedCornerShape(20.mpx)).padding(28.mpx).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
            TvText(stringResource(R.string.mintv_v08_customize), size = 30, bold = true)
            HomeButton(stringResource(if (previews) R.string.mintv_preview_on else R.string.mintv_preview_off), togglePreview, Modifier.focusRequester(first))
            HomeButton(stringResource(R.string.mintv_choose_teams), teams)
            order.forEachIndexed { index, section ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    HomeButton(stringResource(if (section in hidden) R.string.mintv_show_home_section else R.string.mintv_hide_home_section, stringResource(homeSectionLabel(section))), { toggle(section) }, Modifier.weight(1f))
                    if (index > 3) DashboardAction(stringResource(R.string.mintv_v08_move_up, stringResource(homeSectionLabel(section))), {
                        reorder(order.toMutableList().apply { set(index, order[index - 1]); set(index - 1, section) })
                    })
                }
            }
            HomeButton(stringResource(R.string.mintv_v08_default_layout), reset)
            HomeButton(stringResource(R.string.mintv_manage_source), sources)
            HomeButton(stringResource(R.string.mintv_home_settings), systemSettings)
            HomeButton(stringResource(R.string.mintv_close), dismiss)
        }
    }
}
private enum class ExternalShortcut(val label: Int, val instructions: Int, val packages: List<String>) {
    SMART_TUBE(R.string.mintv_smarttube, R.string.mintv_install_smarttube, MinTvExternalApps.smartTubePackages),
    SVT(R.string.mintv_svt, R.string.mintv_install_svt, listOf(MinTvExternalApps.SVT_PLAY)),
}

@Composable
internal fun HomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, logo: String? = null, primary: Boolean = false) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.mpx)),
        contentPadding = PaddingValues(horizontal = 22.mpx, vertical = 15.mpx),
        colors = ButtonDefaults.colors(containerColor = if (primary) Color(0xFF234C45) else MinTvPanel,
            contentColor = Color.White, focusedContainerColor = Color(0xFF2B3B47), focusedContentColor = Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.mpx, HomeTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.mpx).padding(end = 10.mpx)) }
        Text(label, style = stageText(22, 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun homeSectionLabel(section: HomeSection) = when (section) {
    HomeSection.LIVE -> R.string.common_nav_live_tv
    HomeSection.FAVORITES -> R.string.mintv_swedish_channels
    HomeSection.CONTINUE -> R.string.search_continue
    HomeSection.SPORT -> R.string.mintv_my_hockey
    HomeSection.MOVIES -> R.string.common_nav_movies
    HomeSection.SERIES -> R.string.common_nav_series
    HomeSection.TWITCH -> R.string.mintv_twitch_connect
    HomeSection.NOW -> R.string.mintv_v08_now
    HomeSection.APPS -> R.string.mintv_apps_title
}
