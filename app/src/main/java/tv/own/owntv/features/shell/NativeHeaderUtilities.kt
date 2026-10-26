package tv.own.owntv.features.shell

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.features.epg.*
import tv.own.owntv.features.home.TvText
import tv.own.owntv.ui.stage.StagePill
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/** One utility surface for existing routes, never another destination selector. */
@Composable
internal fun NativeHeaderUtilities(epg: EpgViewModel, profile: Long?, destination: TopDestination,
    guide: () -> Unit, search: () -> Unit, tvTools: List<() -> Unit>, library: () -> Unit,
    downloads: () -> Unit, header: () -> Unit, entry: FocusRequester, enterContent: () -> Unit, profileName: String, switchProfile: () -> Unit) {
    val observed by epg.manualRefresh.collectAsStateWithLifecycle()
    val status = observed.takeIf { it.profile == profile }
    val time = rememberSystemTimeFormatter()
    var ownsFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = ownsFocus) { header() }
    Row(Modifier.fillMaxWidth().heightIn(min = 42.mpx).padding(horizontal = 40.mpx)
        .focusRequester(entry).onFocusChanged { ownsFocus = it.hasFocus }.onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionUp -> { if (event.type == KeyEventType.KeyDown) header(); true }
                Key.DirectionDown -> { if (event.type == KeyEventType.KeyDown) enterContent(); true }
                else -> false
            }
        }.focusGroup(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            StagePill(stringResource(if (status?.result == ManualEpgResult.RUNNING) R.string.mintv_v0111_epg_running else R.string.mintv_v0111_epg_refresh), { epg.refreshManually() }, Modifier.testTag("header-epg-refresh"))
            if (destination == TopDestination.TV) {
                StagePill(stringResource(R.string.common_nav_guide), guide, Modifier.testTag("header-guide"))
                StagePill(stringResource(R.string.common_nav_search), search, Modifier.testTag("header-search"))
                tvTools.getOrNull(0)?.let { StagePill(stringResource(R.string.mintv_full_library), it, Modifier.testTag("header-all-channels")) }
                tvTools.getOrNull(1)?.let { StagePill(stringResource(R.string.mintv_collection_settings), it, Modifier.testTag("header-collection")) }
            }
            if (destination == TopDestination.PROFILE) {
                StagePill(stringResource(R.string.mintv_v0111_profile_switch, profileName), switchProfile, Modifier.testTag("header-switch-profile"))
                StagePill(stringResource(R.string.mintv_v0111_library_history), library, Modifier.testTag("header-library-history"))
                StagePill(stringResource(R.string.common_nav_downloads), downloads, Modifier.testTag("header-downloads"))
            }
            when (status?.result) {
                ManualEpgResult.SUCCESS -> TvText(stringResource(R.string.mintv_v0111_epg_done, time(status.lastSuccess ?: 0)), size = 16, lines = 1)
                ManualEpgResult.FAILED -> TvText(stringResource(R.string.mintv_v0111_epg_failed), size = 16, lines = 1)
                ManualEpgResult.EMPTY -> TvText(stringResource(R.string.mintv_v0111_epg_empty), size = 16, lines = 1)
                else -> Unit
            }
        }
        TvText(stringResource(R.string.mintv_v08_version_chip, BuildConfig.VERSION_NAME), size = 16, lines = 1)
    }
}
