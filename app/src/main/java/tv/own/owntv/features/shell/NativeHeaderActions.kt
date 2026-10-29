package tv.own.owntv.features.shell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.R
import tv.own.owntv.features.epg.*
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.*
import tv.own.owntv.ui.format.rememberSystemTimeFormatter

/** Compact children of the global bar, never a separate navigation row. */
@Composable
internal fun NativeHeaderActions(epg: EpgViewModel, profile: Long?, guide: () -> Unit,
    search: () -> Unit, nowPlaying: (() -> Unit)? = null) {
    val observed by epg.manualRefresh.collectAsStateWithLifecycle()
    val status = observed.takeIf { it.profile == profile }
    val time = rememberSystemTimeFormatter()
    val refreshLabel = stringResource(when (status?.result) {
        ManualEpgResult.RUNNING -> R.string.mintv_v0111_epg_running
        ManualEpgResult.FAILED -> R.string.mintv_v0111_epg_failed
        ManualEpgResult.EMPTY -> R.string.mintv_v0111_epg_empty
        else -> R.string.mintv_v0111_epg_refresh
    })
    val refreshDescription = if (status?.result == ManualEpgResult.SUCCESS)
        stringResource(R.string.mintv_v0111_epg_done, time(status.lastSuccess ?: 0)) else refreshLabel
    HeaderAction(OwnTVIcon.REFRESH, refreshDescription, "header-epg-refresh") { epg.refreshManually() }
    HeaderAction(OwnTVIcon.EPG, stringResource(R.string.common_nav_guide), "header-guide", guide)
    HeaderAction(OwnTVIcon.SEARCH, stringResource(R.string.common_nav_search), "header-search", search)
    if (nowPlaying != null) HeaderAction(OwnTVIcon.PLAY, stringResource(R.string.shell_now_playing), "header-now-playing", nowPlaying)
    tv.own.owntv.features.update.GlobalUpdateEntry()
}

@Composable
private fun HeaderAction(icon: OwnTVIcon, label: String, tag: String, action: () -> Unit) {
    StageSurface(action, 12.mpx, Modifier.size(44.mpx).testTag(tag).semantics { contentDescription = label }) { focused ->
        OwnTVIcon(icon, if (focused) stageAccent.accent else StageColors.Text,
            Modifier.size(26.mpx).align(androidx.compose.ui.Alignment.Center))
    }
}
