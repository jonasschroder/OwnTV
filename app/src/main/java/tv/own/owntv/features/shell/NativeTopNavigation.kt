package tv.own.owntv.features.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.theme.*

/** One stable native header. Focus never activates a page, starts a fetch or mounts a decoder. */
@Composable
internal fun NativeTopNavigation(
    selected: TopDestination,
    selectedFocus: FocusRequester,
    onSelect: (TopDestination) -> Unit,
    onEnterContent: (TopDestination) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onUtilities: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(selected) }
    val targets = remember { TopDestination.entries.associateWith { FocusRequester() } }
    val labels = mapOf(TopDestination.HOME to R.string.mintv_nav_home,
        TopDestination.TV to R.string.mintv_nav_tv, TopDestination.SPORT to R.string.mintv_nav_sport,
        TopDestination.VOD to R.string.mintv_nav_vod, TopDestination.PROFILE to R.string.mintv_nav_profile)
    BoxWithConstraints(modifier.fillMaxWidth().height(100.mpx * maxOf(1f, LocalDensity.current.fontScale)).background(OwnTVTheme.colors.background)) {
        val brand = maxWidth > 850.dp
        Row(Modifier.fillMaxSize().padding(horizontal = 32.mpx, vertical = 16.mpx)
            .focusRequester(selectedFocus)
            .focusProperties { onEnter = { targets.getValue(selected).requestFocus() } }
            .onFocusChanged { if (it.hasFocus) onFocused() }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionRight && focused == TopDestination.PROFILE && onUtilities != null) {
                    if (event.type == KeyEventType.KeyDown) onUtilities()
                    true
                } else if (event.key == Key.DirectionDown) {
                    if (event.type == KeyEventType.KeyDown) onEnterContent(focused)
                    true
                } else false
            }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
            if (brand) Text(stringResource(R.string.app_name), style = stageText(26, 700),
                color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 22.mpx))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
                TopDestination.entries.filter { it != TopDestination.PROFILE }.forEach { destination ->
                    NativeTopItem(destination, stringResource(labels.getValue(destination)), selected,
                        targets.getValue(destination), onSelect) { focused = destination }
                }
            }
            NativeTopItem(TopDestination.PROFILE, stringResource(labels.getValue(TopDestination.PROFILE)),
                selected, targets.getValue(TopDestination.PROFILE), onSelect) { focused = TopDestination.PROFILE }

        }
    }
}

@Composable
private fun NativeTopItem(destination: TopDestination, label: String, active: TopDestination,
    target: FocusRequester, onSelect: (TopDestination) -> Unit, onFocused: () -> Unit) {
    Column(Modifier.width(IntrinsicSize.Max)) {
        StageSurface({ onSelect(destination) }, 14.mpx,
            Modifier.height(56.mpx * maxOf(1f, LocalDensity.current.fontScale)).focusRequester(target)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .testTag("top-${destination.name}").semantics { selected = destination == active },
            focusStyle = StageFocus.FX) { focused ->
            Text(label, style = stageText(if (destination == TopDestination.PROFILE) 22 else 26, 700),
                color = if (focused || active == destination) stageAccent.accent else StageColors.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 22.mpx))
        }
        Box(Modifier.height(3.mpx).fillMaxWidth()
            .background(if (active == destination) stageAccent.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}

/** Aliases to existing screen owners; OK-only activation and no new preference store. */
@Composable
internal fun NativeSubNavigation(routes: List<MainSection>, selected: MainSection,
    onSelect: (MainSection) -> Unit, onSearch: (() -> Unit)?, onHeader: () -> Unit,
    onEnterContent: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier, onNowPlaying: (() -> Unit)? = null, entryFocusRequester: FocusRequester) {
    val targets = remember(routes) { routes.associateWith { FocusRequester() } }
    Row(modifier.fillMaxWidth().height(64.mpx).focusRequester(entryFocusRequester)
        .focusProperties { onEnter = { (targets[selected] ?: targets.values.firstOrNull())?.requestFocus() } }.padding(horizontal = 40.mpx)
        .horizontalScroll(rememberScrollState()).onFocusChanged { if (it.hasFocus) onFocused() }
        .onPreviewKeyEvent { event ->
            when(event.key) {
                Key.DirectionUp -> { if (event.type == KeyEventType.KeyDown) onHeader(); true }
                Key.DirectionDown -> { if (event.type == KeyEventType.KeyDown) onEnterContent(); true }
                else -> false
            }
        }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
        routes.forEach { route ->
            StagePill(stringResource(when (route) {
                MainSection.LIVE_TV -> R.string.mintv_swedish_channels
                MainSection.MORE -> R.string.mintv_nav_library
                else -> route.labelRes
            }), { onSelect(route) },
                Modifier.focusRequester(targets.getValue(route)).testTag("sub-${route.name}").semantics { this.selected = route == selected },
                dimmed = route != selected)
        }
        if (onNowPlaying != null) StagePill(stringResource(R.string.shell_now_playing), onNowPlaying)
        if (onSearch != null) StagePill(stringResource(R.string.common_nav_search), onSearch,
            Modifier.testTag("nav-context-search"))
    }
}
