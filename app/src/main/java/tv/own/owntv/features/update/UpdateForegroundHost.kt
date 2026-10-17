package tv.own.owntv.features.update

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository

/** Foreground lifecycle owns requests, never a background job/service; Home/playback stays usable. */
@Composable
internal fun UpdateForegroundHost(homeUsable: Boolean, allowPrompt: Boolean) {
    val manager: MinTvUpdater = koinInject()
    val settings: SettingsRepository = koinInject()
    val automatic by settings.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { manager.setForeground(true); scope.launch { manager.resume() } }
            if (event == Lifecycle.Event.ON_PAUSE) manager.setForeground(false)
        }
        lifecycle.addObserver(observer)
        manager.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); manager.setForeground(false) }
    }
    LaunchedEffect(homeUsable, automatic, lifecycle) {
        if (homeUsable && automatic) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(5_000)
            while (true) { manager.check(); delay(UpdatePolicy.CHECK_INTERVAL) }
        }
    }
    // Verified availability is exposed by the Home header and existing Settings entry.
    // A routine check never adds another focusable overlay or opens a modal over browsing.
}
