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
