package tv.own.owntv.features.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/** Explicit vertical sections: scroll the target into composition BEFORE requesting focus. */
internal class HomeDpadNavigation(val focus: List<FocusRequester>) {
    var selected by mutableIntStateOf(1)
    fun section(index: Int): Modifier = Modifier.focusRequester(focus[index]).onFocusChanged { if (it.hasFocus) selected = index }
}

@Composable
internal fun rememberHomeDpadNavigation(firstFavorite: FocusRequester, sectionCount: Int = 5, profileKey: Long? = null, firstSection: Int = 1): HomeDpadNavigation {
    val focus = remember(firstFavorite, sectionCount, firstSection) { List(sectionCount) { if (it == firstSection) firstFavorite else FocusRequester() } }
    return rememberSaveable(firstFavorite, sectionCount, profileKey, firstSection,
        saver = Saver(save = { it.selected }, restore = { HomeDpadNavigation(focus).apply { selected = it.coerceIn(focus.indices) } })) { HomeDpadNavigation(focus).apply { selected = firstSection } }
}

/** -1 marks a genuinely absent conditional section, e.g. no real resume history yet. */
internal fun nextHomeSection(selected: Int, delta: Int, itemIndices: List<Int>): Int? {
    if (delta !in listOf(-1, 1)) return null
    val current = itemIndices.getOrNull(selected)?.takeIf { it >= 0 } ?: return null
    val candidates = itemIndices.indices.filter { itemIndices[it] >= 0 &&
        if (delta > 0) itemIndices[it] > current else itemIndices[it] < current }
    val row = (if (delta > 0) candidates.minOfOrNull { itemIndices[it] }
        else candidates.maxOfOrNull { itemIndices[it] }) ?: return null
    return candidates.first { itemIndices[it] == row }
}

@Composable
internal fun homeDpadModifier(navigation: HomeDpadNavigation, list: LazyListState, itemIndices: List<Int>): Modifier {
    val scope = rememberCoroutineScope()
    var movement by remember(navigation, list) { mutableStateOf<Job?>(null) }
    return Modifier.onPreviewKeyEvent { event ->
        val delta = when (event.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> 0 }
        val target = nextHomeSection(navigation.selected, delta, itemIndices)
        if (target == null) false
        else {
            if (event.type == KeyEventType.KeyDown) {
                movement?.cancel()
                movement = scope.launch {
                    try {
                        list.animateScrollToItem(itemIndices[target])
                        withFrameNanos { }; withFrameNanos { }
                        navigation.focus[target].requestSafely()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: IllegalStateException) { /* The list was removed during navigation. */ }
                }
            }
            true // consume key-up too; a single remote press moves exactly one section
        }
    }
}

@Composable
internal fun HomeSportsCard(hasTeams: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier,
    watch: Boolean = false,
    information: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {}) {
    TvCard(onOpen, modifier) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TvText(androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.mintv_my_hockey),
                androidx.compose.ui.Modifier.weight(1f), size = 24, bold = true)
            TvText(androidx.compose.ui.res.stringResource(if (watch) tv.own.owntv.R.string.mintv_watch_match else if (hasTeams) tv.own.owntv.R.string.mintv_my_hockey else tv.own.owntv.R.string.mintv_choose_teams),
                size = 20, color = MinTvTeal, lines = 1)
        }
        if (hasTeams) information()
        else TvText(androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.mintv_choose_favorite_teams), color = MinTvMuted)
    }
}

private class CommittedHomeFocus(var indices: List<Int>, var selected: Int)

/** Compare the last committed layout, before native focus can jump off a removed lazy item. */
@Composable
internal fun ReconcileHomeNavigation(navigation: HomeDpadNavigation, list: LazyListState, indices: List<Int>, active: Boolean) {
    val selected = navigation.selected // subscribe in composition, not only in the deferred effect
    val previous = remember(navigation) { CommittedHomeFocus(indices, selected) }
    val removed = previous.indices.getOrNull(previous.selected)?.let { it >= 0 } == true &&
        indices.getOrNull(previous.selected) == -1
    SideEffect { previous.indices = indices; previous.selected = selected }
    LaunchedEffect(indices, active) {
        if (removed || indices.getOrNull(navigation.selected) == -1) {
            navigation.selected = 0
            if (active) focusPickerItem(list, indices[0], navigation.focus[0], navigation.focus[9])
        }
    }
}
