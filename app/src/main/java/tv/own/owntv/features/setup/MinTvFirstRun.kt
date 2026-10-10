package tv.own.owntv.features.setup

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import tv.own.owntv.R
import tv.own.owntv.features.profiles.ProfilesViewModel

/** A single notice acknowledgment, not a new locale authority or a PIN bypass. */
@Composable
internal fun MinTvFirstRun(vm: ProfilesViewModel, modifier: Modifier = Modifier) {
    val defaultName = stringResource(R.string.setup_default_profile)
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Box(modifier) { WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.mintv_welcome),
        sub = stringResource(R.string.setup_disclaimer),
        back = null,
        next = WizardAction(stringResource(if (busy) R.string.mintv_companion_loading else R.string.setup_i_understand), {
            if (!busy) { busy = true; vm.createMinTvDefault(defaultName) { busy = false } }
        }, first, enabled = !busy),
        showProgress = false,
    ) }
}

internal fun minTvFirstRunRequired(activeId: Long?, profilesLoaded: Boolean, profileCount: Int): Boolean =
    profilesLoaded && activeId != null && activeId < 0 && profileCount == 0
