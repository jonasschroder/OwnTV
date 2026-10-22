package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.discovery.DiscoveryCard
import tv.own.owntv.features.live.*
import tv.own.owntv.features.live.source.*
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Snapshot per profile/visit; EPG stays live. No metadata discovery, sports fetch or preview surface. */
@Composable
internal fun MinTvContentHome(
    vm: HomeViewModel, liveVm: LiveViewModel, activeProfileId: Long?, previewEnabled: Boolean,
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit, onPauseOrDispose: () -> Unit,
    onLiveTv: () -> Unit, onGuide: () -> Unit, onPlayMovie: (Long, Long) -> Unit,
    onOpenMovie: (DiscoveryCard) -> Unit, onPlayEpisode: (Long, Long, Long) -> Unit,
    onOpenSeries: (Long) -> Unit, onSources: () -> Unit, onManageSources: () -> Unit,
    onOpenSettings: (MinTvSettingsDestination) -> Unit,
    onChildFocused: () -> Unit, restoreFocus: Boolean, onRestored: () -> Unit,
    firstRowFocusRequester: FocusRequester, onEntryHook: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit,
    contentStart: Dp, reservedTop: Dp = 144.mpx, statusWidth: Dp = 760.mpx, modifier: Modifier = Modifier,
) {
    val settings: SettingsRepository = koinInject()
    val db: OwnTVDatabase = koinInject()
    val store: SwedishChannelStore = koinInject()
    val custom: CustomizationStore = koinInject()
    val sourceAccess by remember(settings, db) {
        activeProfileSources(settings, db.sourceDao()).map {
            SmartSourceAccess(it.profileId, it.liveSourceIds, it.movieSourceIds, it.seriesSourceIds)
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val context by liveVm.homeLibraryContext.collectAsStateWithLifecycle()
    val metadata by liveVm.swedishRowContext.collectAsStateWithLifecycle()
    val movieVisibility by remember(activeProfileId) { custom.observe(activeProfileId ?: -1, MediaType.MOVIE) }
        .collectAsStateWithLifecycle(initialValue = null)
    val seriesVisibility by remember(activeProfileId) { custom.observe(activeProfileId ?: -1, MediaType.SERIES) }
        .collectAsStateWithLifecycle(initialValue = null)
    val profileSettings by remember(activeProfileId) { db.profileDao().observeById(activeProfileId ?: -1) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = previewEnabled && lifecycle == Lifecycle.State.RESUMED
    var snapshot by remember(activeProfileId, context, metadata, movieVisibility, seriesVisibility, profileSettings, sourceAccess) { mutableStateOf<SmartHomeSnapshot?>(null) }
    var visible by remember(activeProfileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var logicalBySource by remember(activeProfileId, context) { mutableStateOf<Map<String, String>>(emptyMap()) }
    DisposableEffect(vm, liveVm) {
        vm.setPreviewEnabled(false); vm.stopPreview(); liveVm.homePreview.setActive(false)
        onDispose { onPauseOrDispose(); liveVm.homePreview.setActive(false) }
    }
    LaunchedEffect(activeProfileId, context, metadata, movieVisibility, seriesVisibility, profileSettings, sourceAccess, active) {
        if (!active) return@LaunchedEffect
        val profile = activeProfileId ?: return@LaunchedEffect
        if (profileSettings == null || movieVisibility == null || seriesVisibility == null || sourceAccess?.profile != profile) return@LaunchedEffect
        val expected = metadata?.takeIf { it.profileId == profile && context.first == profile && it.sourceIds == context.second }
            ?: return@LaunchedEffect
        try {
            val choices = store.choices(profile)
            val allGroups = store.library(profile, expected.sourceIds, choices.copy(hidden = emptySet())).groups
            logicalBySource = allGroups.flatMap { g -> g.variants.map { it.stableKey to g.identity } }.toMap()
            val groups = allGroups.filterNot { it.identity in choices.hidden }.take(48)
            val channels = groups.mapNotNull { group ->
                group.preferred(choices.preferred[group.identity], choices.showUhd)?.let { variant ->
                    liveVm.homeChannel(variant.id, profile)?.let { SmartChannel(group.identity, group.label, it) }
                }
            }
            val recent = db.channelDao().recentlyWatchedWithTimestampFiltered(profile, expected.sourceIds, 20).first()
            var watched: ChannelEntity? = null
            var watchedAt: Long? = null
            for (row in recent) {
                val actual = liveVm.homeChannel(row.channel.id, profile) ?: continue
                if (SwedishChannels.recognize(actual.name)?.identity in choices.hidden) continue
                val group = allGroups.firstOrNull { g -> g.variants.any { it.id == actual.id } }
                if (group != null && group.identity in choices.hidden) continue
                val preference = group?.let { choices.preferred[it.identity] }
                watched = if (preference == null) actual else group.preferred(preference, choices.showUhd)?.let { liveVm.homeChannel(it.id, profile) } ?: actual
                watchedAt = row.watchedAt
                break
            }
            val resumes = readSmartResumes(db, settings, custom, profile)
            val access = sourceAccess ?: return@LaunchedEffect
            val hasSources = access.live.isNotEmpty() || access.movies.isNotEmpty() || access.series.isNotEmpty()
            if (liveVm.swedishContextCurrent(expected)) snapshot = selectSmartHome(profile, hasSources, channels,
                watched, watchedAt, resumes, System.currentTimeMillis())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // A failed local read cannot invent a channel or progress. Existing TV remains reachable.
            if (liveVm.swedishContextCurrent(expected)) snapshot = SmartHomeSnapshot(profile, true, emptyList(), null, null, null, emptyList())
        }
    }
    val current = snapshot?.takeIf { it.profile == activeProfileId && context.first == activeProfileId }
    val keys = (listOfNotNull(current?.live) + visible).map { it.homeEpgKey() }.distinct().take(SWEDISH_EPG_WINDOW)
    val guide = rememberHomeDashboardGuide(liveVm, activeProfileId, active, keys, current?.live)
    val profile = activeProfileId ?: -1L
    val savedChoices by remember(profile) { store.observeChoices(profile) }.collectAsStateWithLifecycle(initialValue = null)
    var restoreAfterPicker by remember(profile) { mutableStateOf(false) }
    fun logical(channel: ChannelEntity): String = current?.channels?.firstOrNull { it.channel.homeEpgKey() == channel.homeEpgKey() }?.key
        ?: logicalBySource[tv.own.owntv.core.customize.CustomizeKeys.channel(channel)].orEmpty()
    val sourceActions = rememberChannelSourceSelection(store, db, liveVm, profile, context.second,
        onPlay = { actual, identity -> onPlayChannel(actual, current?.channels.orEmpty().map { if (it.key == identity) actual else it.channel }) },
        onClosed = { restoreAfterPicker = true })
    // A saved source changes only the entity/EPG behind an existing logical key, not Home membership,
    // programme ordering, hero kind or focus. EPG refresh itself never rebuilds this snapshot.
    LaunchedEffect(savedChoices, profile, current != null) {
        try {
            val before = current ?: return@LaunchedEffect
            val choices = savedChoices ?: return@LaunchedEffect
            val groups = store.library(profile, context.second, choices).groups
            suspend fun selected(identity: String, exact: ChannelEntity): ChannelEntity {
                val group = groups.singleOrNull { it.identity == identity } ?: return exact
                val variant = ChannelSourceResolver.resolve(group, choices, exact = tv.own.owntv.core.customize.CustomizeKeys.channel(exact)).selected ?: return exact
                return liveVm.homeChannel(variant.id, profile) ?: exact
            }
            val rows = before.channels.map { it.copy(channel = selected(it.key, it.channel)) }
            val live = before.live?.let { old ->
                val group = groups.firstOrNull { g -> g.variants.any { it.stableKey == tv.own.owntv.core.customize.CustomizeKeys.channel(old) } }
                    ?: groups.firstOrNull { it.identity == logical(old) }
                if (group == null) old else selected(group.identity, old)
            }
            if (liveVm.homeLibraryContext.value == context && snapshot == before) snapshot = before.copy(channels = rows, live = live)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Retain the current snapshot; playback still revalidates availability. */ }
    }
    fun play(channel: ChannelEntity) = sourceActions.play(logical(channel), channel)
    fun resume(item: SmartResume) {
        val profile = activeProfileId ?: return
        scope.launch {
            val actual = readSmartResumes(db, settings, custom, profile, exact = item).firstOrNull { it.key == item.key } ?: return@launch
            if (settings.activeProfileId.first() != profile) return@launch
            when (actual.kind) {
                SmartResumeKind.MOVIE -> onPlayMovie(actual.itemId, actual.position)
                SmartResumeKind.EPISODE -> onPlayEpisode(actual.seriesId ?: return@launch, actual.itemId, actual.position)
            }
        }
    }
    SmartHomeContent(current, guide, activeProfileId, ::play, ::resume, onOpenSeries, onLiveTv, onGuide, onSources,
        firstRowFocusRequester, onEntryHook, onChildFocused, restoreFocus, onRestored, contentStart, reservedTop, modifier,
        onVisibleChannels = { visible = it }, onHeader = onHeader,
        onSource = { channel, downTime -> sourceActions.open(logical(channel), downTime) },
        restoreSourceFocus = restoreAfterPicker, onSourceRestored = { restoreAfterPicker = false })
}

@Composable
internal fun HomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, logo: String? = null, primary: Boolean = false) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.mpx)),
        contentPadding = PaddingValues(horizontal = 22.mpx, vertical = 15.mpx),
        colors = ButtonDefaults.colors(containerColor = if (primary) Color(0xFF234C45) else MinTvPanel,
            contentColor = Color.White, focusedContainerColor = Color(0xFF2B3B47), focusedContentColor = Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.mpx).padding(end = 10.mpx)) }
        Text(label, style = stageText(22, 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal fun homeSectionLabel(section: HomeSection) = when (section) {
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
