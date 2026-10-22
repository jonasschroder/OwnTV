package tv.own.owntv.features.live.source

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Opt-in only for logical TV actions; VOD and programme context-menu handlers stay unchanged. */
internal fun Modifier.logicalChannelKeys(onClick: () -> Unit, onLong: (Long) -> Unit,
    cancellation: SourcePressCancellation? = null): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val held by rememberUpdatedState(onLong)
    val state = remember(cancellation) { RemoteOkPress() }
    val identity = remember(cancellation) { Any() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var timer by remember(cancellation) { mutableStateOf<Runnable?>(null) }
    val owner = LocalLifecycleOwner.current
    fun cancel() { cancellation?.clear(identity); timer?.let(handler::removeCallbacks); timer = null; state.cancel() }
    DisposableEffect(owner, cancellation) {
        val observer = LifecycleEventObserver { _, event -> if (event != Lifecycle.Event.ON_RESUME) cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); cancel() }
    }
    onFocusChanged { if (!it.isFocused) cancel() }.onPreviewKeyEvent { event ->
        if (event.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
            cancel(); false
        } else {
            val native = event.nativeKeyEvent
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                when (event.type) {
                    KeyEventType.KeyDown -> if (state.down(native.downTime, native.repeatCount)) {
                        cancellation?.register(identity, ::cancel)
                        timer?.let(handler::removeCallbacks)
                        val downTime = native.downTime // KeyEvent objects are pooled after dispatch.
                        timer = Runnable { if (state.held(downTime)) held(downTime) }
                            .also { handler.postDelayed(it, 500L) }
                    }
                    KeyEventType.KeyUp -> {
                        timer?.let(handler::removeCallbacks); timer = null
                        if (native.isCanceled) state.cancel()
                        // A busy UI thread may deliver KeyUp before the pending timer callback.
                        if (!native.isCanceled && native.eventTime - native.downTime >= 500L && state.held(native.downTime)) held(native.downTime)
                        else if (state.up(native.downTime)) click()
                    }
                }
            } else cancel()
            true
        }
    }
}

/** Consume every event belonging to the opening long press, even when its KeyUp was lost.
 * The next deliberate press has a new downTime, so it never needs an extra sacrificial OK.
 */
internal fun Modifier.sourcePickerOpeningGuard(openingDownTime: Long?) = onPreviewKeyEvent { event ->
    event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) &&
        openingDownTime != null && event.nativeKeyEvent.downTime == openingDownTime
}
