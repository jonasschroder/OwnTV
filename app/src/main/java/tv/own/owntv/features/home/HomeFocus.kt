package tv.own.owntv.features.home

import androidx.compose.ui.focus.FocusRequester

/** Lazy targets may be detached after Back/profile/load; refusal is not a successful focus move. */
internal fun FocusRequester.requestSafely(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    false
}
