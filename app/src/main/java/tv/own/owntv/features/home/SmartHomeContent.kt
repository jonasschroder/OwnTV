package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.*
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.features.live.SwedishRowInfo
import tv.own.owntv.features.live.SwedishChannelCard
import tv.own.owntv.features.live.ChannelQuality
import tv.own.owntv.features.live.source.logicalChannelKeys
import tv.own.owntv.features.live.source.SourcePressCancellation
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.mpx

internal val DesignLabBackground = Color(0xFF071312)

internal data class SmartChannel(val key: String, val label: String, val channel: ChannelEntity)
internal data class SmartHomeSnapshot(val profile: Long, val hasSources: Boolean,
    val channels: List<SmartChannel>, val live: ChannelEntity?, val liveWatchedAt: Long?,
    val heroResume: SmartResume?, val resumes: List<SmartResume>, val favorites: List<SmartChannel> = emptyList())

/** Native Din kväll composition; only metadata updates during a visit, never membership/order. */
@Composable
internal fun SmartHomeContent(snapshot: SmartHomeSnapshot?, guide: HomeGuideState, profile: Long?,
    onLive: (ChannelEntity) -> Unit, onResume: (SmartResume) -> Unit, onEpisodes: (Long) -> Unit,
    onTv: () -> Unit, onGuide: () -> Unit, onSources: () -> Unit,
    firstFocus: FocusRequester, onEntry: ((() -> Boolean)?) -> Unit,
    onFocused: () -> Unit, restore: Boolean, onRestored: () -> Unit,
    contentStart: Dp, reservedTop: Dp, modifier: Modifier = Modifier,
    onVisibleChannels: (List<ChannelEntity>) -> Unit = {}, onHeader: () -> Unit,
    onSource: ((ChannelEntity, Long) -> Unit)? = null,
    restoreSourceFocus: Boolean = false, onSourceRestored: () -> Unit = {},
    matchday: (@Composable (Modifier) -> Unit)? = null, companion: @Composable () -> Unit = {}) {
    val current = snapshot?.takeIf { it.profile == profile }
    val channels = current?.channels.orEmpty()
    val resumes = smartHomeResumeRows(current?.heroResume, current?.resumes.orEmpty(), matchday != null)
    val favorites = current?.favorites.orEmpty()
    val list = rememberLazyListState()
    val rail = rememberLazyListState()
    val favoritesRail = rememberLazyListState()
    var favoriteKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    val entryFavorite = favoriteKey?.takeIf { key -> favorites.any { it.key == key } } ?: favorites.firstOrNull()?.key
    val scope = rememberCoroutineScope()
    var movement by remember(profile) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(profile) { onDispose { movement?.cancel() } }
    val pendingSourcePress = remember(profile) { SourcePressCancellation() }
    val companionFocus = remember(profile) { FocusRequester() }
    var sourceFromCompanion by remember(profile) { mutableStateOf(false) }
    val heroSource: ((ChannelEntity, Long, Boolean) -> Unit)? = if (onSource == null) null else { channel, down, companion ->
        sourceFromCompanion = companion; onSource(channel, down)
    }
    val nav = rememberHomeDpadNavigation(firstFocus, 4, profile, firstSection = 0)
    var channelKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var resumeKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    val keys = channels.map { it.key }
    val resumeKeys = resumes.map { it.key }
    var previousKeys by rememberSaveable(profile) { mutableStateOf(keys) }
    var previousResumes by rememberSaveable(profile) { mutableStateOf(resumeKeys) }
    val entryChannel = smartFocusKey(channelKey, previousKeys, keys)
    val entryResume = smartFocusKey(resumeKey, previousResumes, resumeKeys)
    SideEffect {
        if (current != null) {
            channelKey = entryChannel; resumeKey = entryResume
            previousKeys = keys; previousResumes = resumeKeys
        }
    }
    val indices = smartHomeSectionIndices(channels.isNotEmpty(), favorites.isNotEmpty(), resumes.isNotEmpty())
    LaunchedEffect(indices, current != null) {
        if (current != null && indices.getOrNull(nav.selected) == -1) {
            nav.selected = 0; list.scrollToItem(0)
            repeat(2) { withFrameNanos { } }; firstFocus.requestSafely()
        }
    }
    val currentIndices by rememberUpdatedState(indices)
    val currentChannels by rememberUpdatedState(channels)
    val currentChannelKey by rememberUpdatedState(entryChannel)
    val currentFavorites by rememberUpdatedState(favorites)
    val currentFavoriteKey by rememberUpdatedState(entryFavorite)
    fun enter(): Boolean {
        movement?.cancel()
        movement = scope.launch {
            val section = nav.selected.takeIf { currentIndices.getOrNull(it) != -1 } ?: 0
            val target = currentIndices[section].coerceAtLeast(0)
            val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (visible == null || visible.offset < list.layoutInfo.viewportStartOffset || visible.offset + visible.size > list.layoutInfo.viewportEndOffset)
                list.scrollToItem(target)
            if (section == 1) {
                val index = currentChannels.indexOfFirst { it.key == currentChannelKey }.coerceAtLeast(0)
                val item = rail.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null || item.offset < rail.layoutInfo.viewportStartOffset || item.offset + item.size > rail.layoutInfo.viewportEndOffset)
                    rail.scrollToItem(index)
            }
            if (section == 2) {
                val index = currentFavorites.indexOfFirst { it.key == currentFavoriteKey }.coerceAtLeast(0)
                val item = favoritesRail.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null || item.offset < favoritesRail.layoutInfo.viewportStartOffset || item.offset + item.size > favoritesRail.layoutInfo.viewportEndOffset)
                    favoritesRail.scrollToItem(index)
            }
            repeat(2) { withFrameNanos { } }
            if (!nav.focus[section].requestSafely()) { list.scrollToItem(0); withFrameNanos { }; firstFocus.requestSafely() }
        }
        return true
    }
    DisposableEffect(firstFocus, profile) { onEntry { enter() }; onDispose { onEntry(null) } }
    LaunchedEffect(restore, current) { if (restore && current != null) { enter(); onRestored() } }
    LaunchedEffect(restoreSourceFocus, current) {
        if (restoreSourceFocus && current != null) {
            repeat(2) { withFrameNanos { } }
            if (!(sourceFromCompanion && (current.heroResume != null || matchday != null) && current.live != null && companionFocus.requestSafely())) enter()
            onSourceRestored()
        }
    }
    val visible by remember(channels) { derivedStateOf {
        val indices = rail.layoutInfo.visibleItemsInfo.map { it.index }
        (indices + ((indices.lastOrNull() ?: -1) + 1..(indices.lastOrNull() ?: -1) + 2))
            .distinct().mapNotNull { channels.getOrNull(it)?.channel }
    } }
    LaunchedEffect(visible) { onVisibleChannels(visible) }
    BoxWithConstraints(modifier.fillMaxSize().background(DesignLabBackground)) {
        // DESIGNLABB 04: five visible native channels, with the rest available by horizontal D-pad scroll.
        val width = ((maxWidth - contentStart - 64.mpx - 10.mpx - 16.mpx * 4) / 5).coerceAtLeast(140.mpx)
        LazyColumn(state = list, contentPadding = PaddingValues(start = contentStart, end = 64.mpx, bottom = 36.mpx),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
            modifier = Modifier.fillMaxSize().padding(top = reservedTop).clip(androidx.compose.ui.graphics.RectangleShape)
                .onPreviewKeyEvent { event ->
                    if (event.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) pendingSourcePress.cancel()
                    val delta = when (event.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> 0 }
                    val target = nextHomeSection(nav.selected, delta, indices)
                    if (delta == -1 && nav.selected == 0) {
                        if (event.type == KeyEventType.KeyDown) { movement?.cancel(); onHeader() }
                        true
                    } else if (target == null) false else {
                        if (event.type == KeyEventType.KeyDown) {
                            movement?.cancel()
                            movement = scope.launch {
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == indices[target] }
                            if (item == null || item.offset < list.layoutInfo.viewportStartOffset || item.offset + item.size > list.layoutInfo.viewportEndOffset)
                                list.animateScrollToItem(indices[target])
                            repeat(2) { withFrameNanos { } }
                            nav.focus[target].requestSafely()
                            }
                        }
                        true
                    }
                }.onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup()) {
            item(key = "smart-hero") {
                Column(verticalArrangement = Arrangement.spacedBy(20.mpx)) {
                TvText(stringResource(R.string.mintv_smart_evening), size = 34, bold = true)
                SmartHomeHero(current, guide, onLive, onResume, onEpisodes, onTv, onGuide, onSources,
                    nav.section(0), heroSource, pendingSourcePress, companionFocus, matchday, companion)
                }
            }
            if (channels.isNotEmpty()) item(key = "smart-now") {
                Column(Modifier.onFocusChanged { if (it.hasFocus) nav.selected = 1 }.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TvText(stringResource(R.string.mintv_v08_now), size = 27, bold = true, lines = 1)
                        SmartAction(stringResource(R.string.mintv_smart_all_channels), onTv, compact = true)
                    }
                    LazyRow(state = rail, contentPadding = PaddingValues(5.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx),
                        modifier = Modifier.testTag("smart-channel-row").focusGroup()) {
                        items(channels, key = { it.key }) { item ->
                            SmartChannelCard(item, guide.rows[item.channel.homeEpgKey()], guide.at, { onLive(item.channel) },
                                Modifier.width(width).then(if (item.key == entryChannel) nav.section(1) else Modifier)
                                    .then(if (onSource != null) Modifier.logicalChannelKeys({ onLive(item.channel) }, { sourceFromCompanion = false; onSource(item.channel, it) }, pendingSourcePress) else Modifier)
                                    .testTag("smart-channel-${item.key}").onFocusChanged { if (it.isFocused) channelKey = item.key })
                        }
                    }

                }
            }
            if (resumes.isNotEmpty()) item(key = "smart-resume") {
                Column(Modifier.onFocusChanged { if (it.hasFocus) nav.selected = 3 }.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    TvText(stringResource(R.string.search_continue), size = 27, bold = true, lines = 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                        resumes.take(3).forEach { item ->
                            SmartResumeCard(item, { onResume(item) }, Modifier.weight(1f)
                                .then(if (item.key == entryResume) nav.section(3) else Modifier)
                                .testTag("smart-resume-${item.key}").onFocusChanged { if (it.isFocused) resumeKey = item.key })
                        }
                    }
                }
            }
            if (favorites.isNotEmpty()) item(key = "smart-favorites") {
                Column(Modifier.onFocusChanged { if (it.hasFocus) nav.selected = 2 }.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    TvText(stringResource(R.string.mintv_favorites_title), size = 27, bold = true)
                    LazyRow(state = favoritesRail, horizontalArrangement = Arrangement.spacedBy(16.mpx), contentPadding = PaddingValues(5.mpx)) {
                        items(favorites, key = { it.key }) { favorite ->
                            SmartChannelCard(favorite, guide.rows[favorite.channel.homeEpgKey()], guide.at, { onLive(favorite.channel) },
                                Modifier.width(width).testTag("smart-favorite-${favorite.key}").onFocusChanged { if (it.isFocused) favoriteKey = favorite.key }.then(if (favorite.key == entryFavorite) nav.section(2) else Modifier)
                                    .then(if (onSource != null) Modifier.logicalChannelKeys({ onLive(favorite.channel) }, { sourceFromCompanion = false; onSource(favorite.channel, it) }, pendingSourcePress) else Modifier))
                        }
                    }
                }
            }

        }
    }
}

