package tv.own.owntv.features.shell

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*

internal val LocalNativeTopNavigation = staticCompositionLocalOf { false }

/** Child controls handle first: especially EPG timeline Left/Right and popup/dialog navigation. */
internal fun Modifier.nativeContentNavigation(header: FocusRequester, manager: FocusManager): Modifier =
    onKeyEvent { event ->
        if (event.key != Key.DirectionUp || event.type != KeyEventType.KeyDown) false
        else {
            if (!manager.moveFocus(FocusDirection.Up)) runCatching { header.requestFocus() }
            true
        }
    }.focusProperties { onExit = {
        if (requestedFocusDirection == FocusDirection.Up) {
            // requestFocus redirects this transition. Cancelling afterwards would cancel that redirect.
            header.requestFocus()
        }
    } }

/** Never contains credentials or content IDs from a different profile. Not persisted in preferences. */
internal fun nativePageKey(profileId: Long?, section: tv.own.owntv.core.nav.MainSection,
    presentation: TopDestination?): String = "${profileId}:${section.name}:${presentation?.name.orEmpty()}"

/** Defer entry until the selected page has composed; never invoke the outgoing page's hook.
 * Bounded retries allow asynchronous/lazy controls to attach. Profile/page changes cancel the job.
 */
@androidx.compose.runtime.Composable
internal fun NativeContentEntry(request: Int, pageKey: String, enter: () -> Boolean, unavailable: () -> Unit, finished: () -> Unit = {}) {
    val currentEnter = androidx.compose.runtime.rememberUpdatedState(enter)
    val currentFinished = androidx.compose.runtime.rememberUpdatedState(finished)
    val currentUnavailable = androidx.compose.runtime.rememberUpdatedState(unavailable)
    androidx.compose.runtime.LaunchedEffect(request, pageKey) {
        if (request == 0) return@LaunchedEffect
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        repeat(30) {
            if (currentEnter.value()) { currentFinished.value(); return@LaunchedEffect }
            androidx.compose.runtime.withFrameNanos { }
        }
        currentUnavailable.value()
        currentFinished.value()
    }
}

/** Register once per presentation, while the action reads the latest asynchronous page state. */
@androidx.compose.runtime.Composable
internal fun NativeContentEntryHook(report: (((() -> Boolean)?) -> Unit)?, owner: Any?, action: () -> Boolean) {
    val currentReport = androidx.compose.runtime.rememberUpdatedState(report)
    val currentAction = androidx.compose.runtime.rememberUpdatedState(action)
    androidx.compose.runtime.DisposableEffect(owner) {
        currentReport.value?.invoke { currentAction.value() }
        onDispose { currentReport.value?.invoke(null) }
    }
}
