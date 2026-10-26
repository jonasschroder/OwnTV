package tv.own.owntv.features.home

import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class NativeRow(val id: String, val keys: List<String>, val horizontalRail: Boolean = true)
internal fun nativeRowTarget(rows: List<NativeRow>, key: String?, vertical: Int, column: Int): String? {
    val row = rows.indexOfFirst { key in it.keys }
    if (row < 0 || vertical !in listOf(-1, 1)) return null
    val target = generateSequence(row + vertical) { it + vertical }.takeWhile { it in rows.indices }
        .firstOrNull { rows[it].keys.isNotEmpty() } ?: return null
    return rows[target].keys.getOrNull(column.coerceIn(0, rows[target].keys.lastIndex.coerceAtLeast(0)))
}
internal class NativeRowsNavigation(val list: LazyListState, val rails: Map<String, LazyListState>,
    val modifier: Modifier, val item: (String) -> Modifier)

/** Shared native focus owner for Sport/VOD: stable keys, no nearest-rectangle cross-row jumps. */
@Composable
internal fun rememberNativeRowsNavigation(rows: List<NativeRow>, profile: Long?, first: FocusRequester,
    onEntry: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit): NativeRowsNavigation {
    val list = rememberLazyListState()
    val rails = rows.filter { it.horizontalRail }.associate { row -> row.id to key(row.id) { rememberLazyListState() } }
    val scope = rememberCoroutineScope()
    val targets = remember(profile, first) { mutableMapOf<String, FocusRequester>() }
    var selected by rememberSaveable(profile) { mutableStateOf<String?>(null) }
    var column by rememberSaveable(profile) { mutableIntStateOf(0) }
    var pending by remember(profile) { mutableStateOf<String?>(null) }
    var movement by remember(profile) { mutableStateOf<Job?>(null) }
    val currentRows by rememberUpdatedState(rows)
    val currentRails by rememberUpdatedState(rails)
    val report by rememberUpdatedState(onEntry)
    var ownsFocus by remember(profile) { mutableStateOf(false) }
    var restoreRemoved by remember(profile) { mutableStateOf(false) }
    val firstKey = rows.firstOrNull { it.keys.isNotEmpty() }?.keys?.firstOrNull()
    fun move(target: String): Boolean {
        val row = currentRows.indexOfFirst { target in it.keys }
        if (row < 0) return false
        val targetRow = currentRows[row]
        val rail = currentRails[targetRow.id]
        val col = targetRow.keys.indexOf(target)
        pending = target; movement?.cancel()
        movement = scope.launch {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }
            if (item == null || item.offset < list.layoutInfo.viewportStartOffset || item.offset + item.size > list.layoutInfo.viewportEndOffset) list.scrollToItem(row)
            rail?.let { rail ->
                val visible = rail.layoutInfo.visibleItemsInfo.firstOrNull { it.index == col }
                if (visible == null || visible.offset < rail.layoutInfo.viewportStartOffset || visible.offset + visible.size > rail.layoutInfo.viewportEndOffset) rail.scrollToItem(col)
            }
            repeat(2) { withFrameNanos { } }
            if (currentRows.any { target in it.keys }) targets[target]?.requestSafely()
        }
        return true
    }
    DisposableEffect(profile, first) {
        report { val key = selected?.takeIf { id -> currentRows.any { id in it.keys } } ?: currentRows.firstOrNull { it.keys.isNotEmpty() }?.keys?.firstOrNull(); key?.let(::move) ?: false }
        onDispose { movement?.cancel(); report(null) }
    }
    SideEffect { targets.keys.retainAll(rows.flatMap { it.keys }.toSet()) }
    // Capture ownership before the commit disposes a removed card's focus target. Reading it
    // inside the effect is too late: the empty destination has already cleared focus by then.
    val removedOwnedTarget = ownsFocus && rows.none { selected in it.keys }
    LaunchedEffect(rows, restoreRemoved) {
        if ((removedOwnedTarget || restoreRemoved) && rows.none { selected in it.keys }) {
            val fallback = rows.firstOrNull { it.keys.isNotEmpty() }?.keys?.firstOrNull()
            if (fallback != null) move(fallback) else onHeader()
            restoreRemoved = false
        }
    }
    val modifier = Modifier.onFocusChanged {
        if (!it.hasFocus && ownsFocus && currentRows.none { selected in it.keys }) restoreRemoved = true
        ownsFocus = it.hasFocus
    }.onPreviewKeyEvent { event ->
        val delta = when (event.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> 0 }
        val horizontal = when (event.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
        if (delta == 0 && horizontal == 0) false else {
            if (event.type == KeyEventType.KeyDown) {
                val row = currentRows.firstOrNull { selected in it.keys }
                if (delta != 0) {
                    val target = nativeRowTarget(currentRows, selected, delta, column)
                    if (target != null) move(target) else if (delta < 0 && row == currentRows.firstOrNull { it.keys.isNotEmpty() }) { movement?.cancel(); onHeader() }
                } else if (row != null) {
                    val col = row.keys.indexOf(selected) + horizontal
                    row.keys.getOrNull(col)?.let { column = col; move(it) }
                }
            }
            true
        }
    }
    return NativeRowsNavigation(list, rails, modifier) { id ->
        val requester = if (id == firstKey) first else targets.getOrPut(id) { FocusRequester() }
        targets[id] = requester
        Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) {
            selected = id
            if (pending != id) column = currentRows.firstOrNull { id in it.keys }?.keys?.indexOf(id)?.coerceAtLeast(0) ?: 0
        } }
    }
}
