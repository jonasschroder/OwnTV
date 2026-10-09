package tv.own.owntv.features.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Focus policy only: playback remains owned by LiveViewModel/Core. Call on the main thread. */
class HomeLivePreviewController<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> Long,
    private val play: (T) -> Unit,
    private val stop: () -> Unit,
    private val debounceMs: Long = 800L,
) {
    private var pending: Job? = null
    private var selected: T? = null
    private var playingKey: Long? = null
    private var active = false
    private var armed = false
    private var promoting = false

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            armed = false // returning to Home must require a new deliberate remote interaction
            cancelPending()
            if (!promoting) stopOwnedPreview()
        }
    }

    /** Initial/restored focus updates metadata, but never arms autoplay. */
    fun focus(item: T?) {
        val changed = selected?.let(key) != item?.let(key)
        selected = item
        if (changed) {
            cancelPending()
            if (!promoting) stopOwnedPreview() // also cancels Core's pending source resolution
        }
        schedule()
    }

    fun onRemoteNavigation() {
        if (!active || promoting) return
        armed = true
        schedule()
    }

    /** Cancel the delayed request, keeping a matching Exo stream alive for expectPromotion/start. */
    fun beginPromotion() {
        promoting = true
        armed = false
        cancelPending()
    }

    /** Called on fullscreen exit/refusal. Home takes no unmuted detached stream into the background. */
    fun endPromotion() {
        promoting = false
        armed = false
        cancelPending()
        // Fullscreen may have zapped to a different channel, so stop even if no preview was started.
        playingKey = null
        stop()
    }

    fun cancelPendingPromotion() {
        if (promoting) endPromotion()
    }

    private fun schedule() {
        val item = selected ?: return
        if (!active || !armed || promoting || playingKey == key(item) || pending != null) return
        pending = scope.launch {
            delay(debounceMs)
            pending = null
            if (active && armed && !promoting && selected?.let(key) == key(item)) {
                playingKey = key(item)
                play(item)
            }
        }
    }

    private fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    private fun stopOwnedPreview() {
        if (playingKey != null) {
            playingKey = null
            stop()
        }
    }
}
