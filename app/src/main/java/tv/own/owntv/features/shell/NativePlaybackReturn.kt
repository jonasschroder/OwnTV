package tv.own.owntv.features.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import tv.own.owntv.core.nav.MainSection

/** Ephemeral browse owner; never persisted as a preference or logged with profile identifiers. */
internal data class NativePlaybackOwner(val profile: Long?, val section: MainSection, val presentation: TopDestination?)

internal fun restoreSportPlaybackFocus(fullscreen: Boolean, origin: NativePlaybackOwner?, current: NativePlaybackOwner) =
    fullscreen && current.profile != null && current.presentation == TopDestination.SPORT && origin == current

/** Explicit remote browsing always wins over an outstanding post-player focus request. */
internal fun Modifier.cancelPlaybackReturnOnInput(pending: Boolean, cancel: () -> Unit): Modifier = onPreviewKeyEvent {
    if (pending && it.type == KeyEventType.KeyDown) cancel()
    false
}
