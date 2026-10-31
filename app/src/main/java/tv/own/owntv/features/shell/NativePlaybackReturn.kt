package tv.own.owntv.features.shell

import androidx.compose.runtime.*
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

/** Keep this state machine outside the very large shell composable's generated register frame. */
internal class NativePlaybackReturnController(owner: NativePlaybackOwner) {
    private var current = owner
    private var origin: NativePlaybackOwner? = null
    var pending by mutableStateOf(false)
        private set
    fun update(owner: NativePlaybackOwner) {
        if (owner != current) { pending = false; origin = null }
        current = owner
    }
    fun opened() { origin = current; pending = false }
    fun closed(fullscreen: Boolean) {
        pending = restoreSportPlaybackFocus(fullscreen, origin, current)
        origin = null
    }
    fun cancel() { pending = false }
}

@Composable
internal fun rememberSportPlaybackReturn(profile: Long?, section: MainSection, presentation: TopDestination?): NativePlaybackReturnController {
    val owner = NativePlaybackOwner(profile, section, presentation)
    val controller = remember { NativePlaybackReturnController(owner) }
    SideEffect { controller.update(owner) }
    return controller
}

/** Start bounded focus attachment only after the content owners have finished hydration.
 * A genuine empty result still supplies an entry and falls back normally; user input cancels pending.
 */
@Composable
internal fun NativePlaybackContentEntry(pending: Boolean, ownerKey: String, enter: (() -> Boolean)?,
    unavailable: () -> Unit, finished: () -> Unit) {
    NativeContentEntry(if (pending && enter != null) 1 else 0, ownerKey,
        { enter?.invoke() == true }, unavailable, finished)
}
