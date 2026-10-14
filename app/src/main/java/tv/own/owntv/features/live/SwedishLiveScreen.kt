package tv.own.owntv.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.theme.mpx

@OptIn(kotlinx.coroutines.FlowPreview::class)
/** Compact logical rows, bounded stored guides, and the SAME selected-channel preview/controller. */
@Composable
internal fun SwedishLiveScreen(vm: LiveViewModel, onLibrary: () -> Unit, onFullscreen: () -> Unit,
    onOpenGuide: () -> Unit, onChildFocused: () -> Unit, previewEnabled: Boolean, restoreFocus: Boolean, onRestored: () -> Unit, modifier: Modifier) {
    val store: SwedishChannelStore = koinInject()
    val db: OwnTVDatabase = koinInject()
    val context by vm.homeLibraryContext.collectAsStateWithLifecycle()
    val profile = context.first
    val scope = rememberCoroutineScope()
    var choices by remember(profile) { mutableStateOf(SwedishChoices()) }
    var library by remember(profile) { mutableStateOf(SwedishLibrary(emptyList())) }
    var collection by rememberSaveable(profile) { mutableStateOf(SwedishCollection.GENERAL) }
    var focusId by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var resolved by remember(profile) { mutableStateOf<Pair<SwedishRowKey, ChannelEntity>?>(null) }
    val metadata by vm.swedishRowContext.collectAsStateWithLifecycle()
    val currentMetadata by rememberUpdatedState(metadata)
    var revision by remember(context) { mutableIntStateOf(0) }
    val cache = remember(metadata, revision) { SwedishRowCache() }
    var rowData by remember(metadata, revision, collection) { mutableStateOf<Map<SwedishRowKey, SwedishRowInfo>>(emptyMap()) }
    var at by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expectedGuideRevision by remember(metadata) { mutableIntStateOf(0) }
    var edit by rememberSaveable(profile) { mutableStateOf(false) }
    var variants by rememberSaveable(profile) { mutableStateOf(false) }
    var failed by remember(profile) { mutableStateOf(false) }
    val list = rememberLazyListState()
    val entry = remember { FocusRequester() }
    val targets = remember(profile) { mutableMapOf<String, FocusRequester>() }
    var loaded by remember(profile) { mutableStateOf(false) }
    val guide by vm.swedishGuide.collectAsStateWithLifecycle()
    val blocked by vm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val previewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val external by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = lifecycle == Lifecycle.State.RESUMED
    LaunchedEffect(context) { choices = store.choices(profile) }
    val access = metadata?.custom?.let { Triple(it.hiddenCategories, it.hiddenItems, it.customCategories) }
    LaunchedEffect(context, choices.order, choices.hidden, access) {
        failed = false
        try {
            db.invalidationTracker.createFlow("channels", "categories", "profiles").collect {
                library = store.library(profile, context.second, choices); loaded = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; loaded = true }
    }
    val groups = library.groups.filter { g ->
        (g.variants.first().name.collection == collection || collection == SwedishCollection.CHILDREN && g.label == "SVT Barn" || collection == SwedishCollection.NEWS && g.label == "SVT24") && g.preferred(choices.preferred[g.identity], choices.showUhd) != null
    }
    val focused = library.groups.firstOrNull { it.identity == focusId }
    val preferred = focused?.preferred(choices.preferred[focused.identity], choices.showUhd)
    val selected = resolved?.takeIf { it.first == preferred?.rowKey() }?.second
    val editItems = if (!edit) 0 else 3 + if (focused == null) 0 else 5
    suspend fun restoreChannel() {
        restoreSwedishChannel(list, groups.map { it.identity }, focusId, editItems, targets, entry)
    }
    LaunchedEffect(collection, profile) { list.scrollToItem(0) }
    DisposableEffect(library.groups) {
        targets.keys.retainAll(library.groups.mapTo(HashSet()) { it.identity })
        onDispose { }
    }
    LaunchedEffect(active, context) {
        if (active) db.invalidationTracker.createFlow("epg_programmes", "epg_channels", "channels", "categories", "profiles", "sources")
            .debounce(350).collect { revision++ }
    }
    LaunchedEffect(active, cache, collection) {
        if (!active) return@LaunchedEffect
        while (true) {
            at = System.currentTimeMillis()
            val boundary = rowData.values.flatMap { listOfNotNull(it.now?.stopMs, it.next?.startMs) }.filter { it > at }.minOrNull()
            delay(minOf(5_000L, boundary?.minus(at) ?: 5_000L).coerceAtLeast(20L))
        }
    }
    LaunchedEffect(active, cache, groups, choices.preferred, choices.showUhd, at) {
        val expected = metadata?.takeIf { it.profileId == profile && it.sourceIds == context.second } ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }.distinctUntilChanged().collectLatest { visible ->
            delay(100) // Coalesce remote auto-repeat; a new viewport cancels pending DB work.
            val window = swedishEpgWindow(groups, visible, choices).map { it.rowKey() }
            val found = cache.load(window, System.currentTimeMillis()) { missing, time ->
                try { vm.swedishStoredRows(missing, expected, time, db) }
                catch (timeout: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); missing.associateWith { SwedishRowInfo(null, null, null) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { missing.associateWith { SwedishRowInfo(null, null, null) } }
            }
            currentCoroutineContext().ensureActive()
            rowData = found
        }
    }
    LaunchedEffect(selected?.id, metadata, revision, rowData[selected?.let { SwedishRowKey(it.id, it.sourceId, tv.own.owntv.core.customize.CustomizeKeys.channel(it)) }]?.let { it.now?.id to it.next?.id }) {
        expectedGuideRevision = vm.refreshSwedishGuide(selected, profile)
    }
    LaunchedEffect(restoreFocus, loaded, groups) {
        if (restoreFocus && loaded) {
            restoreChannel()
            onRestored()
        }
    }
    LaunchedEffect(focusId, context, preferred, access) {
        val variant = preferred ?: return@LaunchedEffect
        val channel = vm.homeChannel(variant.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == variant.stableKey && it.sourceId == variant.sourceId }
        resolved = channel?.let { variant.rowKey() to it }
        channel?.let(vm::onChannelFocused)
    }
    LaunchedEffect(selected?.id, active, previewEnabled, previewSetting) {
        if (active && previewEnabled && previewSetting) selected?.let { delay(700); vm.playPreview(it) }
    }
    fun update(value: SwedishChoices) { scope.launch { store.save(profile, value); choices = value } }
    fun play(variant: SwedishVariant) { scope.launch {
        val channel = vm.homeChannel(variant.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == variant.stableKey && it.sourceId == variant.sourceId } ?: return@launch
        // Fresh entity retains all provider authorization, DRM, catch-up and EPG context.
        val zap = groups.mapNotNull { it.preferred(choices.preferred[it.identity], choices.showUhd) }
            .take(200).mapNotNull { row -> vm.homeChannel(row.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == row.stableKey && it.sourceId == row.sourceId } }
        val expected = metadata ?: return@launch
        if (currentMetadata != expected || expected.profileId != profile || !vm.swedishContextCurrent(expected)) return@launch
        vm.watchFullscreen(channel, zap)
        if (!external || channel.drmConfig != null) onFullscreen()
    } }
    SwedishLiveFrame(modifier.onFocusChanged { if (it.hasFocus) onChildFocused() }, header = {
            TvText(stringResource(R.string.mintv_swedish_channels), Modifier.weight(1f), size = 34, bold = true)
            SwedishLiveAction(stringResource(R.string.mintv_full_library), onLibrary, Modifier.focusRequester(entry))
            SwedishLiveAction(stringResource(R.string.mintv_collection_settings), { edit = !edit })
            SwedishLiveAction(stringResource(R.string.common_nav_guide), onOpenGuide)
        }, categories = {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            items(SwedishCollection.entries, key = { it.name }) { category ->
                SwedishLiveAction(stringResource(collectionLabel(category)), { collection = category; variants = false }, selected = collection == category)
            }
        }
        }, notice = {
        if (library.bounded || failed) TvText(stringResource(R.string.mintv_curated_partial), color = MinTvMuted)
        }, channels = {
            LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                if (edit) {
                    item { HomeButton(stringResource(if (choices.showUhd) R.string.mintv_hide_uhd else R.string.mintv_show_uhd), { update(choices.copy(showUhd = !choices.showUhd)) }) }
                    item { HomeButton(stringResource(R.string.mintv_show_variants), { update(choices.copy(showVariants = !choices.showVariants)) }) }
                    item { HomeButton(stringResource(R.string.mintv_restore_collection), { update(SwedishChoices()); focusId = null; variants = false }) }
                    focused?.let { group ->
                        item { HomeButton(stringResource(R.string.mintv_choose_variant), { variants = !variants }) }
                        item { HomeButton(stringResource(R.string.mintv_pin_channel), { selected?.let(vm::toggleFavorite) }) }
                        item { HomeButton(stringResource(R.string.mintv_hide_channel), { update(choices.copy(hidden = choices.hidden + group.identity)); focusId = null }) }
                        for (offset in listOf(-1, 1)) item {
                            HomeButton(stringResource(if (offset < 0) R.string.mintv_move_up else R.string.mintv_move_down), {
                                val order = library.groups.map { it.identity }.toMutableList()
                                val i = order.indexOf(group.identity); val target = i + offset
                                if (i >= 0 && target in order.indices) { order.add(target, order.removeAt(i)); update(choices.copy(order = order)) }
                            })
                        }
                    }
                }
                if (groups.isEmpty()) item { TvText(stringResource(R.string.mintv_curated_empty), color = MinTvMuted, lines = 3) }
                items(groups, key = { it.identity }) { group ->
                    val preferred = group.preferred(choices.preferred[group.identity], choices.showUhd)!!
                    val rowFocus = remember(profile, group.identity) { targets.getOrPut(group.identity) { FocusRequester() } }
                    SwedishChannelRow(group.label, preferred.name.quality, rowData[preferred.rowKey()], at,
                        selected = focusId == group.identity, onPlay = { play(preferred) },
                        modifier = Modifier.focusRequester(rowFocus).onFocusChanged { if (it.isFocused) focusId = group.identity })
                    if (choices.showVariants || variants && focusId == group.identity) {
                        group.variants.forEach { variant ->
                            HomeButton(listOfNotNull(variant.rawName, providerNames[variant.sourceId]).joinToString(stringResource(R.string.mintv_team_separator)), {
                                update(choices.copy(preferred = choices.preferred + (group.identity to variant.stableKey)))
                                variants = false
                                scope.launch { restoreChannel() }
                            }, Modifier.padding(start = 24.mpx))
                        }
                    }
                }
            }
        }, preview = {
                SwedishLiveAction(stringResource(R.string.mintv_choose_variant), { variants = !variants })
                val epg = guide?.takeIf { it.revision >= expectedGuideRevision }?.forChannel(selected, metadata, at)
                LiveStagePane(selected, focused?.label, epg, vm.previewEngine,
                    showVideo = active && previewEnabled && previewSetting && selected != null,
                    singleSessionBlocked = blocked, scheduleFocus = remember { FocusRequester() },
                    onOpenProgramme = { onOpenGuide() }, onBackToList = { scope.launch { restoreChannel() } }, modifier = Modifier.fillMaxWidth())
        })
}

private fun collectionLabel(collection: SwedishCollection) = when (collection) {
    SwedishCollection.GENERAL -> R.string.mintv_swedish_channels
    SwedishCollection.SPORT -> R.string.mintv_hockey_sport
    SwedishCollection.NEWS -> R.string.mintv_news
    SwedishCollection.CHILDREN -> R.string.mintv_children
    SwedishCollection.OTHER -> R.string.mintv_other_swedish
}
