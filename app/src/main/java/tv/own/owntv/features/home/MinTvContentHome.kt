package tv.own.owntv.features.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.features.discovery.*
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.home.MinTvExternalApps
import tv.own.owntv.home.MinTvIntents
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

private val HomeNavy = MinTvNavy
private val HomeTeal = MinTvTeal

/** Content-only destination in OwnTVShell: no Activity, player or provider session of its own. */
@Composable
internal fun MinTvContentHome(
    vm: HomeViewModel,
    liveVm: LiveViewModel,
    activeProfileId: Long?,
    previewEnabled: Boolean,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onPauseOrDispose: () -> Unit,
    onLiveTv: () -> Unit,
    onGuide: () -> Unit,
    onPlayMovie: (Long, Long) -> Unit,
    onOpenMovie: (DiscoveryCard) -> Unit,
    onPlayEpisode: (Long, Long, Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onSources: () -> Unit,
    onManageSources: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    firstRowFocusRequester: FocusRequester,
    onEntryHook: ((() -> Boolean)?) -> Unit,
    contentStart: Dp,
    reservedTop: Dp = 144.mpx,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val library by liveVm.homeLibraryState.collectAsStateWithLifecycle()
    val settings = koinInject<SettingsRepository>()
    val previewsOn by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val favorites = state.favoriteLive.takeIf { state.profileId == activeProfileId && library.profileId == activeProfileId }.orEmpty()
        .filter { it.sourceId in library.sourceIds && (library.visibleFavoriteIds?.contains(it.id) != false) }
    var selectedId by rememberSaveable(activeProfileId) { mutableStateOf<Long?>(null) }
    val selected = favorites.firstOrNull { it.id == selectedId } ?: favorites.firstOrNull()
    val controller = liveVm.homePreview
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    var matchcenterEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var matchcenterOpen by remember { mutableStateOf(false) }
    var preferencesOpen by remember { mutableStateOf(false) }
    val active = previewEnabled && !matchcenterOpen && !preferencesOpen && previewsOn && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val liveFocus = remember { FocusRequester() }
    var missingApp by remember { mutableStateOf<ExternalShortcut?>(null) }
    var launchFailed by remember { mutableStateOf(false) }
    var guide by remember(selected?.id, activeProfileId) { mutableStateOf<EpgNowNext?>(null) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val previewState by liveVm.previewEngine.state.collectAsStateWithLifecycle()
    val blocked by liveVm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val navigation = rememberHomeDpadNavigation(firstRowFocusRequester, sectionCount = 8, profileKey = activeProfileId)
    val continuations = remember(state.continueMovies, state.continueSeries, state.profileId, activeProfileId) {
        homeContinuations(state, activeProfileId)
    }
    val discoverySelection: DiscoveryRepository = koinInject()
    val sectionStore: HomeSections = koinInject()
    val sportsPreferences: SportsPreferences = koinInject()
    val sportsRevision by sportsPreferences.changes.collectAsStateWithLifecycle()
    var hidden by remember(activeProfileId) { mutableStateOf<Set<HomeSection>>(emptySet()) }
    var sportsSelection by remember(activeProfileId) { mutableStateOf(SportPreferences()) }
    var chooseTeams by remember { mutableStateOf(false) }
    LaunchedEffect(activeProfileId) { activeProfileId?.let { hidden = sectionStore.hidden(it) } }
    LaunchedEffect(activeProfileId, sportsRevision) { activeProfileId?.let { sportsSelection = sportsPreferences.read(it) } }
    val discoveryVisible by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.any { it.key in listOf("mintv-resume", "mintv-shl", "mintv-movies", "mintv-series", "mintv-twitch", "mintv-apps") }
    } }
    val movieDiscovery = rememberDiscovery(MetadataType.MOVIE, visible = discoveryVisible, enabled = HomeSection.MOVIES !in hidden && previewEnabled && !matchcenterOpen && !preferencesOpen)
    val seriesDiscovery = rememberDiscovery(MetadataType.TV, visible = discoveryVisible, enabled = HomeSection.SERIES !in hidden && previewEnabled && !matchcenterOpen && !preferencesOpen)
    val movieRow = movieDiscovery.rows.firstOrNull { it.kind == DiscoveryKind.TOP }
    val seriesRow = seriesDiscovery.rows.firstOrNull { it.kind == DiscoveryKind.TOP }
    val previewAllowed = active && HomeSection.LIVE !in hidden
    val showFavorites = HomeSection.FAVORITES !in hidden && favorites.isNotEmpty()
    val hasResume = HomeSection.CONTINUE !in hidden && continuations.isNotEmpty()
    val showSport = HomeSection.SPORT !in hidden && sportsSelection.teamIds.isNotEmpty() && sportsSelection.prominent
    val showMovies = HomeSection.MOVIES !in hidden && movieRow != null
    val showSeries = HomeSection.SERIES !in hidden && seriesRow != null
    val showTwitch = HomeSection.TWITCH !in hidden
    val showApps = HomeSection.APPS !in hidden
    val sectionItems = homeSectionIndices(listOf(true, showFavorites, hasResume, showSport, showMovies, showSeries, showTwitch, showApps))
    LaunchedEffect(sectionItems) {
        if (sectionItems.getOrNull(navigation.selected) == -1) {
            navigation.selected = 0; withFrameNanos { }; navigation.focus[0].requestSafely()
        }
    }
    LaunchedEffect(library) {
        if (library.profileId == activeProfileId && library.status != HomeLibraryStatus.LOADING) vm.refresh(activeProfileId)
    }
    val emptyFocus = firstRowFocusRequester
    var restoreFavoriteAfterLoad by remember(activeProfileId) { mutableStateOf(false) }
    val favoriteListState = rememberLazyListState()
    val hockeyVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "mintv-shl" } } }
    val twitchVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "mintv-twitch" } } }
    var favoriteRowFocused by remember { mutableStateOf(false) }
    var heroFocused by remember { mutableStateOf(false) }
    val favoriteCardsVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "mintv-favorites" } } }
    val visibleFavorites by remember(favorites) { derivedStateOf {
        favoriteListState.layoutInfo.visibleItemsInfo.mapNotNull { row -> favorites.firstOrNull { it.id == row.key } }.take(6)
    } }
    var favoriteProgrammes by remember(activeProfileId) {
        mutableStateOf<Map<Long, tv.own.owntv.core.parser.XtEpgEntry>>(emptyMap())
    }
    val cardsActive = previewEnabled && !matchcenterOpen && !preferencesOpen && favoriteCardsVisible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(activeProfileId, cardsActive, visibleFavorites, library.sourceIds) {
        if (!cardsActive || visibleFavorites.isEmpty()) return@LaunchedEffect
        while (true) {
            // Existing one-batch local EPG reader. No short-EPG/provider network request per card.
            favoriteProgrammes = try { withTimeoutOrNull(5_000) { liveVm.nowPlayingFor(visibleFavorites) }.orEmpty() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyMap() }
            clock = System.currentTimeMillis()
            delay(30_000)
        }
    }
    var remoteNavigationSeen by remember(activeProfileId) { mutableStateOf(false) }
    LaunchedEffect(favorites.isNotEmpty()) {
        if (favorites.isNotEmpty() && restoreFavoriteAfterLoad) {
            if (navigation.selected == 1 && previewEnabled && !matchcenterOpen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
                // Earlier navigation on the loading card must not arm a newly inserted default.
                remoteNavigationSeen = false
                controller.setActive(false)
                controller.setActive(previewAllowed)
                withFrameNanos { }; withFrameNanos { }; navigation.focus[1].requestSafely()
            }
            restoreFavoriteAfterLoad = false
        }
    }
    val pauseOrDispose by rememberUpdatedState(onPauseOrDispose)

    // There is no movie/trending hero decoder on this destination.
    DisposableEffect(vm, controller) {
        vm.setPreviewEnabled(false)
        vm.stopPreview()
        onDispose {
            pauseOrDispose()
            controller.setActive(false)
        }
    }
    LaunchedEffect(previewAllowed, activeProfileId) {
        remoteNavigationSeen = false
        controller.setActive(false)
        controller.setActive(previewAllowed)
    }
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            // Synchronous, before MainActivity snapshots engines in onStop.
            if (event == Lifecycle.Event.ON_PAUSE) {
                pauseOrDispose()
                controller.setActive(false)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selected?.id, activeProfileId, favoriteRowFocused, heroFocused) {
        controller.focus(selected.takeIf { favoriteRowFocused || heroFocused })
    }
    LaunchedEffect(selected?.id, activeProfileId, lifecycle, matchcenterOpen, preferencesOpen, hidden) {
        guide = null // never label a new channel with the previous one's programme
        if (matchcenterOpen || preferencesOpen || HomeSection.LIVE in hidden) return@LaunchedEffect // hidden Home must not poll provider EPG
        val channel = selected ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                guide = try {
                    liveVm.homeNowNext(channel)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                clock = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }
    DisposableEffect(firstRowFocusRequester, favorites.isEmpty()) {
        onEntryHook {
            if (matchcenterOpen) matchcenterEntry?.invoke() == true
            else if (sectionItems.getOrNull(navigation.selected) == -1) liveFocus.requestSafely() else navigation.focus[navigation.selected].requestSafely()
        }
        onDispose { onEntryHook(null) }
    }
    LaunchedEffect(restoreFocus, previewEnabled, favorites) {
        if (restoreFocus && previewEnabled) {
            withFrameNanos { }; withFrameNanos { }
            if (matchcenterOpen) matchcenterEntry?.invoke()
            else if (sectionItems.getOrNull(navigation.selected) == -1) liveFocus.requestSafely()
            else navigation.focus[navigation.selected].requestSafely()
            onRestored()
        }
    }

    fun openExternal(intent: Intent?) {
        pauseOrDispose()
        controller.setActive(false) // stop/cancel BEFORE handing control to another app
        vm.stopPreview()
        if (intent == null) { launchFailed = true; return }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            launchFailed = true
        } catch (_: SecurityException) {
            launchFailed = true
        }
    }

    MinSportCompanion(
        visible = hockeyVisible,
        active = sportsActive(lifecycleState.isAtLeast(Lifecycle.State.RESUMED), previewEnabled && !preferencesOpen),
        liveVm = liveVm, profileId = activeProfileId, favorites = favorites, onPlay = onPlayChannel,
        contentStart = contentStart, onSources = { pauseOrDispose(); controller.setActive(false); onSources() },
        reservedTop = reservedTop,
        onMatchcenterEntry = { matchcenterEntry = it },
        homeCardModifier = navigation.section(3),
    ) { shlCard, screen ->
        LaunchedEffect(screen) {
            matchcenterOpen = screen
            if (screen) { favoriteRowFocused = false; heroFocused = false; pauseOrDispose(); controller.setActive(false) }
        }
        if (!screen) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = contentStart, end = 64.mpx, top = 16.mpx, bottom = 48.mpx),
                verticalArrangement = Arrangement.spacedBy(20.mpx),
                modifier = modifier.homeContentViewport(reservedTop).background(HomeNavy).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                        // Re-enable after a failed external launch, but never on automatic focus restoration.
                        remoteNavigationSeen = true
                        controller.setActive(previewAllowed)
                        if (favoriteRowFocused || heroFocused) controller.onRemoteNavigation()
                    }
                    false
                }.then(homeDpadModifier(navigation, listState, sectionItems)).onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup(),
            ) {
                item(key = "mintv-live-hero") {
                    if (HomeSection.LIVE in hidden) Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                        HomeButton(stringResource(R.string.common_nav_live_tv), onLiveTv, navigation.section(0).focusRequester(liveFocus))
                        HomeButton(stringResource(R.string.common_nav_guide), onGuide)
                        HomeButton(stringResource(R.string.mintv_companion_settings), { preferencesOpen = true })
                    } else {
                    val hasVideo = HomeSection.LIVE !in hidden && active && selected != null && previewState != LivePreviewEngine.State.IDLE && previewState != LivePreviewEngine.State.ERROR
                    val previewMessage = when {
                        selected == null -> R.string.mintv_favorites_empty
                        !previewsOn -> R.string.mintv_preview_disabled
                        blocked -> R.string.content_preview_single_stream
                        previewState == LivePreviewEngine.State.ERROR -> R.string.mintv_preview_unsupported
                        previewState == LivePreviewEngine.State.LOADING && active -> R.string.mintv_preview_loading
                        previewState == LivePreviewEngine.State.IDLE -> R.string.mintv_preview_hint
                        else -> null
                    }
                    HomeLiveHero(selected?.name, selected?.displayLogoUrl, guide?.now, guide?.next?.title, clock,
                        previewMessage?.let { stringResource(it) },
                        onWatch = { selected?.let { onPlayChannel(it, favorites) }
                            ?: run { pauseOrDispose(); controller.setActive(false); onLiveTv() } },
                        onLive = { pauseOrDispose(); controller.setActive(false); onLiveTv() },
                        onGuide = { pauseOrDispose(); controller.setActive(false); onGuide() },
                        onSettings = { preferencesOpen = true },
                        modifier = Modifier.onFocusChanged { heroFocused = it.hasFocus; if (it.hasFocus) navigation.selected = 0 },
                        firstAction = navigation.section(0).focusRequester(liveFocus)) {
                        if (hasVideo) {
                            // Same engine/surface and protected-video path as before. Never a second player.
                            ExoPreviewSurface(liveVm.previewEngine, Modifier.fillMaxSize(), useTextureView = selected.drmConfig == null)
                        } else if (selected?.displayLogoUrl != null) {
                            AsyncImage(model = selected.displayLogoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.size(150.mpx).padding(12.mpx))
                        } else TvText(stringResource(R.string.app_name), size = 42, color = MinTvTeal, bold = true)
                    }
                }
                    }
                if (showFavorites) item(key = "mintv-favorites") {
                    if (favorites.isEmpty()) {
                        val status = if (library.profileId != activeProfileId || state.isLoading || state.profileId != activeProfileId || library.status == HomeLibraryStatus.READY) HomeLibraryStatus.LOADING else library.status
                        TvCard({
                            if (status != HomeLibraryStatus.LOADING) {
                                pauseOrDispose(); controller.setActive(false)
                                when (status) {
                                    HomeLibraryStatus.NO_SOURCE -> onSources()
                                    HomeLibraryStatus.NO_CHANNELS -> onManageSources()
                                    else -> { liveVm.select(tv.own.owntv.core.live.LiveKey.All); liveVm.setSearchQuery(""); onLiveTv() }
                                }
                            }
                        }, Modifier.fillMaxWidth().focusRequester(emptyFocus).onFocusChanged { if (it.hasFocus) { navigation.selected = 1; restoreFavoriteAfterLoad = true } }) {
                            TvText(stringResource(when (status) {
                                HomeLibraryStatus.LOADING -> R.string.mintv_favorites_loading
                                HomeLibraryStatus.NO_SOURCE -> R.string.mintv_welcome
                                HomeLibraryStatus.NO_CHANNELS -> R.string.mintv_no_channels_title
                                else -> R.string.mintv_choose_channels_title
                            }), size = 28, bold = true)
                            if (status != HomeLibraryStatus.LOADING) {
                                TvText(stringResource(when (status) {
                                    HomeLibraryStatus.NO_SOURCE -> R.string.mintv_no_source_help
                                    HomeLibraryStatus.NO_CHANNELS -> R.string.mintv_no_channels_help
                                    else -> R.string.mintv_choose_channels_help
                                }), color = MinTvMuted)
                                TvText(stringResource(when (status) { HomeLibraryStatus.NO_SOURCE -> R.string.mintv_add_source; HomeLibraryStatus.NO_CHANNELS -> R.string.mintv_manage_source; else -> R.string.mintv_choose_channels }), color = MinTvTeal, bold = true)
                            }
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                        TvText(stringResource(R.string.mintv_favorites_title), size = 26, bold = true, lines = 1)
                        LazyRow(state = favoriteListState, horizontalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(6.mpx), modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) navigation.selected = 1
                            favoriteRowFocused = it.hasFocus
                        }.focusGroup()) {
                            itemsIndexed(favorites, key = { _, channel -> channel.id }) { _, channel ->
                                val programme = (guide?.now.takeIf { channel.id == selected?.id } ?: favoriteProgrammes[channel.id])
                                    ?.takeIf { clock in it.startMs until it.stopMs }
                                ChannelCard(channel.name, channel.displayLogoUrl,
                                    programme?.title ?: stringResource(R.string.mintv_epg_unavailable),
                                    { onPlayChannel(channel, favorites) }, Modifier.width(350.mpx).heightIn(min = 144.mpx)
                                        .then(if (channel.id == selected?.id) navigation.section(1) else Modifier)
                                        .onFocusChanged { if (it.hasFocus) {
                                            selectedId = channel.id
                                            controller.focus(channel)
                                            if (remoteNavigationSeen) controller.onRemoteNavigation()
                                            liveVm.onChannelFocused(channel)
                                            onChildFocused()
                                        } }, selected = channel.id == selected?.id,
                                    progress = programme?.let { ((clock - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) })
                            }
                        }
                    }
                }
                if (hasResume) item(key = "mintv-resume") {
                    HomeContinuationRow(continuations, navigation.section(2)) { item ->
                        pauseOrDispose(); controller.setActive(false); vm.stopPreview()
                        when (item.kind) {
                            tv.own.owntv.core.launcher.LauncherContinuationKind.MOVIE -> onPlayMovie(item.sourceItemId, item.positionMs)
                            tv.own.owntv.core.launcher.LauncherContinuationKind.EPISODE -> onPlayEpisode(0L, item.targetItemId, item.positionMs)
                            else -> Unit
                        }
                    }
                }
                if (showSport) item(key = "mintv-shl") { shlCard() }
                if (showMovies) item(key = "mintv-movies") {
                    DiscoveryPosterRow(movieRow!!, MetadataType.MOVIE, movieDiscovery.rankedAt, { card ->
                        scope.launch { activeProfileId?.let { if (discoverySelection.available(card, it)) { pauseOrDispose(); controller.setActive(false); vm.stopPreview(); onOpenMovie(card) } } }
                    }, first = navigation.section(4))
                }
                if (showSeries) item(key = "mintv-series") {
                    DiscoveryPosterRow(seriesRow!!, MetadataType.TV, seriesDiscovery.rankedAt, { card ->
                        scope.launch { activeProfileId?.let { if (discoverySelection.available(card, it)) { pauseOrDispose(); controller.setActive(false); vm.stopPreview(); onOpenSeries(card.id) } } }
                    }, first = navigation.section(5))
                }
                if (showTwitch) item(key = "mintv-twitch") {
                    // Stream-status polling may yield to Live TV work. A pending device grant
                    // must remain active while its dialog is open, independent of preview/focus.
                    TwitchCompanion(visible = twitchVisible && !favoriteRowFocused && previewState != LivePreviewEngine.State.LOADING,
                        modifier = navigation.section(6), active = previewEnabled && !preferencesOpen && lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
                }
                if (showApps) item(key = "mintv-apps") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                        Text(stringResource(R.string.mintv_apps_title), style = stageText(25, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                            ExternalShortcut.entries.forEachIndexed { index, shortcut ->
                                HomeButton(stringResource(shortcut.label), {
                                    pauseOrDispose()
                                    controller.setActive(false)
                                    val intent = MinTvExternalApps.launch(context, shortcut.packages)
                                    if (intent != null) openExternal(intent) else missingApp = shortcut
                                }, Modifier.weight(1f).height(82.mpx).then(if (index == 0) navigation.section(7) else Modifier))
                            }
                        }
                    }
                }
            }
        }
    }
    if (preferencesOpen) {
        Dialog(onDismissRequest = { preferencesOpen = false }) {
            Column(Modifier.width(720.mpx).heightIn(max = 820.mpx).background(HomeNavy, RoundedCornerShape(14.mpx)).padding(32.mpx).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
                TvText(stringResource(R.string.mintv_companion_settings), size = 30, bold = true)
                HomeButton(stringResource(if (previewsOn) R.string.mintv_preview_on else R.string.mintv_preview_off), {
                    controller.setActive(false); scope.launch { settings.setLivePreviewEnabled(!previewsOn) }
                })
                HomeButton(stringResource(R.string.mintv_choose_teams), { chooseTeams = true })
                HomeSection.entries.forEach { section ->
                    HomeButton(stringResource(if (section in hidden) R.string.mintv_show_home_section else R.string.mintv_hide_home_section,
                        stringResource(homeSectionLabel(section))), {
                        activeProfileId?.let { profile -> scope.launch {
                            val next = if (section in hidden) hidden - section else hidden + section
                            sectionStore.save(profile, next); hidden = next
                        } }
                    })
                }
                HomeButton(stringResource(R.string.mintv_add_source), { preferencesOpen = false; pauseOrDispose(); controller.setActive(false); onSources() })
                HomeButton(stringResource(R.string.mintv_home_settings), { preferencesOpen = false; openExternal(MinTvIntents.settings()) })
                HomeButton(stringResource(R.string.mintv_close), { preferencesOpen = false })
            }
        }
    }
    if (chooseTeams && activeProfileId != null) SportsTeamPicker(activeProfileId) { chooseTeams = false }
    if (missingApp != null || launchFailed) {
        Dialog(onDismissRequest = { missingApp = null; launchFailed = false }) {
            Column(Modifier.width(820.mpx).background(HomeNavy, RoundedCornerShape(24.mpx)).padding(36.mpx), verticalArrangement = Arrangement.spacedBy(24.mpx)) {
                Text(stringResource(if (launchFailed) R.string.mintv_external_failed else missingApp!!.instructions), style = stageText(23, 500), color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    missingApp?.takeIf { it != ExternalShortcut.SMART_TUBE }?.let { shortcut ->
                        HomeButton(stringResource(R.string.mintv_open_store), { openExternal(MinTvExternalApps.store(context, shortcut.packages.first())) })
                    }
                    HomeButton(stringResource(R.string.mintv_close), { missingApp = null; launchFailed = false })
                }
            }
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
    HomeSection.SPORT -> R.string.mintv_my_sport
    HomeSection.MOVIES -> R.string.common_nav_movies
    HomeSection.SERIES -> R.string.common_nav_series
    HomeSection.TWITCH -> R.string.mintv_twitch_connect
    HomeSection.APPS -> R.string.mintv_apps_title
}
