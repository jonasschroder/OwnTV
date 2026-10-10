package tv.own.owntv.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.*

/** Foreground lifecycle owns requests, never a background job/service; Home/playback stays usable. */
@Composable
internal fun UpdateForegroundHost(homeUsable: Boolean, allowPrompt: Boolean) {
    val manager: MinTvUpdater = koinInject()
    val settings: SettingsRepository = koinInject()
    val automatic by settings.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
    val state by manager.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var dismissedCode by remember { mutableLongStateOf(0) }
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
    if (showDialog && allowPrompt) UpdateDialog(onDismiss = { showDialog = false })
    val available = state as? MinTvUpdater.State.Available
    if (allowPrompt && !showDialog && available != null && available.candidate.code != dismissedCode) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            Column(Modifier.padding(32.mpx).width(510.mpx).stageGlass(24.mpx, overContent = true).padding(22.mpx)) {
                Text(stringResource(R.string.mintv_update_available, available.candidate.version), style = stageText(19, 600), color = StageColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    StageButton(stringResource(R.string.update_now), onClick = { showDialog = true }, height = 48.mpx, textSize = 17, tinted = true)
                    StageButton(stringResource(R.string.update_later), onClick = { dismissedCode = available.candidate.code }, height = 48.mpx, textSize = 17)
                }
            }
        }
    }
}