@Composable
internal fun SmartAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, compact: Boolean = false) {
    Button(onClick, modifier.height((if (compact) 36 else 50).mpx * maxOf(1f, LocalDensity.current.fontScale)),
        contentPadding = PaddingValues(horizontal = if (compact) 10.mpx else 20.mpx, vertical = 5.mpx),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.mpx)),
        colors = ButtonDefaults.colors(containerColor = if (primary) MinTvTeal else Color.Transparent,
            contentColor = if (primary) MinTvNavy else MinTvTeal, focusedContainerColor = if (primary) Color(0xFFE4FFFF) else Color(0xFF29454D),
            focusedContentColor = if (primary) MinTvNavy else Color.White),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) { TvText(label, size = if (compact) 18 else 22, bold = true, lines = 1, color = LocalContentColor.current) }
}

@Composable
private fun SmartHomeHero(snapshot: SmartHomeSnapshot?, guide: HomeGuideState,
    onLive: (ChannelEntity) -> Unit, onResume: (SmartResume) -> Unit, onEpisodes: (Long) -> Unit,
    onTv: () -> Unit, onGuide: () -> Unit, onSources: () -> Unit, first: Modifier,
    onSource: ((ChannelEntity, Long, Boolean) -> Unit)?, pendingSourcePress: SourcePressCancellation, companionFocus: FocusRequester,
    matchday: (@Composable (Modifier) -> Unit)?, companion: @Composable () -> Unit) {
    val live = snapshot?.live
    val resume = snapshot?.heroResume
    val info = live?.let { guide.rows[it.homeEpgKey()]?.at(guide.at) }
    val time = rememberSystemTimeFormatter()
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    Row(Modifier.fillMaxWidth().heightIn(min = 302.mpx * scale).testTag("smart-hero"), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
        if (matchday != null) Column(Modifier.weight(3.8f), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            matchday(first)
            if (live != null) SmartAction(stringResource(R.string.mintv_smart_last_tv), { onLive(live) },
                Modifier.testTag("smart-last-live").focusRequester(companionFocus)
                    .then(if (onSource != null) Modifier.logicalChannelKeys({ onLive(live) }, { onSource(live, it, true) }, pendingSourcePress) else Modifier), compact = true)
        }
        else Column(Modifier.weight(3.8f).background(Color(0xFF162E27), RoundedCornerShape(24.mpx)).padding(30.mpx), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
            TvText(stringResource(when {
                resume?.kind == SmartResumeKind.EPISODE -> R.string.mintv_smart_continue_series
                resume != null -> R.string.search_continue
                snapshot?.liveWatchedAt != null -> R.string.mintv_smart_back_channel
                else -> R.string.mintv_smart_evening
            }), size = 18, color = MinTvTeal, bold = true, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                if (resume == null && live != null) HomeChannelLogo(live.name, info?.logo ?: live.displayLogoUrl, Modifier.size(64.mpx))
                if (resume != null) SmartResumeIdentity(resume, 64.mpx)
                Column(Modifier.weight(1f)) {
                    (resume?.seriesTitle ?: live?.name)?.let { TvText(it, size = 21, color = MinTvMuted, lines = 1) }
                    TvText(resume?.title ?: info?.now?.title ?: stringResource(if (live != null) R.string.mintv_v08_empty_now
                        else if (snapshot == null) R.string.mintv_smart_loading else R.string.mintv_smart_start), size = 36, bold = true, lines = 2)
                }
            }
            if (live == null && resume == null && snapshot != null) {
                TvText(stringResource(if (snapshot.hasSources) R.string.mintv_smart_browse_hint else R.string.mintv_smart_source_hint),
                    size = 22, color = MinTvMuted, lines = 2)
            }
            if (resume != null) {
                SmartResumeLine(resume)
                ProgrammeProgress((resume.position.toDouble() / resume.duration).toFloat(), Modifier.widthIn(max = 640.mpx))
            } else info?.now?.let {
                TvText(stringResource(R.string.mintv_v08_time_range, time(it.startMs), time(it.stopMs)), size = 21, color = MinTvMuted, lines = 1)
                ProgrammeProgress(it.progress(guide.at), Modifier.widthIn(max = 640.mpx))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                SmartAction(stringResource(when { resume != null -> R.string.content_action_resume; live != null -> R.string.mintv_watch_channel
                    snapshot?.hasSources == false -> R.string.mintv_smart_add_source; else -> R.string.mintv_home_live }),
                    { when { resume != null -> onResume(resume); live != null -> onLive(live); snapshot?.hasSources == false -> onSources(); else -> onTv() } },
                    first.testTag("smart-hero-play").then(if (resume == null && live != null && onSource != null)
                        Modifier.logicalChannelKeys({ onLive(live) }, { onSource(live, it, false) }, pendingSourcePress) else Modifier), primary = true)
                if (resume?.seriesId != null) SmartAction(stringResource(R.string.mintv_smart_episodes), { onEpisodes(resume.seriesId) })
                else if (live != null) SmartAction(stringResource(R.string.mintv_guide), onGuide)
                if (resume != null && live != null) SmartAction(stringResource(R.string.mintv_smart_last_tv), { onLive(live) },
                    Modifier.testTag("smart-last-live").focusRequester(companionFocus).then(if (onSource != null)
                        Modifier.logicalChannelKeys({ onLive(live) }, { onSource(live, it, true) }, pendingSourcePress) else Modifier), compact = true)
            }
        }
        Column(Modifier.weight(1f).padding(8.mpx), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
            companion()
            TvText(stringResource(R.string.mintv_v08_version_chip, tv.own.owntv.BuildConfig.VERSION_NAME), size = 15, color = MinTvMuted, lines = 1)
        }
    }
}

