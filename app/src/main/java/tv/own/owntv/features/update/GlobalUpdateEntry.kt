package tv.own.owntv.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.features.home.TvText
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.*

/** One route in the existing top bar. No focus-stealing availability prompt or automatic installation. */
@Composable
internal fun GlobalUpdateEntry() {
    val manager: MinTvUpdater = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    UpdateEntryPresentation(state, manager.channelLabel, { open = true }, Modifier.focusRequester(focus))
    if (open) UpdateDialog(onDismiss = {
        open = false
        scope.launch { repeat(2) { withFrameNanos { } }; runCatching { focus.requestFocus() } }
    }, checkOnOpen = true)
}

@Composable
internal fun UpdateEntryPresentation(state: MinTvUpdater.State, channel: String, action: () -> Unit, modifier: Modifier = Modifier) {
    val available = state is MinTvUpdater.State.Available || state == MinTvUpdater.State.Permission || state == MinTvUpdater.State.Confirmation
    val label = when {
        available -> stringResource(R.string.v016_update_entry_available)
        state is MinTvUpdater.State.Downloading -> stringResource(R.string.v016_update_entry_progress, state.percent)
        state == MinTvUpdater.State.Verifying || state == MinTvUpdater.State.Installing -> stringResource(R.string.v016_update_entry_working)
        state == MinTvUpdater.State.Installed -> stringResource(R.string.v016_update_entry_installed)
        else -> stringResource(R.string.mintv_v08_version_chip, BuildConfig.VERSION_NAME)
    }
    val description = stringResource(R.string.v016_update_entry_description, channel, label)
    StageSurface(action, 12.mpx, modifier.height(44.mpx).widthIn(max = 220.mpx)
        .testTag("header-update").semantics { contentDescription = description }) { focused ->
        TvText(label, size = 16, color = if (focused) stageAccent.onAccent else if (available) stageAccent.accent else StageColors.Text,
            lines = 1, modifier = Modifier.padding(horizontal = 10.mpx))
    }
}
