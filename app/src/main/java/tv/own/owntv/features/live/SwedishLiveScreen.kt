package tv.own.owntv.features.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.*
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
import tv.own.owntv.features.live.source.*
import tv.own.owntv.ui.theme.mpx

@OptIn(kotlinx.coroutines.FlowPreview::class)
/** Native five-column logical channels, bounded stored guides and the existing fullscreen player. */
@Composable
internal fun SwedishLiveScreen(vm: LiveViewModel, onFullscreen: () -> Unit, onChildFocused: () -> Unit, previewEnabled: Boolean, restoreFocus: Boolean, onRestored: () -> Unit, modifier: Modifier, onHeader: (() -> Unit)? = null,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null, onCategoryEntryHook: (((() -> Boolean)?) -> Unit)? = null, editing: Boolean = false) {
    val store: SwedishChannelStore = koinInject()
    val db: OwnTVDatabase = koinInject()
    val context by vm.homeLibraryContext.collectAsStateWithLifecycle()
    val profile = context.first
    val scope = rememberCoroutineScope()
    val pendingSourcePress = remember(profile) { SourcePressCancellation() }
    var choices by remember(profile) { mutableStateOf(SwedishChoices()) }
    var library by remember(profile) { mutableStateOf(SwedishLibrary(emptyList())) }
    var collection by rememberSaveable(profile) { mutableStateOf(SwedishCollection.GENERAL) }
    var focusId by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var resolved by remember(profile) { mutableStateOf<Pair<SwedishRowKey, ChannelEntity>?>(null) }
    val observedMetadata by vm.swedishRowContext.collectAsStateWithLifecycle()
    // Library context and per-profile customization arrive on separate flows. During handover,
    // clear rows/pane immediately, even when both profiles share the same channel/source IDs.
    val metadata = observedMetadata?.takeIf { it.profileId == profile && it.sourceIds == context.second }
    val currentMetadata by rememberUpdatedState(metadata)
    var revision by remember(context) { mutableIntStateOf(0) }
    val cache = remember(metadata, revision) { SwedishRowCache() }
    var rowData by remember(metadata, revision, collection) { mutableStateOf<Map<SwedishRowKey, SwedishRowInfo>>(emptyMap()) }
    var at by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val edit = editing
    var variants by rememberSaveable(profile) { mutableStateOf(false) }
    var previewSuppressedFor by remember(context) { mutableStateOf<String?>(null) }
    var failed by remember(profile) { mutableStateOf(false) }
    val list = key(profile, collection) { rememberLazyGridState() }
    var preferredColumn by rememberSaveable(profile, collection) { mutableIntStateOf(0) }
    var verticalTarget by remember(profile, collection) { mutableStateOf<String?>(null) }
    var gridHasFocus by remember(profile, collection) { mutableStateOf(false) }
    val entry = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    var categoryEntryRequest by remember(profile, collection) { mutableIntStateOf(0) }
    val editFocus = remember { FocusRequester() }
    var movement by remember(profile, collection) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val targets = remember(profile) { mutableMapOf<String, FocusRequester>() }
    var loaded by remember(profile) { mutableStateOf(false) }
    val external by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = previewEnabled && lifecycle == Lifecycle.State.RESUMED
    LaunchedEffect(context) { store.observeChoices(profile).collect { choices = it } }
    val access = metadata?.custom?.let { Triple(it.hiddenCategories, it.hiddenItems, it.customCategories) }
    LaunchedEffect(context, choices.order, choices.hidden, access) {
        failed = false
        try {
            db.invalidationTracker.createFlow("channels", "categories", "profiles", "sources").collect {
                library = store.library(profile, context.second, choices); loaded = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true; loaded = true }
    }
    val candidates = library.groups.filter { g ->
        (g.variants.first().name.collection == collection || collection == SwedishCollection.OTHER && g.variants.first().name.collection in listOf(SwedishCollection.NEWS, SwedishCollection.CHILDREN)) && g.preferred(choices.preferred[g.identity], choices.showUhd) != null
    }
    val knownGuide = remember(profile, metadata) { linkedMapOf<String, SwedishRowInfo>() }
    var order by remember(profile, collection) { mutableStateOf<List<String>>(emptyList()) }
    val candidateIds = candidates.map { it.identity }
    LaunchedEffect(candidateIds, collection, gridHasFocus) {
        order = if (collection == SwedishCollection.OTHER) stableGuideOrder(candidates, order, gridHasFocus, knownGuide, at) else candidateIds
    }
    val groups = (order.filter { it in candidateIds } + candidateIds.filterNot { it in order }).mapNotNull { id -> candidates.firstOrNull { it.identity == id } }
    val focused = library.groups.firstOrNull { it.identity == focusId }
    val preferred = focused?.preferred(choices.preferred[focused.identity], choices.showUhd)
    val selected = resolved?.takeIf { it.first == preferred?.rowKey() }?.second
    val editItems = if (!edit) 0 else 2 + if (focused == null) 0 else 5
    suspend fun restoreChannel(): Boolean {
        val index = groups.indexOfFirst { it.identity == focusId }.coerceAtLeast(0)
        val target = groups.getOrNull(index)?.identity
        if (target != null) {
            verticalTarget = target
            if (focusId != target) preferredColumn = index % CHANNEL_GRID_COLUMNS
            val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == target }
            if (visible == null || visible.offset.y < list.layoutInfo.viewportStartOffset ||
                visible.offset.y + visible.size.height > list.layoutInfo.viewportEndOffset) list.scrollToItem(index + editItems)
            repeat(2) { withFrameNanos { } }
            if (targets[target]?.requestSafely() == true) return true
        }
        categoryFocus.requestSafely()
        return false
    }
    // Down may arrive before the database reader/grid is ready. Keep the intent profile/category
    // scoped; a subsequent navigation key cancels it instead of stealing focus on data arrival.
    LaunchedEffect(categoryEntryRequest, loaded, groups) {
        if (categoryEntryRequest == 0 || !loaded) return@LaunchedEffect
        if (groups.isNotEmpty()) repeat(30) {
            if (restoreChannel()) { categoryEntryRequest = 0; return@LaunchedEffect }
            withFrameNanos { }
        }
        categoryEntryRequest = 0 // A genuinely empty collection stays safely on its selector.
    }
    DisposableEffect(profile, collection) { onDispose { movement?.cancel() } }
    LaunchedEffect(loaded, groups) {
        if (loaded && gridHasFocus && groups.none { it.identity == focusId }) restoreChannel()
    }
    tv.own.owntv.features.shell.NativeContentEntryHook(onCategoryEntryHook, profile) { categoryFocus.requestSafely() }
    var categoryHasFocus by remember { mutableStateOf(false) }
    val reportEntry by rememberUpdatedState(onEntryHook)
    DisposableEffect(profile, loaded, groups) {
        reportEntry?.invoke { if (!loaded) false else { scope.launch { restoreChannel() }; true } }
        onDispose { reportEntry?.invoke(null) }
    }
    // This destination has no preview surface. Returning from fullscreen must also silence Exo
    // when the user's legacy preview preference is on; mini/audio modes keep previewEnabled=false.
    LaunchedEffect(previewEnabled, vm) { if (previewEnabled) vm.stopBrowsePreview() }
    val browsing by rememberUpdatedState(previewEnabled)
    DisposableEffect(vm) {
        if (previewEnabled) vm.stopBrowsePreview()
        // The Live backend also owns fullscreen playback. Never stop it on a player transition.
        onDispose { if (browsing) vm.stopBrowsePreview() }
    }
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
            for (group in groups) group.preferred(choices.preferred[group.identity], choices.showUhd)?.rowKey()?.let { row ->
                found[row]?.let { knownGuide[group.identity] = it }
            }
            while (knownGuide.size > SWEDISH_EPG_CACHE) knownGuide.remove(knownGuide.keys.first())
        }
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
    }
    fun update(value: SwedishChoices) { scope.launch { store.save(profile, value); choices = value } }
    suspend fun tuneChannel(original: ChannelEntity, logicalIdentity: String? = null, origin: tv.own.owntv.player.LiveEntryOrigin = tv.own.owntv.player.LiveEntryOrigin.TV_GRID) {
        fun trace(event: tv.own.owntv.player.LiveEntryEvent) = tv.own.owntv.player.LiveEntryDiagnostics.trace.record(0,
            android.os.SystemClock.elapsedRealtime(), origin, event = event)
        trace(tv.own.owntv.player.LiveEntryEvent.CHANNEL_PREPARING)
        val channel = vm.homeChannel(original.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == tv.own.owntv.core.customize.CustomizeKeys.channel(original) && it.sourceId == original.sourceId && it.streamUrl.isNotBlank() } ?: return
        // Fresh entity retains all provider authorization, DRM, catch-up and EPG context.
        val latestChoices = store.choices(profile)
        val zap = groups.mapNotNull { it.preferred(latestChoices.preferred[it.identity], latestChoices.showUhd) }
            .take(200).mapNotNull { row -> vm.homeChannel(row.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == row.stableKey && it.sourceId == row.sourceId } }
        val expected = metadata ?: run { trace(tv.own.owntv.player.LiveEntryEvent.CHANNEL_STALE); return }
        if (currentMetadata != expected || expected.profileId != profile || !vm.swedishContextCurrent(expected)) {
            trace(tv.own.owntv.player.LiveEntryEvent.CHANNEL_STALE); return
        }
        val selectedIdentity = logicalIdentity ?: library.groups.firstOrNull { group -> group.variants.any { it.id == channel.id } }?.identity
        val consistentZap = zap.map { row ->
            val identity = library.groups.firstOrNull { group -> group.variants.any { it.id == row.id } }?.identity
            if (identity != null && identity == selectedIdentity) channel else row
        }.distinctBy { it.id }
        if (vm.watchFullscreenAsync(channel, consistentZap, origin) == LiveViewModel.HomePlayback.IN_APP) onFullscreen()
    }
    var pendingPlayback by remember(profile) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun playChannel(channel: ChannelEntity, identity: String? = null) {
        pendingPlayback?.cancel()
        pendingPlayback = scope.launch { tuneChannel(channel, identity) }
    }
    fun play(variant: SwedishVariant) {
        pendingPlayback?.cancel()
        pendingPlayback = scope.launch {
            vm.homeChannel(variant.id, profile)?.takeIf { tv.own.owntv.core.customize.CustomizeKeys.channel(it) == variant.stableKey && it.sourceId == variant.sourceId }
                ?.let { tuneChannel(it, variant.name.identity) }
        }
    }
    val sourceActions = rememberChannelSourceSelection(store, db, vm, profile, context.second,
        onPlay = { channel, identity -> playChannel(channel, identity) }, onClosed = { scope.launch { restoreChannel() } })
    LaunchedEffect(sourceActions.isOpen) { if (sourceActions.isOpen) previewSuppressedFor = focusId }
    fun openSources() { focused?.let { sourceActions.open(it.identity, null) } }
    androidx.activity.compose.BackHandler(enabled = gridHasFocus && !sourceActions.isOpen) { categoryFocus.requestSafely() }
    androidx.activity.compose.BackHandler(enabled = categoryHasFocus && !editing && onHeader != null) { onHeader?.invoke() }
    SwedishChannelGridFrame(modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key != Key.DirectionDown) categoryEntryRequest = 0
        if (event.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) pendingSourcePress.cancel()
        false
    }.onFocusChanged { if (it.hasFocus) onChildFocused() }, header = {
            TvText(stringResource(R.string.mintv_swedish_channels), Modifier.weight(1f), size = 34, bold = true)

        }, categories = {
        LazyRow(Modifier.testTag("navigation-context").onFocusChanged { categoryHasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionUp -> { if (event.type == KeyEventType.KeyDown) onHeader?.invoke(); onHeader != null }
                    Key.DirectionDown -> { if (event.type == KeyEventType.KeyDown) categoryEntryRequest++; true }
                    else -> false
                }
            }, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            items(listOf(SwedishCollection.GENERAL, SwedishCollection.SPORT, SwedishCollection.OTHER), key = { it.name }) { category ->
                SwedishLiveAction(stringResource(collectionLabel(category)), { collection = category; variants = false }, Modifier.testTag("tv-category-${category.name}").then(if (collection == category) Modifier.focusRequester(categoryFocus) else Modifier), selected = collection == category)
            }
        }
        }, notice = {
        if (library.bounded || failed) TvText(stringResource(R.string.mintv_curated_partial), color = MinTvMuted)
        }, channels = {
            SwedishChannelGrid(list, Modifier.fillMaxSize().onFocusChanged { gridHasFocus = it.hasFocus }.focusGroup()) {
                if (edit) {
                    item(span = { GridItemSpan(maxLineSpan) }) { HomeButton(stringResource(if (choices.showUhd) R.string.mintv_hide_uhd else R.string.mintv_show_uhd), { update(choices.copy(showUhd = !choices.showUhd)) }, Modifier.focusRequester(editFocus)) }
                    item(span = { GridItemSpan(maxLineSpan) }) { HomeButton(stringResource(R.string.mintv_restore_collection), { update(SwedishChoices()); focusId = null; variants = false }) }
                    focused?.let { group ->
                        item(span = { GridItemSpan(maxLineSpan) }) { HomeButton(stringResource(R.string.mintv_choose_variant), { openSources() }) }
                        item(span = { GridItemSpan(maxLineSpan) }) { HomeButton(stringResource(R.string.mintv_pin_channel), { selected?.let(vm::toggleFavorite) }) }
                        item(span = { GridItemSpan(maxLineSpan) }) { HomeButton(stringResource(R.string.mintv_hide_channel), { update(choices.copy(hidden = choices.hidden + group.identity)); focusId = null }) }
                        for (offset in listOf(-1, 1)) item(span = { GridItemSpan(maxLineSpan) }) {
                            HomeButton(stringResource(if (offset < 0) R.string.mintv_move_up else R.string.mintv_move_down), {
                                val order = library.groups.map { it.identity }.toMutableList()
                                val i = order.indexOf(group.identity); val target = i + offset
                                if (i >= 0 && target in order.indices) { order.add(target, order.removeAt(i)); update(choices.copy(order = order)) }
                            })
                        }
                    }
                }
                if (groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { TvText(stringResource(R.string.mintv_curated_empty), color = MinTvMuted, lines = 3) }
                items(count = groups.size, key = { groups[it].identity }) { groupIndex ->
                    val group = groups[groupIndex]
                    val preferred = group.preferred(choices.preferred[group.identity], choices.showUhd)!!
                    val rowFocus = remember(profile, group.identity) { targets.getOrPut(group.identity) { FocusRequester() } }
                    SwedishChannelCard(group.label, preferred.name.quality, rowData[preferred.rowKey()], at,
                        onPlay = {
                            if (preferred.name.collection == SwedishCollection.OTHER) play(preferred)
                            else sourceActions.play(group.identity, null)
                        },
                        modifier = Modifier.testTag("swedish-channel-${group.identity}").focusRequester(rowFocus)
                            .channelGridNavigation(groupIndex, groups.size, { preferredColumn }, onMove = { next, direction ->
                                if (direction == ChannelGridDirection.LEFT || direction == ChannelGridDirection.RIGHT) preferredColumn = next % CHANNEL_GRID_COLUMNS
                                val targetIdentity = groups[next].identity
                                verticalTarget = targetIdentity
                                movement?.cancel()
                                movement = scope.launch {
                                    val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetIdentity }
                                    if (visible == null || visible.offset.y < list.layoutInfo.viewportStartOffset || visible.offset.y + visible.size.height > list.layoutInfo.viewportEndOffset) list.scrollToItem(next + editItems)
                                    repeat(2) { withFrameNanos { } }
                                    targets[targetIdentity]?.requestSafely()
                                }
                            }, onFirstRowUp = {
                                movement?.cancel()
                                if (edit) editFocus.requestSafely() else categoryFocus.requestSafely()
                            }, onNavigation = { pendingSourcePress.cancel() }).logicalChannelKeys({
                            if (preferred.name.collection == SwedishCollection.OTHER) play(preferred)
                            else sourceActions.play(group.identity, null)
                        }, { sourceActions.open(group.identity, it) }, pendingSourcePress).onFocusChanged { if (it.isFocused) {
                            focusId = group.identity
                            if (verticalTarget != group.identity) preferredColumn = groups.indexOf(group).coerceAtLeast(0) % CHANNEL_GRID_COLUMNS
                            verticalTarget = null
                        } })

                }
            }
        })
}

