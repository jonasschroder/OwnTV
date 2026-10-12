package tv.own.owntv.features.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.launcher.LauncherContinuationItem
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.home.HomeButton
import tv.own.owntv.features.home.HomeContinuationRow
import tv.own.owntv.features.home.MinTvMuted
import tv.own.owntv.features.home.TvText
import tv.own.owntv.features.home.requestSafely
import tv.own.owntv.features.home.updateLabel
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.mpx

/** Visibility-scoped adapter, no always-on collector or network refresh on focus. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
internal fun rememberDiscovery(type: MetadataType, visible: Boolean = true): DiscoveryShelf {
    val repository: DiscoveryRepository = koinInject()
    val settings: SettingsRepository = koinInject()
    val sourceDao: SourceDao = koinInject()
    val db: OwnTVDatabase = koinInject()
    val context by remember { activeProfileSources(settings, sourceDao) }
        .collectAsStateWithLifecycle(ActiveProfileSources(-1, emptyList()))
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = visible && lifecycle == Lifecycle.State.RESUMED
    var shelf by remember(context, type) { mutableStateOf(DiscoveryShelf()) }
    LaunchedEffect(context, type, active) {
        if (!active) return@LaunchedEffect
        val ids = if (type == MetadataType.MOVIE) context.movieSourceIds else context.seriesSourceIds
        if (ids.isEmpty()) return@LaunchedEffect
        try {
            shelf = repository.shelf(context.profileId, ids, type)
            coroutineScope {
                launch {
                    db.invalidationTracker.createFlow(if (type == MetadataType.MOVIE) "movies" else "series",
                        "metadata_match", "metadata_cache", "watch_history", "favorites", "playback_progress", "categories")
                        .debounce(750).collect { shelf = repository.shelf(context.profileId, ids, type).copy(failed = shelf.failed) }
                }
                launch {
                    val success = repository.refresh()
                    shelf = repository.shelf(context.profileId, ids, type).copy(failed = !success)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { shelf = shelf.copy(failed = true) }
    }
    return shelf
}

@Composable
internal fun DiscoveryScreen(type: MetadataType, continuation: List<LauncherContinuationItem>,
    onContinue: (LauncherContinuationItem) -> Unit, onSelect: (DiscoveryCard) -> Unit, onLibrary: () -> Unit,
    restoreFocus: Boolean, onRestored: () -> Unit, onEntryHook: (((() -> Boolean)?) -> Unit)?, modifier: Modifier) {
    val shelf = rememberDiscovery(type)
    val selection: DiscoveryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val profileSettings: SettingsRepository = koinInject()
    val profileId by profileSettings.activeProfileId.collectAsStateWithLifecycle(-1L)
    val list = rememberLazyListState()
    val header = remember { FocusRequester() }
    var selectedKey by rememberSaveable(profileId, type) { mutableStateOf<String?>(null) }
    val targets = remember(profileId, type) { mutableMapOf<String, FocusRequester>() }
    val entry by rememberUpdatedState({ selectedKey?.let { targets[it]?.requestSafely() } == true || header.requestSafely() })
    DisposableEffect(onEntryHook) { onEntryHook?.invoke { entry() }; onDispose { onEntryHook?.invoke(null) } }
    LaunchedEffect(restoreFocus) { if (restoreFocus) { withFrameNanos { }; entry(); onRestored() } }
    DisposableEffect(shelf.rows) {
        val keys = shelf.rows.flatMap { row -> row.cards.map { "${row.kind}:${row.genre}:${it.stableKey}" } }.toSet()
        targets.keys.retainAll(keys)
        onDispose { }
    }
    Column(modifier.fillMaxSize().padding(top = 120.mpx, start = 48.mpx, end = 40.mpx, bottom = 20.mpx), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            TvText(stringResource(if (type == MetadataType.MOVIE) R.string.common_nav_movies else R.string.common_nav_series), Modifier.weight(1f), size = 36, bold = true)
            HomeButton(stringResource(if (type == MetadataType.MOVIE) R.string.mintv_all_movies else R.string.mintv_all_series), onLibrary, Modifier.focusRequester(header).testTag("discovery-all-library"))
        }
        if (shelf.failed || shelf.bounded) TvText(stringResource(R.string.mintv_discovery_fallback), color = MinTvMuted, size = 18, lines = 2)
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(24.mpx), contentPadding = PaddingValues(bottom = 32.mpx)) {
            if (continuation.isNotEmpty()) item(key = "continue") { HomeContinuationRow(continuation.filter { type != MetadataType.TV || it.watchNextType != tv.own.owntv.core.launcher.LauncherWatchNextType.NEXT }, Modifier, onPlay = onContinue) }
            val next = continuation.filter { type == MetadataType.TV && it.watchNextType == tv.own.owntv.core.launcher.LauncherWatchNextType.NEXT }
            if (next.isNotEmpty()) item(key = "next-episode") { HomeContinuationRow(next, Modifier, stringResource(R.string.mintv_next_episode_row), onContinue) }
            if (shelf.rows.isEmpty()) item(key = "empty") { TvText(stringResource(R.string.mintv_discovery_empty), color = MinTvMuted, lines = 3) }
            items(shelf.rows, key = { "${it.kind}:${it.genre}" }) { row ->
                DiscoveryPosterRow(row, type, shelf.rankedAt, { card -> scope.launch { if (selection.available(card, profileId)) onSelect(card) } }, { key -> selectedKey = key }, targets)
            }
            item(key = "tmdb-credit") {
                Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    Image(painterResource(R.drawable.ic_tmdb_logo), stringResource(R.string.mintv_tmdb_source), Modifier.width(100.mpx).height(18.mpx))
                    TvText(stringResource(R.string.mintv_tmdb_disclaimer), color = MinTvMuted, size = 15, lines = 2)
                    TvText(stringResource(R.string.mintv_local_recommendation_explanation), color = MinTvMuted, size = 16, lines = 2)
                }
            }
        }
    }
}

@Composable
internal fun DiscoveryPosterRow(row: DiscoveryRow, type: MetadataType, rankedAt: Long?, onSelect: (DiscoveryCard) -> Unit,
    onFocused: (String) -> Unit = {}, targets: MutableMap<String, FocusRequester>? = null, first: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        TvText(row.genre ?: stringResource(when (row.kind) {
            DiscoveryKind.TOP -> if (type == MetadataType.MOVIE) R.string.mintv_top_available_movies else R.string.mintv_top_available_series
            DiscoveryKind.TRENDING -> R.string.mintv_trending_available
            DiscoveryKind.RECENT -> R.string.mintv_recently_added_library
            DiscoveryKind.RATED -> R.string.mintv_high_rated_provider
            DiscoveryKind.RECOMMENDED -> R.string.mintv_recommended_genres
            DiscoveryKind.GENRE -> R.string.mintv_genre_collection
        }), size = 26, bold = true)
        if (row.kind in listOf(DiscoveryKind.TOP, DiscoveryKind.TRENDING) && rankedAt != null)
            TvText(stringResource(R.string.mintv_tmdb_rank_date, updateLabel(rankedAt, System.currentTimeMillis())), color = MinTvMuted, size = 17)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(8.mpx)) {
            itemsIndexed(row.cards, key = { _, card -> card.stableKey }) { index, card ->
                val key = "${row.kind}:${row.genre}:${card.stableKey}"
                val focus = remember(targets, key) { targets?.getOrPut(key) { FocusRequester() } }
                StagePoster(title = card.name, rating = card.rating?.takeIf { it > 0 }?.let { "%.1f".format(it) },
                    line = listOfNotNull(card.year?.toString(), card.rating?.takeIf { it > 0 }?.let { stringResource(R.string.mintv_provider_rating) }).joinToString(stringResource(R.string.mintv_team_separator)),
                    width = 176.mpx, height = 264.mpx, onClick = { onSelect(card) },
                    modifier = Modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                        .then(if (index == 0) first else Modifier).onFocusChanged { if (it.isFocused) onFocused(key) }) {
                    AsyncImage(card.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
