package tv.own.owntv.features.shell.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.theme.mpx

/** Existing ordinal deep links stay stable; one visible category rail, no More > Settings stack. */
@Composable
internal fun SettingsCategoryMenu(selected: Int, choose: (Int) -> Unit, enter: () -> Unit, target: FocusRequester) {
    LazyColumn(Modifier.fillMaxSize().focusRequester(target).focusGroup().onPreviewKeyEvent {
        if (it.key == Key.DirectionRight) { if (it.type == KeyEventType.KeyDown) enter(); true } else false
    }, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        itemsIndexed(SettingsGroup.entries) { index, group ->
            StagePill(stringResource(group.titleRes), { choose(index) },
                Modifier.fillMaxWidth().testTag("settings-group-${group.name}"), dimmed = index != selected)
        }
    }
}

@Composable
internal fun ProfileSportSettingsRows() {
    val settings = koinInject<SettingsRepository>()
    val profile by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = null)
    tv.own.owntv.features.home.HockeySettings(profile, Modifier)
}