private fun collectionLabel(collection: SwedishCollection) = when (collection) {
    SwedishCollection.GENERAL -> R.string.mintv_swedish_channels
    SwedishCollection.SPORT -> R.string.mintv_hockey_sport
    SwedishCollection.NEWS -> R.string.mintv_news
    SwedishCollection.CHILDREN -> R.string.mintv_children
    SwedishCollection.OTHER -> R.string.mintv_other_swedish
}

/** Artwork-only grid: no hidden preview decoder; full provider library remains available. */
@Composable
internal fun SwedishChannelGridFrame(modifier: Modifier, header: @Composable RowScope.() -> Unit,
    categories: @Composable () -> Unit, notice: @Composable () -> Unit, channels: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(Color(0xFF081A19)).padding(horizontal = 40.mpx, vertical = 22.mpx),
        verticalArrangement = Arrangement.spacedBy(14.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx), content = header)
        categories(); notice()
        Box(Modifier.weight(1f)) { channels() }
    }
}

/** Shared production grid geometry, also exercised with synthetic data by native tests. */
@Composable
internal fun SwedishChannelGrid(state: LazyGridState, modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(CHANNEL_GRID_COLUMNS), modifier = modifier, state = state,
        contentPadding = PaddingValues(6.mpx), verticalArrangement = Arrangement.spacedBy(22.mpx),
        horizontalArrangement = Arrangement.spacedBy(18.mpx), content = content)
}
