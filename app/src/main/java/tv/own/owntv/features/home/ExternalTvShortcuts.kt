package tv.own.owntv.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.home.MinTvExternalApps
import tv.own.owntv.ui.theme.mpx

/** Package-scoped launch only; focus never launches or installs anything. */
@Composable
internal fun ExternalTvShortcuts() {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var unavailable by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
        listOf(MinTvExternalApps.SVT_PLAY to R.string.mintv_v0111_svt_play,
            MinTvExternalApps.TELENOR_STREAM to R.string.mintv_v0111_telenor).forEach { (pkg, label) ->
            val launch = remember(pkg, lifecycle) { runCatching { MinTvExternalApps.launch(context, listOf(pkg)) }.getOrNull() }
            val icon = remember(pkg, lifecycle) { if (launch == null) null else runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() }
            HomeSurface({
                val intent = runCatching { MinTvExternalApps.launch(context, listOf(pkg)) ?: MinTvExternalApps.store(context, pkg) }.getOrNull()
                unavailable = intent == null || runCatching { context.startActivity(intent); true }.getOrDefault(false).not()
            }, Modifier.weight(1f).testTag("external-$pkg")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.mpx)) {
                    if (icon != null) AsyncImage(icon, null, Modifier.size(30.mpx))
                    Column(Modifier.weight(1f)) {
                        TvText(stringResource(label), size = 19, bold = true, lines = 2)
                        if (launch == null) TvText(stringResource(R.string.mintv_v0111_open_store), size = 16, color = MinTvMuted, lines = 1)
                    }
                }
            }
        }
    }
    if (unavailable) TvText(stringResource(R.string.mintv_v0111_app_unavailable), size = 17, color = MinTvMuted, lines = 2)
}
