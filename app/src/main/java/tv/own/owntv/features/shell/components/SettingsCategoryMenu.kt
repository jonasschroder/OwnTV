package tv.own.owntv.features.shell.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
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
    val list = rememberLazyListState()
    var focused by remember { mutableIntStateOf(selected) }
    val scope = rememberCoroutineScope()
    fun activate(index: Int) {
        choose(index)
        scope.launch { repeat(2) { withFrameNanos { } }; enter() }
    }
    LaunchedEffect(selected) { list.scrollToItem(selected.coerceIn(SettingsGroup.entries.indices)) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-categories").focusGroup().onPreviewKeyEvent {
        if (it.key == Key.DirectionRight) {
            if (it.type == KeyEventType.KeyDown) { activate(focused) }
            true
        } else false
    }, state = list, verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        itemsIndexed(SettingsGroup.entries) { index, group ->
            StagePill(stringResource(group.titleRes), { activate(index) },
                Modifier.fillMaxWidth().then(if (index == selected) Modifier.focusRequester(target) else Modifier).onFocusChanged { if (it.isFocused) focused = index }.testTag("settings-group-${group.name}"), dimmed = index != selected)
        }
    }
}

@Composable
internal fun ProfileSportSettingsRows() {
    val settings = koinInject<SettingsRepository>()
    val profile by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = null)
    tv.own.owntv.features.home.HockeySettings(profile, Modifier)
}

/** Existing routes are content actions in Settings, not a second horizontal header menu. */
@Composable
internal fun ProfileNavigationRows(switchProfile: (() -> Unit)?, library: (() -> Unit)?, downloads: (() -> Unit)?) {
    val actions = listOf(
        Triple("settings-switch-profile", tv.own.owntv.R.string.mintv_v0112_switch_profile, switchProfile),
        Triple("settings-library-history", tv.own.owntv.R.string.mintv_v0111_library_history, library),
        Triple("settings-downloads", tv.own.owntv.R.string.common_nav_downloads, downloads),
    )
    actions.forEach { (tag, title, action) ->
        if (action != null) tv.own.owntv.features.settings.StageSettingRow(
            icon = tv.own.owntv.ui.components.OwnTVIcon.PERSON,
            title = stringResource(title), desc = null, value = tv.own.owntv.features.settings.SettingValue.Opens(null),
            onClick = action, modifier = Modifier.testTag(tag))
    }
}
