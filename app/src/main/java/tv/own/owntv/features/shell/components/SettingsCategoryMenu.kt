package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import tv.own.owntv.R
import tv.own.owntv.features.home.TvText
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.StageColors
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
            val active = index == selected
            val accent = stageAccent.accent
            val radius = 12.mpx
            val title = stringResource(when (group) {
                SettingsGroup.CONTENT -> R.string.v016_category_content
                SettingsGroup.SOUND -> R.string.v016_category_sound
                SettingsGroup.WATCHING -> R.string.v016_category_watching
                else -> group.titleRes
            })
            StageSurface({ activate(index) }, radius, focusStyle = StageFocus.FX,
                idle = Modifier.background(if (active) accent.copy(alpha = .14f) else StageColors.ControlFill, RoundedCornerShape(radius))
                    .border(1.mpx, if (active) accent.copy(alpha = .7f) else StageColors.ControlFill, RoundedCornerShape(radius)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.mpx)
                    .then(if (active) Modifier.focusRequester(target) else Modifier)
                    .onFocusChanged { if (it.isFocused) focused = index }
                    .semantics { this.selected = active }.testTag("settings-group-${group.name}")) { hasFocus ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.mpx, vertical = 12.mpx),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
                    TvText(title, Modifier.weight(1f), size = 22, bold = active || hasFocus, lines = 2)
                    if (active) TvText(stringResource(R.string.v016_category_active), size = 16, color = accent, bold = true, lines = 1)
                }
            }
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
