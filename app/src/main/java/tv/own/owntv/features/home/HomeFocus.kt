package tv.own.owntv.features.home

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

/** Lazy targets may be detached after Back/profile/load; refusal is not a successful focus move. */
internal fun FocusRequester.requestSafely(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    SportsDiagnostics.record(SportsDiagnostics.Event.DETACHED_FOCUS)
    false
}

/** Compose the requested lazy row before asking for remote focus, including off-screen rows. */
internal suspend fun focusPickerItem(list: LazyListState, index: Int, target: FocusRequester,
    fallback: FocusRequester) {
    list.scrollToItem(index.coerceAtLeast(0))
    withFrameNanos { }; withFrameNanos { }
    if (!target.requestSafely()) fallback.requestSafely()
}
