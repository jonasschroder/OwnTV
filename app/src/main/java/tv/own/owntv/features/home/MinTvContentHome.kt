package tv.own.owntv.features.home

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
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

private val HomeNavy = Color(0xFF101820)
private val HomeTeal = Color(0xFF69DCC5)

/** Content-only destination in OwnTVShell: no Activity, player or provider session of its own. */
@Composable
fun MinTvContentHome(
    vm: HomeViewModel,
    liveVm: LiveViewModel,
    activeProfileId: Long?,
    previewEnabled: Boolean,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onPauseOrDispose: () -> Unit,
    onLiveTv: () -> Unit,
    onGuide: () -> Unit,
    onSport: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean,
    onRestored: () -> Unit,
    firstRowFocusRequester: FocusRequester,
    onEntryHook: ((() -> Boolean)?) -> Unit,
    contentStart: Dp,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings = koinInject<SettingsRepository>()
    val previewsOn by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val favorites = state.favoriteLive.takeIf { state.profileId == activeProfileId }.orEmpty()
    var selectedId by rememberSaveable(activeProfileId) { mutableStateOf<Long?>(null) }
    val selected = favorites.firstOrNull { it.id == selectedId } ?: favorites.firstOrNull()
    val controller = liveVm.homePreview
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val active = previewEnabled && previewsOn && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
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
    LaunchedEffect(active, activeProfileId) {
        controller.setActive(false)
        controller.setActive(active)
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
    LaunchedEffect(selected?.id, activeProfileId) { controller.focus(selected) }
    LaunchedEffect(selected?.id, activeProfileId, lifecycleState) {
        guide = null // never label a new channel with the previous one's programme
        if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        val channel = selected ?: return@LaunchedEffect
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
    DisposableEffect(firstRowFocusRequester, favorites.isEmpty()) {
        onEntryHook { runCatching { if (favorites.isEmpty()) liveFocus.requestFocus() else firstRowFocusRequester.requestFocus() }.isSuccess }
        onDispose { onEntryHook(null) }
    }
    LaunchedEffect(restoreFocus, previewEnabled, favorites) {
        if (restoreFocus && previewEnabled) {
            withFrameNanos { }
            runCatching { if (favorites.isEmpty()) liveFocus.requestFocus() else firstRowFocusRequester.requestFocus() }
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

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = contentStart, end = 64.mpx, top = 112.mpx, bottom = 48.mpx),
        verticalArrangement = Arrangement.spacedBy(26.mpx),
        modifier = modifier.background(HomeNavy).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                // Re-enable after a failed external launch, but never on automatic focus restoration.
                controller.setActive(active)
                controller.onRemoteNavigation()
            }
            false
        }.onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup(),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = stageText(38, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                    HomeButton(stringResource(R.string.mintv_home_live), { pauseOrDispose(); controller.setActive(false); onLiveTv() }, Modifier.focusRequester(liveFocus))
                    HomeButton(stringResource(R.string.mintv_guide), { pauseOrDispose(); controller.setActive(false); onGuide() })
                    HomeButton(stringResource(R.string.mintv_sport), { pauseOrDispose(); controller.setActive(false); onSport() })
                    HomeButton(stringResource(if (previewsOn) R.string.mintv_preview_on else R.string.mintv_preview_off), {
                        controller.setActive(false)
                        scope.launch { settings.setLivePreviewEnabled(!previewsOn) }
                    })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.mpx)) {
                Box(Modifier.weight(1.3f).aspectRatio(16f / 9f).clip(RoundedCornerShape(22.mpx)).background(Color.Black), contentAlignment = Alignment.Center) {
                    val hasVideo = active && selected != null && previewState != LivePreviewEngine.State.IDLE && previewState != LivePreviewEngine.State.ERROR
                    if (hasVideo) {
                        ExoPreviewSurface(liveVm.previewEngine, Modifier.fillMaxSize(), useTextureView = true)
                    } else {
                        selected?.displayLogoUrl?.let { logo ->
                            AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(150.mpx).padding(12.mpx))
                        }
                    }
                    val message = when {
                        selected == null -> R.string.mintv_favorites_empty
                        !previewsOn -> R.string.mintv_preview_disabled
                        blocked -> R.string.content_preview_single_stream
                        previewState == LivePreviewEngine.State.ERROR -> R.string.mintv_preview_unsupported
                        previewState == LivePreviewEngine.State.LOADING && active -> R.string.mintv_preview_loading
                        previewState == LivePreviewEngine.State.IDLE -> R.string.mintv_preview_hint
                        else -> null
                    }
                    message?.let {
                        Text(stringResource(it), style = stageText(19, 500), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(HomeNavy.copy(alpha = 0.9f)).padding(20.mpx))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.mpx)) {
                    Text(selected?.name ?: stringResource(R.string.mintv_favorites_title), style = stageText(28, 700), color = HomeTeal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val now = guide?.now?.takeIf { clock in it.startMs until it.stopMs }
                    Text(now?.title ?: stringResource(R.string.mintv_epg_unavailable), style = stageText(36, 700), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    now?.let {
                        val progress = ((clock - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(6.mpx).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.mpx))) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(HomeTeal, RoundedCornerShape(3.mpx)))
                        }
                        it.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(description, style = stageText(18, 400), color = Color.LightGray, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(stringResource(R.string.mintv_next, guide?.next?.title ?: stringResource(R.string.mintv_epg_unavailable)), style = stageText(19, 500), color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.mintv_ok_hint), style = stageText(17, 400), color = HomeTeal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                Text(stringResource(R.string.mintv_favorites_title), style = stageText(25, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (favorites.isEmpty()) {
                    Text(stringResource(if (state.isLoading || state.profileId != activeProfileId) R.string.mintv_favorites_loading else R.string.mintv_favorites_empty), style = stageText(19, 400), color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(8.mpx), modifier = Modifier.focusRestorer().focusGroup()) {
                        itemsIndexed(favorites, key = { _, channel -> channel.id }) { _, channel ->
                            HomeButton(channel.name, { onPlayChannel(channel, favorites) },
                                Modifier.width(250.mpx).height(112.mpx)
                                    .then(if (channel.id == selected?.id) Modifier.focusRequester(firstRowFocusRequester) else Modifier)
                                    .onFocusChanged { if (it.hasFocus) {
                                        selectedId = channel.id
                                        controller.focus(channel)
                                        liveVm.onChannelFocused(channel)
                                        onChildFocused()
                                    } },
                                logo = channel.displayLogoUrl,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                Text(stringResource(R.string.mintv_apps_title), style = stageText(25, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    ExternalShortcut.entries.forEach { shortcut ->
                        HomeButton(stringResource(shortcut.label), {
                            pauseOrDispose()
                            controller.setActive(false)
                            val intent = if (shortcut == ExternalShortcut.TWITCH) MinTvExternalApps.ohnePixel(context)
                                else MinTvExternalApps.launch(context, shortcut.packages)
                            if (intent != null) openExternal(intent) else missingApp = shortcut
                        }, Modifier.weight(1f).height(82.mpx))
                    }
                    HomeButton(stringResource(R.string.mintv_home_settings), { openExternal(MinTvIntents.settings()) }, Modifier.weight(1f).height(82.mpx))
                }
                Text(stringResource(R.string.mintv_feeds_deferred), style = stageText(16, 400), color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
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
    TWITCH(R.string.mintv_twitch, R.string.mintv_install_soundtv, listOf(MinTvExternalApps.SOUND_TV)),
    SMART_TUBE(R.string.mintv_smarttube, R.string.mintv_install_smarttube, MinTvExternalApps.smartTubePackages),
    SVT(R.string.mintv_svt, R.string.mintv_install_svt, listOf(MinTvExternalApps.SVT_PLAY)),
}

@Composable
private fun HomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, logo: String? = null) {
    Button(
        onClick = onClick, modifier = modifier,
        colors = ButtonDefaults.colors(containerColor = Color(0xFF1B2B36), contentColor = Color.White, focusedContainerColor = HomeTeal, focusedContentColor = HomeNavy),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.mpx, Color.White))),
        scale = ButtonDefaults.scale(focusedScale = 1.03f),
    ) {
        logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.mpx).padding(end = 10.mpx)) }
        Text(label, style = stageText(18, 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
