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
internal fun rememberHomeDpadNavigation(firstFavorite: FocusRequester): HomeDpadNavigation {
    val focus = remember(firstFavorite) { List(5) { if (it == 1) firstFavorite else FocusRequester() } }
    return rememberSaveable(saver = Saver(save = { it.selected }, restore = { HomeDpadNavigation(focus).apply { selected = it } })) { HomeDpadNavigation(focus) }
}

@Composable
internal fun homeDpadModifier(navigation: HomeDpadNavigation, list: LazyListState, itemIndices: List<Int>): Modifier {
    val scope = rememberCoroutineScope()
    var movement by remember(navigation, list) { mutableStateOf<Job?>(null) }
    return Modifier.onPreviewKeyEvent { event ->
        val delta = when (event.key) { Key.DirectionDown -> 1; Key.DirectionUp -> -1; else -> 0 }
        val target = navigation.selected + delta
        if (delta == 0 || target !in itemIndices.indices) false
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
    information: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {}) {
    TvCard(onOpen, modifier) {
        TvText(androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.mintv_my_sport), size = 26, color = MinTvTeal, bold = true)
        if (hasTeams) information()
        else TvText(androidx.compose.ui.res.stringResource(tv.own.owntv.R.string.mintv_choose_favorite_teams), color = MinTvMuted)
        TvText(androidx.compose.ui.res.stringResource(if (hasTeams) tv.own.owntv.R.string.mintv_matchcenter else tv.own.owntv.R.string.mintv_choose_teams), color = MinTvTeal)
    }
}
