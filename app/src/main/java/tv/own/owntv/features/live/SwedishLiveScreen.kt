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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.theme.mpx

/** One logical row, real provider variants and the existing Live controller/EPG surface. */
@Composable
internal fun SwedishLiveScreen(vm: LiveViewModel, onLibrary: () -> Unit, onFullscreen: () -> Unit,
    onOpenGuide: () -> Unit, onChildFocused: () -> Unit, previewEnabled: Boolean, modifier: Modifier) {
    val store: SwedishChannelStore = koinInject()
    val db: OwnTVDatabase = koinInject()
    val context by vm.homeLibraryContext.collectAsStateWithLifecycle()
    val profile = context.first
    val scope = rememberCoroutineScope()
    var choices by remember(profile) { mutableStateOf(SwedishChoices()) }
    var library by remember(profile) { mutableStateOf(SwedishLibrary(emptyList())) }
    var collection by rememberSaveable(profile) { mutableStateOf(SwedishCollection.GENERAL) }
    var focusId by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var selected by remember(profile) { mutableStateOf<ChannelEntity?>(null) }
    var edit by rememberSaveable(profile) { mutableStateOf(false) }
    var variants by rememberSaveable(profile) { mutableStateOf(false) }
    var failed by remember(profile) { mutableStateOf(false) }
    val list = rememberLazyListState()
    val entry = remember { FocusRequester() }
    val epg by vm.nowNext.collectAsStateWithLifecycle()
    val blocked by vm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val previewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val external by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val providerNames by vm.providerNames.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = lifecycle == Lifecycle.State.RESUMED
    LaunchedEffect(context) { choices = store.choices(profile) }
    LaunchedEffect(context, choices) {
        failed = false
        try {
            db.invalidationTracker.createFlow("channels", "categories", "profiles").collect {
                library = store.library(profile, context.second, choices)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val groups = library.groups.filter { g ->
        (g.variants.first().name.collection == collection || collection == SwedishCollection.CHILDREN && g.label == "SVT Barn" || collection == SwedishCollection.NEWS && g.label == "SVT24") && g.preferred(choices.preferred[g.identity], choices.showUhd) != null
    }
    val focused = library.groups.firstOrNull { it.identity == focusId }
    LaunchedEffect(focusId, context, choices, library) {
        selected = null
        focused?.preferred(choices.preferred[focused.identity], choices.showUhd)?.let { variant ->
            selected = vm.homeChannel(variant.id, profile)
            selected?.let(vm::onChannelFocused)
        }
    }
    LaunchedEffect(selected?.id, active, previewEnabled, previewSetting) {
        if (active && previewEnabled && previewSetting) selected?.let { delay(700); vm.playPreview(it) }
    }
    fun update(value: SwedishChoices) { scope.launch { store.save(profile, value); choices = value } }
    fun play(id: Long) { scope.launch {
        val channel = vm.homeChannel(id, profile) ?: return@launch
        // Fresh entity retains all provider authorization, DRM, catch-up and EPG context.
        val zap = groups.mapNotNull { it.preferred(choices.preferred[it.identity], choices.showUhd) }
            .take(200).mapNotNull { vm.homeChannel(it.id, profile) }
        vm.watchFullscreen(channel, zap)
        if (!external) onFullscreen()
    } }
    Column(modifier.fillMaxSize().padding(top = 122.mpx, start = 30.mpx, end = 28.mpx, bottom = 24.mpx)
        .onFocusChanged { if (it.hasFocus) onChildFocused() }, verticalArrangement = Arrangement.spacedBy(16.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            TvText(stringResource(R.string.mintv_swedish_channels), Modifier.weight(1f), size = 34, bold = true)
            HomeButton(stringResource(R.string.mintv_full_library), onLibrary, Modifier.focusRequester(entry))
            HomeButton(stringResource(R.string.mintv_collection_settings), { edit = !edit })
            HomeButton(stringResource(R.string.common_nav_guide), onOpenGuide)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            items(SwedishCollection.entries, key = { it.name }) { category ->
                HomeButton(stringResource(collectionLabel(category)), { collection = category; variants = false })
            }
        }
        if (library.bounded || failed) TvText(stringResource(R.string.mintv_curated_partial), color = MinTvMuted)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.mpx)) {
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
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
                    TvCard({ play(preferred.id) }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focusId = group.identity }, selected = focusId == group.identity) {
                        TvText(group.label, size = 26, bold = true)
                        TvText(preferred.rawName, color = MinTvMuted, size = 18)
                    }
                    if (choices.showVariants || variants && focusId == group.identity) {
                        group.variants.forEach { variant ->
                            HomeButton(listOfNotNull(variant.rawName, providerNames[variant.sourceId]).joinToString(stringResource(R.string.mintv_team_separator)), {
                                update(choices.copy(preferred = choices.preferred + (group.identity to variant.stableKey)))
                                variants = false
                            }, Modifier.padding(start = 24.mpx))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                HomeButton(stringResource(R.string.mintv_choose_variant), { variants = !variants })
                LiveStagePane(selected, selected?.name, epg, vm.previewEngine,
                    showVideo = active && previewEnabled && previewSetting,
                    singleSessionBlocked = blocked, scheduleFocus = remember { FocusRequester() },
                    onOpenProgramme = { onOpenGuide() }, onBackToList = { entry.requestSafely() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun collectionLabel(collection: SwedishCollection) = when (collection) {
    SwedishCollection.GENERAL -> R.string.mintv_swedish_channels
    SwedishCollection.SPORT -> R.string.mintv_hockey_sport
    SwedishCollection.NEWS -> R.string.mintv_news
    SwedishCollection.CHILDREN -> R.string.mintv_children
    SwedishCollection.OTHER -> R.string.mintv_other_swedish
}
