package tv.own.owntv.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.features.series.SeriesScreen

internal enum class NativeVodMode { LANDING, LIBRARY, MOVIE_DETAILS, SERIES_DETAILS }

/** Presentation router only. Existing VMs/details, access checks and player remain authoritative. */
@Composable
internal fun NativeVodHost(profile: Long?, section: MainSection, active: Boolean, movieVm: MovieViewModel,
    seriesVm: SeriesViewModel, onSection: (MainSection) -> Unit, onFullscreen: (MainSection) -> Unit,
    onChildFocused: () -> Unit, onHeader: () -> Unit, onEntry: ((() -> Boolean)?) -> Unit,
    modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val db: OwnTVDatabase = koinInject()
    val custom: CustomizationStore = koinInject()
    val repository: DiscoveryRepository = koinInject()
    val access by remember { activeProfileSources(settings, db.sourceDao()) }.collectAsStateWithLifecycle(initialValue = null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var mode by rememberSaveable(profile) { mutableStateOf(NativeVodMode.LANDING) }
    var returnAfterPlayer by rememberSaveable(profile) { mutableStateOf(false) }
    val first = remember(profile) { FocusRequester() }
    var landingEntry by remember(profile) { mutableStateOf<(() -> Boolean)?>(null) }
    var childEntry by remember(profile) { mutableStateOf<(() -> Boolean)?>(null) }
    var restore by remember(profile) { mutableStateOf(false) }
    val currentMode by rememberUpdatedState(mode)
    val enterLanding by rememberUpdatedState(landingEntry)
    val enterChild by rememberUpdatedState(childEntry)
    val report by rememberUpdatedState(onEntry)
    DisposableEffect(profile) {
        report { (if (currentMode == NativeVodMode.LANDING) enterLanding else enterChild)?.invoke() ?: false }
        onDispose { report(null) }
    }
    val foreground = active && lifecycle == Lifecycle.State.RESUMED && profile != null && access?.profileId == profile
    val movies = rememberDiscovery(MetadataType.MOVIE, mode == NativeVodMode.LANDING, foreground && mode == NativeVodMode.LANDING, profile ?: -1L)
    val series = rememberDiscovery(MetadataType.TV, mode == NativeVodMode.LANDING, foreground && mode == NativeVodMode.LANDING, profile ?: -1L)
    var resumes by remember(profile, access) { mutableStateOf<List<SmartResume>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var variants by remember(profile) { mutableStateOf<DiscoveryCard?>(null) }
    val requestedMovie by movieVm.detailsRequest.collectAsStateWithLifecycle()
    val openedSeries by seriesVm.openedSeries.collectAsStateWithLifecycle()
    LaunchedEffect(requestedMovie, openedSeries?.id) {
        if (requestedMovie?.let { it.second == profile } == true) mode = NativeVodMode.MOVIE_DETAILS
        else if (openedSeries != null && mode == NativeVodMode.LANDING) mode = NativeVodMode.SERIES_DETAILS
    }
    LaunchedEffect(active) {
        if (!active && mode != NativeVodMode.LANDING && mode != NativeVodMode.LIBRARY) returnAfterPlayer = true
        if (active && returnAfterPlayer) {
            returnAfterPlayer = false; seriesVm.closeSeries(); mode = NativeVodMode.LANDING; restore = true
        }
    }
    LaunchedEffect(profile, access, foreground, mode) {
        if (!foreground || profile == null || access?.profileId != profile) return@LaunchedEffect
        try { resumes = readSmartResumes(db, settings, custom, profile).take(3) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { resumes = emptyList() }
    }
    LaunchedEffect(restore, mode, movies, series) {
        if (restore && mode == NativeVodMode.LANDING) { repeat(2) { withFrameNanos { } }; if (enterLanding?.invoke() == true) restore = false }
    }
    fun landing() { mode = NativeVodMode.LANDING; variants = null; restore = true }
    fun library(type: MetadataType) { mode = NativeVodMode.LIBRARY; variants = null; onSection(if (type == MetadataType.MOVIE) MainSection.MOVIES else MainSection.SERIES) }
    fun title(card: DiscoveryCard) {
        if (card.variants.isNotEmpty()) { variants = card; return }
        val owner = profile ?: return
        scope.launch {
            if (!repository.available(card, owner) || settings.activeProfileId.first() != owner) return@launch
            if (card.type == MetadataType.MOVIE) {
                movieVm.requestDetails(card); mode = NativeVodMode.MOVIE_DETAILS; onSection(MainSection.MOVIES)
            } else {
                val show = db.seriesDao().getSeriesById(card.id) ?: return@launch
                if (CustomizeKeys.series(show) != card.stableKey || !repository.available(card, owner) || settings.activeProfileId.first() != owner) return@launch
                seriesVm.openSeries(show); mode = NativeVodMode.SERIES_DETAILS; onSection(MainSection.SERIES)
            }
        }
    }
    fun resume(item: SmartResume) {
        val owner = profile ?: return
        scope.launch {
            val actual = readSmartResumes(db, settings, custom, owner, exact = item).firstOrNull { it.key == item.key } ?: return@launch
            if (settings.activeProfileId.first() != owner) return@launch
            if (actual.kind == SmartResumeKind.MOVIE) {
                if (movieVm.playByIdAsync(actual.itemId, actual.position) && !movieVm.externalPlayerOn.value) onFullscreen(MainSection.MOVIES)
            } else if (seriesVm.playFromHomeAsync(actual.seriesId ?: return@launch, actual.itemId, actual.position) && !seriesVm.externalPlayerOn.value) onFullscreen(MainSection.SERIES)
        }
    }
    BackHandler(mode != NativeVodMode.LANDING) { landing() }
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        NativeVodLanding(NativeVodSnapshot(profile, nativeVodPreview(movies), nativeVodPreview(series), resumes), first,
            { landingEntry = it }, onHeader, ::library, ::title, ::resume)
        if (mode != NativeVodMode.LANDING) Box(Modifier.fillMaxSize().background(MinTvNavy)) {
            if (section == MainSection.MOVIES) MoviesScreen({ onFullscreen(MainSection.MOVIES) }, onChildFocused,
                onEntryHook = { childEntry = it }, onDetailsClosed = { if (mode == NativeVodMode.MOVIE_DETAILS) landing() })
            else SeriesScreen({ onFullscreen(MainSection.SERIES) }, onChildFocused,
                onEntryHook = { childEntry = it }, onSeriesClosed = { if (mode == NativeVodMode.SERIES_DETAILS) landing() })
        }
        variants?.let { card -> ProviderVariantPicker(card,
            onSelect = { variants = null; title(it.copy(variants = emptyList())) }, onAll = { library(card.type) },
            onDismiss = { variants = null; restore = true }) }
    }
}