@Composable
private fun SmartChannelCard(item: SmartChannel, info: SwedishRowInfo?, at: Long, play: () -> Unit, modifier: Modifier) {
    SwedishChannelCard(item.label, ChannelQuality.UNKNOWN, info ?: SwedishRowInfo(item.channel.displayLogoUrl, null, null), at,
        play, modifier, compact = true)
}

@Composable
internal fun SmartResumeCard(item: SmartResume, play: () -> Unit, modifier: Modifier) {
    SmartCard(play, modifier.heightIn(min = 100.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
            SmartResumeIdentity(item, 96.mpx)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                TvText(item.seriesTitle ?: item.title, size = 23, bold = true, lines = 1)
                SmartResumeLine(item)
                ProgrammeProgress((item.position.toDouble() / item.duration).toFloat())
            }
        }
    }
}

@Composable
private fun SmartResumeLine(item: SmartResume) {
    val milliseconds = item.duration - item.position
    val remaining = (milliseconds / 60_000 + if (milliseconds % 60_000 > 0) 1 else 0).coerceAtLeast(1)
    val label = if (item.kind == SmartResumeKind.EPISODE) stringResource(R.string.mintv_smart_episode_remaining,
        item.season ?: 0, item.episode ?: 0, remaining) else stringResource(R.string.mintv_smart_remaining, remaining)
    TvText(label, size = 19, color = MinTvMuted, lines = 1)
}

@Composable
private fun SmartCard(play: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Button(play, modifier, contentPadding = PaddingValues(16.mpx),
        shape = ButtonDefaults.shape(RoundedCornerShape(16.mpx)),
        colors = ButtonDefaults.colors(containerColor = Color(0xFF14242E), focusedContainerColor = Color(0xFF29454D)),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.mpx, MinTvTeal))),
        scale = ButtonDefaults.scale(focusedScale = 1f)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.mpx), content = content)
    }
}

/** Semantic media-type badge, never generated or guessed programme artwork. */
@Composable
private fun SmartResumeIdentity(item: SmartResume, size: Dp) {
    Box(Modifier.size(size).background(Color(0xFF29454D), RoundedCornerShape(12.mpx)), contentAlignment = Alignment.Center) {
        tv.own.owntv.ui.components.OwnTVIcon(
            if (item.kind == SmartResumeKind.EPISODE) tv.own.owntv.ui.components.OwnTVIcon.SERIES else tv.own.owntv.ui.components.OwnTVIcon.MOVIES,
            MinTvTeal, Modifier.size(size * .6f))
    }
}
