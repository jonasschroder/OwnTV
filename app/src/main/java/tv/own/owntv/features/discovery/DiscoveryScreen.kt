package tv.own.owntv.features.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.focusRestorer
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.model.MediaType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import tv.own.owntv.features.home.MinTvNavy
import tv.own.owntv.features.home.TvText
import tv.own.owntv.features.home.requestSafely
import tv.own.owntv.features.home.updateLabel
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.mpx

/** Visibility-scoped adapter, no always-on collector or network refresh on focus. */
@Composable
internal fun rememberDiscovery(type: MetadataType, visible: Boolean = true, enabled: Boolean = true, expectedProfile: Long? = null): DiscoveryShelf {
    val repository: DiscoveryRepository = koinInject()
    val settings: SettingsRepository = koinInject()
    val sourceDao: SourceDao = koinInject()
    val db: OwnTVDatabase = koinInject()
    val customize: CustomizationStore = koinInject()
    val context by remember { activeProfileSources(settings, sourceDao) }
        .collectAsStateWithLifecycle(ActiveProfileSources(-1, emptyList()))
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = enabled && lifecycle == Lifecycle.State.RESUMED
    val metadataMode by settings.metadataMode.collectAsStateWithLifecycle(tv.own.owntv.core.metadata.MetadataMode.PROVIDER_PLUS_TMDB)
    val custom by remember(context.profileId, type) { customize.observe(context.profileId,
        if (type == MetadataType.MOVIE) MediaType.MOVIE else MediaType.SERIES) }
        .collectAsStateWithLifecycle(SectionCustomizations())
    var shelf by remember(context, type, custom, metadataMode) { mutableStateOf(DiscoveryShelf()) }
    val refreshes = remember(context, type, metadataMode) { MutableStateFlow(false) }
    LaunchedEffect(context, type, active, metadataMode, custom) {
        if (!active) return@LaunchedEffect
        val ids = if (type == MetadataType.MOVIE) context.movieSourceIds else context.seriesSourceIds
        if (ids.isEmpty()) return@LaunchedEffect
        try {
            discoveryShelves(db.invalidationTracker.createFlow(if (type == MetadataType.MOVIE) "movies" else "series",
                "metadata_match", "metadata_cache", "watch_history", "favorites", "playback_progress", "categories").map { Unit }, repository.candidateRevision, refreshes) {
                repository.shelf(context.profileId, ids, type)
            }.collect { shelf = it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { shelf = shelf.copy(failed = true) }
    }
    LaunchedEffect(context, type, active, visible, metadataMode) {
        if (!active || !visible) return@LaunchedEffect
        val ids = if (type == MetadataType.MOVIE) context.movieSourceIds else context.seriesSourceIds
        if (ids.isEmpty()) return@LaunchedEffect
        try {
            refreshes.value = !repository.refresh()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { refreshes.value = true }
    }
    return if (expectedProfile != null && context.profileId != expectedProfile) DiscoveryShelf() else shelf
}

@Composable
internal fun DiscoveryScreen(type: MetadataType, continuation: List<LauncherContinuationItem>,
    onContinue: (LauncherContinuationItem) -> Unit, onSelect: (DiscoveryCard) -> Unit, onLibrary: () -> Unit,
    restoreFocus: Boolean, onRestored: () -> Unit, onEntryHook: (((() -> Boolean)?) -> Unit)?, modifier: Modifier,
    onFolder: (ProviderFolder) -> Unit = { onLibrary() }, onPlay: (DiscoveryCard) -> Unit = onSelect) {
    val shelf = rememberDiscovery(type)
    val selection: DiscoveryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val profileSettings: SettingsRepository = koinInject()
    val profileId by profileSettings.activeProfileId.collectAsStateWithLifecycle(-1L)
    val list = rememberLazyListState()
    val header = remember { FocusRequester() }
    var selectedKey by rememberSaveable(profileId, type) { mutableStateOf<String?>(null) }
    val targets = remember(profileId, type) { mutableMapOf<String, FocusRequester>() }
    var variants by remember(profileId, type) { mutableStateOf<Pair<DiscoveryRow, DiscoveryCard>?>(null) }
    var playVariant by remember(profileId, type) { mutableStateOf(false) }
    val heroKey = shelf.hero?.card?.stableKey?.let { "hero:$it" }
    val entry by rememberUpdatedState({ selectedKey?.let { targets[it]?.requestSafely() } == true ||
        heroKey?.let { targets[it]?.requestSafely() } == true || header.requestSafely() })
    DisposableEffect(onEntryHook) { onEntryHook?.invoke { entry() }; onDispose { onEntryHook?.invoke(null) } }
    LaunchedEffect(restoreFocus) { if (restoreFocus) { withFrameNanos { }; entry(); onRestored() } }
    DisposableEffect(shelf.rows, heroKey) {
        val keys = shelf.rows.flatMap { row -> row.cards.map { "${row.key}:${it.stableKey}" } + "${row.key}:all" }.toSet() + listOfNotNull(heroKey, heroKey?.let { "$it:info" })
        targets.keys.retainAll(keys)
        onDispose { }
    }
    Column(modifier.fillMaxSize().padding(top = 120.mpx, start = 48.mpx, end = 40.mpx, bottom = 20.mpx), verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            TvText(stringResource(if (type == MetadataType.MOVIE) R.string.common_nav_movies else R.string.common_nav_series), Modifier.weight(1f), size = 36, bold = true)
            HomeButton(stringResource(if (type == MetadataType.MOVIE) R.string.mintv_all_movies else R.string.mintv_all_series), onLibrary, Modifier.focusRequester(header).testTag("discovery-all-library"))
        }
        if (shelf.failed || shelf.bounded) TvText(stringResource(R.string.mintv_discovery_fallback), color = MinTvMuted, size = 18, lines = 2)
        if (shelf.rows.any { it.folder != null }) TvText(stringResource(R.string.mintv_provider_folders_hint), color = MinTvMuted, size = 18, lines = 2)
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(24.mpx), contentPadding = PaddingValues(bottom = 32.mpx)) {
            shelf.hero?.let { hero -> item(key = "featured") {
                val key = "hero:${hero.card.stableKey}"
                val first = remember(targets, key) { targets.getOrPut(key) { FocusRequester() } }
                val info = remember(targets, key) { targets.getOrPut("$key:info") { FocusRequester() } }
                val resume = DiscoveryHeroSelection.continuation(hero.card, continuation)
                val label = stringResource(if (resume != null) R.string.mintv_featured_continue
                    else if (type == MetadataType.MOVIE) R.string.mintv_featured_play else R.string.mintv_featured_episodes)
                DiscoveryFeaturedHero(hero, label, {
                    scope.launch {
                        if (selection.available(hero.card, profileId)) {
                            if (resume != null) onContinue(resume)
                            else if (hero.card.variants.isNotEmpty() && type == MetadataType.MOVIE) {
                                playVariant = true; variants = DiscoveryRow(DiscoveryKind.RECENT, listOf(hero.card)) to hero.card
                            } else if (type == MetadataType.MOVIE) onPlay(hero.card) else onSelect(hero.card)
                        }
                    }
                }, { scope.launch { if (selection.available(hero.card, profileId)) onSelect(hero.card) } },
                    primaryModifier = Modifier.focusRequester(first).onFocusChanged { if (it.isFocused) selectedKey = key },
                    informationModifier = Modifier.focusRequester(info).onFocusChanged { if (it.isFocused) selectedKey = "$key:info" })
            } }
            if (continuation.isNotEmpty()) item(key = "continue") { HomeContinuationRow(continuation.filter { type != MetadataType.TV || it.watchNextType != tv.own.owntv.core.launcher.LauncherWatchNextType.NEXT }, Modifier, onPlay = onContinue) }
            val next = continuation.filter { type == MetadataType.TV && it.watchNextType == tv.own.owntv.core.launcher.LauncherWatchNextType.NEXT }
            if (next.isNotEmpty()) item(key = "next-episode") { HomeContinuationRow(next, Modifier, stringResource(R.string.mintv_next_episode_row), onContinue) }
            if (shelf.rows.isEmpty()) item(key = "empty") { TvText(stringResource(R.string.mintv_discovery_empty), color = MinTvMuted, lines = 3) }
            items(shelf.rows, key = { it.key }) { row ->
                DiscoveryPosterRow(row, type, shelf.rankedAt, { card ->
                    if (card.variants.isNotEmpty()) { playVariant = false; variants = row to card }
                    else scope.launch { if (selection.available(card, profileId)) onSelect(card) }
                }, { key -> selectedKey = key }, targets, onFolder = { folder ->
                    scope.launch { if (selection.folderAvailable(folder, profileId, type)) onFolder(folder) }
                })
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
    variants?.let { (row, card) -> ProviderVariantPicker(card, onSelect = { variant -> scope.launch {
        if (selection.available(variant, profileId)) { variants = null; if (playVariant) onPlay(variant) else onSelect(variant) }
    } }, onAll = { row.folder?.let { folder -> scope.launch {
        if (selection.folderAvailable(folder, profileId, type)) { variants = null; onFolder(folder) }
    } } }, onDismiss = { variants = null }) }
}

@Composable
internal fun ProviderVariantPicker(card: DiscoveryCard, onSelect: (DiscoveryCard) -> Unit,
    onAll: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(card.stableKey) { withFrameNanos { }; first.requestSafely() }
        Column(Modifier.width(700.mpx).background(MinTvNavy, RoundedCornerShape(14.mpx)).padding(28.mpx),
            verticalArrangement = Arrangement.spacedBy(16.mpx)) {
            TvText(stringResource(R.string.mintv_vod_choose_variant), size = 26, bold = true)
            (listOf(card.copy(variants = emptyList())) + card.variants).forEachIndexed { index, variant ->
                HomeButton(variant.name, { onSelect(variant) }, if (index == 0) Modifier.focusRequester(first) else Modifier)
            }
            HomeButton(stringResource(R.string.mintv_folder_all_entries), onAll)
            HomeButton(stringResource(R.string.mintv_close), onDismiss)
        }
    }
}

@Composable
internal fun DiscoveryPosterRow(row: DiscoveryRow, type: MetadataType, rankedAt: Long?, onSelect: (DiscoveryCard) -> Unit,
    onFocused: (String) -> Unit = {}, targets: MutableMap<String, FocusRequester>? = null, first: Modifier = Modifier,
    onFolder: (ProviderFolder) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        TvText(row.folder?.name ?: row.genre ?: stringResource(when (row.kind) {
            DiscoveryKind.PROVIDER -> R.string.mintv_provider_folder
            DiscoveryKind.TOP -> if (type == MetadataType.MOVIE) R.string.mintv_top_available_movies else R.string.mintv_top_available_series
            DiscoveryKind.TRENDING -> R.string.mintv_trending_available
            DiscoveryKind.RECENT -> R.string.mintv_recently_added_library
            DiscoveryKind.RATED -> R.string.mintv_high_rated_provider
            DiscoveryKind.RECOMMENDED -> R.string.mintv_recommended_genres
            DiscoveryKind.GENRE -> R.string.mintv_genre_collection
        }), size = 26, bold = true)
        row.folder?.let { folder ->
            TvText(stringResource(R.string.mintv_provider_folder_source, folder.sourceName), color = MinTvMuted, size = 17)
        }
        if (row.kind in listOf(DiscoveryKind.TOP, DiscoveryKind.TRENDING) && rankedAt != null)
            TvText(stringResource(R.string.mintv_tmdb_rank_date, updateLabel(rankedAt, System.currentTimeMillis())), color = MinTvMuted, size = 17)
        LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(8.mpx)) {
            itemsIndexed(row.cards, key = { _, card -> card.stableKey }) { index, card ->
                val key = "${row.key}:${card.stableKey}"
                val focus = remember(targets, key) { targets?.getOrPut(key) { FocusRequester() } }
                StagePoster(title = card.name, rating = card.rating?.takeIf { it > 0 }?.let { tv.own.owntv.features.shell.components.vodRating(it) },
                    line = listOfNotNull(card.year?.toString(),
                        if (card.variants.isNotEmpty()) pluralStringResource(R.plurals.mintv_vod_variants, card.variants.size + 1, card.variants.size + 1) else null,
                        card.rating?.takeIf { it > 0 }?.let { stringResource(R.string.mintv_provider_rating) }).joinToString(stringResource(R.string.mintv_team_separator)),
                    width = 176.mpx, height = 264.mpx, onClick = { onSelect(card) },
                    modifier = Modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                        .then(if (index == 0) first else Modifier).onFocusChanged { if (it.isFocused) onFocused(key) }) {
                    VodPosterArt(card.poster, if (type == MetadataType.MOVIE) OwnTVIcon.MOVIES else OwnTVIcon.SERIES)
                }
            }
            row.folder?.let { folder -> item(key = "all") {
                val key = "${row.key}:all"
                val focus = remember(targets, key) { targets?.getOrPut(key) { FocusRequester() } }
                HomeButton(stringResource(R.string.mintv_folder_all_entries), { onFolder(folder) },
                    Modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocused(key) })
            } }
        }
    }
}
