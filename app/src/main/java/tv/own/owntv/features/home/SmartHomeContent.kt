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
import tv.own.owntv.features.live.SwedishRowInfo
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.mpx

internal data class SmartChannel(val key: String, val label: String, val channel: ChannelEntity)
internal data class SmartHomeSnapshot(val profile: Long, val hasSources: Boolean,
    val channels: List<SmartChannel>, val live: ChannelEntity?, val liveWatchedAt: Long?,
    val heroResume: SmartResume?, val resumes: List<SmartResume>)

/** Native Din kväll composition; only metadata updates during a visit, never membership/order. */
@Composable
internal fun SmartHomeContent(snapshot: SmartHomeSnapshot?, guide: HomeGuideState, profile: Long?,
    onLive: (ChannelEntity) -> Unit, onResume: (SmartResume) -> Unit, onEpisodes: (Long) -> Unit,
    onTv: () -> Unit, onGuide: () -> Unit, onSources: () -> Unit,
    firstFocus: FocusRequester, onEntry: ((() -> Boolean)?) -> Unit,
    onFocused: () -> Unit, restore: Boolean, onRestored: () -> Unit,
    contentStart: Dp, reservedTop: Dp, modifier: Modifier = Modifier,
    onVisibleChannels: (List<ChannelEntity>) -> Unit = {}, onHeader: () -> Unit) {
    val current = snapshot?.takeIf { it.profile == profile }
    val channels = current?.channels.orEmpty()
    val resumes = current?.resumes.orEmpty()
    val list = rememberLazyListState()
    val rail = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nav = rememberHomeDpadNavigation(firstFocus, 3, profile, firstSection = 0)
    var channelKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var resumeKey by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    val keys = channels.map { it.key }
    val resumeKeys = resumes.map { it.key }
    var previousKeys by remember(profile) { mutableStateOf(keys) }
    var previousResumes by remember(profile) { mutableStateOf(resumeKeys) }
    val entryChannel = smartFocusKey(channelKey, previousKeys, keys)
    val entryResume = smartFocusKey(resumeKey, previousResumes, resumeKeys)
    SideEffect { if (current != null) { previousKeys = keys; previousResumes = resumeKeys } }
    val indices = listOf(0, if (channels.isEmpty()) -1 else 1,
        if (resumes.isEmpty()) -1 else if (channels.isEmpty()) 1 else 2)
    LaunchedEffect(indices, current != null) {
        if (current != null && indices.getOrNull(nav.selected) == -1) {
            nav.selected = 0; list.scrollToItem(0)
            repeat(2) { withFrameNanos { } }; firstFocus.requestSafely()
        }
    }
    val currentIndices by rememberUpdatedState(indices)
    val currentChannels by rememberUpdatedState(channels)
    val currentChannelKey by rememberUpdatedState(entryChannel)
    fun enter(): Boolean {
        scope.launch {
            val section = nav.selected.takeIf { currentIndices.getOrNull(it) != -1 } ?: 0
            val target = currentIndices[section].coerceAtLeast(0)
            val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            if (visible == null || visible.offset < list.layoutInfo.viewportStartOffset || visible.offset + visible.size > list.layoutInfo.viewportEndOffset)
                list.scrollToItem(target)
            if (section == 1) rail.scrollToItem(currentChannels.indexOfFirst { it.key == currentChannelKey }.coerceAtLeast(0))
            repeat(2) { withFrameNanos { } }
            if (!nav.focus[section].requestSafely()) { list.scrollToItem(0); withFrameNanos { }; firstFocus.requestSafely() }
        }
        return true
    }
    DisposableEffect(firstFocus, profile) { onEntry { enter() }; onDispose { onEntry(null) } }
    LaunchedEffect(restore, current) { if (restore && current != null) { enter(); onRestored() } }
    val visible by remember(channels) { derivedStateOf {
        val indices = rail.layoutInfo.visibleItemsInfo.map { it.index }
        (indices + ((indices.lastOrNull() ?: -1) + 1..(indices.lastOrNull() ?: -1) + 2))
            .distinct().mapNotNull { channels.getOrNull(it)?.channel }
    } }
    LaunchedEffect(visible) { onVisibleChannels(visible) }
    BoxWithConstraints(modifier.fillMaxSize().background(MinTvNavy)) {
        val width = ((maxWidth - contentStart - 64.mpx - 10.mpx - 16.mpx * 7) / 8).coerceAtLeast(140.mpx)
        LazyColumn(state = list, contentPadding = PaddingValues(start = contentStart, end = 64.mpx, bottom = 36.mpx),
            verticalArrangement = Arrangement.spacedBy(16.mpx),
            modifier = Modifier.fillMaxSize().padding(top = reservedTop).clip(androidx.compose.ui.graphics.RectangleShape)
                .onPreviewKeyEvent { event ->
                    val delta = when (event.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> 0 }
                    val target = nextHomeSection(nav.selected, delta, indices)
                    if (delta == -1 && nav.selected == 0) {
                        if (event.type == KeyEventType.KeyDown) onHeader()
                        true
                    } else if (target == null) false else {
                        if (event.type == KeyEventType.KeyDown) scope.launch {
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == indices[target] }
                            if (item == null || item.offset < list.layoutInfo.viewportStartOffset || item.offset + item.size > list.layoutInfo.viewportEndOffset)
                                list.animateScrollToItem(indices[target])
                            repeat(2) { withFrameNanos { } }
                            nav.focus[target].requestSafely()
                        }
                        true
                    }
                }.onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup()) {
            item(key = "smart-hero") {
                SmartHomeHero(current, guide, onLive, onResume, onEpisodes, onTv, onGuide, onSources,
                    nav.section(0))
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
                                    .testTag("smart-channel-${item.key}").onFocusChanged { if (it.isFocused) channelKey = item.key })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SmartAction(stringResource(R.string.mintv_smart_previous), {
                            scope.launch {
                                val next = (rail.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                channelKey = channels[next].key; rail.animateScrollToItem(next)
                                repeat(2) { withFrameNanos { } }; nav.focus[1].requestSafely()
                            }
                        }, compact = true)
                        SmartAction(stringResource(R.string.mintv_smart_more_channels), {
                            scope.launch {
                                val next = (rail.firstVisibleItemIndex + 1).coerceAtMost(channels.lastIndex)
                                channelKey = channels[next].key; rail.animateScrollToItem(next)
                                repeat(2) { withFrameNanos { } }; nav.focus[1].requestSafely()
                            }
                        }, compact = true)
                    }
                }
            }
            if (resumes.isNotEmpty()) item(key = "smart-resume") {
                Column(Modifier.onFocusChanged { if (it.hasFocus) nav.selected = 2 }.focusGroup(), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                    TvText(stringResource(R.string.search_continue), size = 27, bold = true, lines = 1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                        resumes.take(3).forEach { item ->
                            SmartResumeCard(item, { onResume(item) }, Modifier.weight(1f)
                                .then(if (item.key == entryResume) nav.section(2) else Modifier)
                                .testTag("smart-resume-${item.key}").onFocusChanged { if (it.isFocused) resumeKey = item.key })
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
    Button(onClick, modifier.heightIn(min = (if (compact) 32 else 48).mpx),
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
    onTv: () -> Unit, onGuide: () -> Unit, onSources: () -> Unit, first: Modifier) {
    val live = snapshot?.live
    val resume = snapshot?.heroResume
    val info = live?.let { guide.rows[it.homeEpgKey()]?.at(guide.at) }
    val time = rememberSystemTimeFormatter()
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    Row(Modifier.fillMaxWidth().heightIn(min = 302.mpx * scale).clip(RoundedCornerShape(24.mpx))
        .background(Color(0xFF182D34)).testTag("smart-hero"), horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
        Column(Modifier.weight(1.6f).padding(30.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
            TvText(stringResource(when {
                resume?.kind == SmartResumeKind.EPISODE -> R.string.mintv_smart_continue_series
                resume != null -> R.string.search_continue
                snapshot?.liveWatchedAt != null -> R.string.mintv_smart_back_channel
                else -> R.string.mintv_smart_evening
            }), size = 18, color = MinTvTeal, bold = true, lines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                if (resume == null && live != null) HomeChannelLogo(live.name, info?.logo ?: live.logoUrl, Modifier.size(64.mpx))
                Column(Modifier.weight(1f)) {
                    (resume?.seriesTitle ?: live?.name)?.let { TvText(it, size = 21, color = MinTvMuted, lines = 1) }
                    TvText(resume?.title ?: info?.now?.title ?: stringResource(if (live != null) R.string.mintv_v08_empty_now
                        else if (snapshot == null) R.string.mintv_smart_loading else R.string.mintv_smart_start), size = 36, bold = true, lines = 2)
                }
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
                    first.testTag("smart-hero-play"), primary = true)
                if (resume?.seriesId != null) SmartAction(stringResource(R.string.mintv_smart_episodes), { onEpisodes(resume.seriesId) })
                else if (live != null) SmartAction(stringResource(R.string.mintv_guide), onGuide)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = .025f)).padding(30.mpx),
            verticalArrangement = Arrangement.spacedBy(14.mpx)) {
            TvText(stringResource(if (resume != null && live != null) R.string.mintv_smart_last_tv else R.string.mintv_smart_next),
                size = 18, bold = true, color = MinTvTeal, lines = 1)
            TvText(if (resume != null) live?.name ?: resume.seriesTitle ?: resume.title
                else info?.next?.title ?: stringResource(R.string.mintv_v08_empty_now), size = 30, bold = true, lines = 2)
            if (resume != null && live != null) SmartAction(stringResource(R.string.mintv_watch_channel), { onLive(live) })
            else if (resume == null) {
                info?.next?.let { TvText(stringResource(R.string.mintv_v08_time_range, time(it.startMs), time(it.stopMs)), size = 22, color = MinTvMuted, lines = 1) }
                SmartAction(stringResource(R.string.mintv_guide), onGuide)
            }
        }
    }
}

@Composable
private fun SmartChannelCard(item: SmartChannel, info: SwedishRowInfo?, at: Long, play: () -> Unit, modifier: Modifier) {
    val current = info?.at(at)?.now
    val time = rememberSystemTimeFormatter()
    SmartCard(play, modifier.heightIn(min = 193.mpx)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.mpx)) {
            HomeChannelLogo(item.label, info?.logo ?: item.channel.logoUrl, Modifier.size(32.mpx))
            TvText(item.label, Modifier.weight(1f), size = 22, bold = true, lines = 2)
        }
        TvText(current?.title ?: stringResource(R.string.mintv_v08_empty_now), size = 22, lines = 2)
        Spacer(Modifier.weight(1f))
        current?.let {
            ProgrammeProgress(it.progress(at))
            TvText(stringResource(R.string.mintv_v08_time_range, time(it.startMs), time(it.stopMs)), size = 18, color = MinTvMuted, lines = 1)
        }
    }
}

@Composable
private fun SmartResumeCard(item: SmartResume, play: () -> Unit, modifier: Modifier) {
    SmartCard(play, modifier.heightIn(min = 100.mpx)) {
        TvText(item.seriesTitle ?: item.title, size = 23, bold = true, lines = 1)
        SmartResumeLine(item)
        ProgrammeProgress((item.position.toDouble() / item.duration).toFloat())
    }
}

@Composable
private fun SmartResumeLine(item: SmartResume) {
    val remaining = ((item.duration - item.position + 59_999) / 60_000).coerceAtLeast(1)
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
