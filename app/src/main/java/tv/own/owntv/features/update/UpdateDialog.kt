package tv.own.owntv.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.*
import java.text.DateFormat
import java.util.Date

/** About + update UI uses the host adapter exclusively; the Core legacy selector stays disabled. */
@Composable
fun UpdateDialog(onDismiss: () -> Unit, checkOnOpen: Boolean = false) {
    val manager: MinTvUpdater = koinInject()
    val settings: SettingsRepository = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val automatic by settings.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
    val checked by settings.lastUpdateCheckAt.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var requestedInstall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { manager.resume(); if (checkOnOpen) manager.requestCheck() }
    LaunchedEffect(state) {
        // Only a live, explicit Update action can open the system prompt automatically.
        // After process recreation the durable Continue button requires another user action.
        if (state == MinTvUpdater.State.Confirmation && requestedInstall) {
            requestedInstall = false
            manager.openInstallerScreen(manager.confirmationIntent())
        }
    }
    // Closing presentation must not reset verified state or cancel an in-app download.
    UpdatePresentation(state, manager.channelLabel, automatic, checked,
        onDismiss = onDismiss,
        onAutomatic = { scope.launch { settings.setUpdateCheckOnStart(!automatic) } },
        onAction = {
            when (state) {
                is MinTvUpdater.State.Available -> { requestedInstall = true; manager.requestDownloadOrInstall() }
                MinTvUpdater.State.Permission -> manager.openInstallerScreen(manager.permissionIntent())
                MinTvUpdater.State.Confirmation -> manager.openInstallerScreen(manager.confirmationIntent())
                else -> manager.requestCheck()
            }
        })
}

/** Same production dialog for verified manager state and explicit synthetic screenshot fixtures.
 * Available notes can only be supplied by the manager after UpdatePolicy.authenticate succeeds.
 */
@Composable
internal fun UpdatePresentation(state: MinTvUpdater.State, channel: String, automatic: Boolean, checked: Long?,
    onDismiss: () -> Unit, onAutomatic: () -> Unit, onAction: () -> Unit) {
    val first = remember { FocusRequester() }
    val busy = state == MinTvUpdater.State.Checking || state is MinTvUpdater.State.Downloading ||
        state == MinTvUpdater.State.Verifying || state == MinTvUpdater.State.Installing
    StagePopup(onDismiss = onDismiss, title = stringResource(R.string.mintv_update_about), width = 1080.mpx,
        modifier = Modifier.focusProperties { onEnter = { first.requestFocus() } }.focusGroup(),
        buttons = {
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestFocus() }
            StageButton(stringResource(R.string.settings_close), onClick = onDismiss,
                modifier = Modifier.focusRequester(first), height = 56.mpx, textSize = 18)
            if (!busy) StageButton(stringResource(when (state) {
                is MinTvUpdater.State.Available -> R.string.update_now
                MinTvUpdater.State.Permission -> R.string.mintv_update_permission_action
                MinTvUpdater.State.Confirmation -> R.string.mintv_update_confirm
                else -> R.string.settings_check_updates
            }), onClick = onAction, height = 56.mpx, textSize = 18, tinted = true)
        }) {
        Text(stringResource(R.string.app_name), style = stageText(25, 700), color = StageColors.Text)
        Text(stringResource(R.string.mintv_v08_version_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()),
            style = stageText(19, 600), color = StageColors.Text)
        Text(channel, style = stageText(17, 600), color = StageSurfaces.Cyan)
        (state as? MinTvUpdater.State.Available)?.candidate?.let { candidate ->
            Text(stringResource(R.string.mintv_v08_version_target, candidate.version, candidate.code.toString()), style = stageText(21, 600), color = StageColors.Text)
        }
        val date = checked?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
            ?: stringResource(R.string.mintv_update_never)
        Text(stringResource(R.string.mintv_update_last_check, date), style = stageText(16, 400), color = StageColors.Muted)
        Spacer(Modifier.height(18.mpx))
        val text = when (val current = state) {
            MinTvUpdater.State.Idle -> stringResource(R.string.mintv_v08_not_checked)
            MinTvUpdater.State.Checking -> stringResource(R.string.mintv_v08_checking_hint)
            MinTvUpdater.State.UpToDate -> stringResource(R.string.update_latest, BuildConfig.VERSION_NAME)
            MinTvUpdater.State.Installed -> stringResource(R.string.mintv_v08_installed)
            is MinTvUpdater.State.Available -> stringResource(R.string.mintv_update_available, current.candidate.version)
            is MinTvUpdater.State.Downloading -> stringResource(R.string.update_downloading, current.percent)
            MinTvUpdater.State.Verifying -> stringResource(R.string.mintv_v08_verifying)
            MinTvUpdater.State.Permission -> stringResource(R.string.mintv_update_permission)
            MinTvUpdater.State.Installing -> stringResource(R.string.update_installer)
            MinTvUpdater.State.Confirmation -> stringResource(R.string.mintv_update_confirm_explanation)
            MinTvUpdater.State.Cancelled -> stringResource(R.string.mintv_update_cancelled)
            is MinTvUpdater.State.Failed -> stringResource(when (current.reason) {
                MinTvUpdater.Problem.SETUP -> R.string.mintv_update_setup_pending
                MinTvUpdater.Problem.RATE_LIMIT -> R.string.mintv_update_rate_limit
                MinTvUpdater.Problem.STORAGE -> R.string.mintv_update_storage
                MinTvUpdater.Problem.INSTALL -> R.string.update_install_failed
                MinTvUpdater.Problem.UNSUPPORTED -> R.string.update_no_compatible_apk
                MinTvUpdater.Problem.NETWORK -> R.string.update_failed_check
                MinTvUpdater.Problem.INVALID -> R.string.mintv_update_invalid
            })
        }
        Text(text, style = stageText(23, 600), color = StageColors.Text)
        if (state is MinTvUpdater.State.Downloading) {
            tv.own.owntv.features.home.ProgrammeProgress(state.percent / 100f, Modifier.padding(vertical = 14.mpx))
            Text(stringResource(R.string.mintv_v08_background_download), style = stageText(17, 400), color = StageColors.Muted)
        }
        if (state is MinTvUpdater.State.Available || state is MinTvUpdater.State.Downloading || state == MinTvUpdater.State.Verifying)
            Text(stringResource(R.string.mintv_v08_progress_hint), style = stageText(17, 400), color = StageColors.Muted)
        (state as? MinTvUpdater.State.Available)?.candidate?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.height(16.mpx))
            Text(stringResource(R.string.mintv_v08_notes), style = stageText(23, 700), color = StageColors.Text)
            // Remote-focusable notes scroll with Up/Down, never fake an action.
            var focused by remember { mutableStateOf(false) }
            Text(renderReleaseNotes(notes, StageColors.Text), style = stageText(18, 400), color = StageColors.Muted,
                modifier = Modifier.fillMaxWidth().heightIn(max = 230.mpx)
                    .onFocusChanged { focused = it.isFocused }
                    .border(2.mpx, if (focused) StageSurfaces.Cyan else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.mpx))
                    .verticalScroll(rememberScrollState()).focusable().padding(8.mpx))
        }
        Spacer(Modifier.height(12.mpx))
        StageButton(stringResource(R.string.mintv_update_automatic, stringResource(if (automatic) R.string.common_on else R.string.common_off)),
            onClick = onAutomatic, height = 48.mpx, textSize = 16)
    }
}

/**
 * Renders the minimal release notes (from CHANGELOG_APP.md, via the GitHub release body) for the update
 * dialog. Lightweight Markdown only — enough for our bullet-only format: `### ` section headers become
 * bold heading lines, `- ` bullets become "• ", and inline `**bold**` spans render bold. Everything else
 * is shown as-is. Not a full Markdown parser.
 */
private fun renderReleaseNotes(notes: String, headingColor: Color): AnnotatedString = buildAnnotatedString {
    val lines = notes.replace("\r\n", "\n").trim().split("\n")
    lines.forEachIndexed { index, raw ->
        if (index > 0) append("\n")
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("### ").trim())
                }
            line.startsWith("## ") ->
                withStyle(SpanStyle(color = headingColor, fontWeight = FontWeight.Bold)) {
                    appendInline(line.removePrefix("## ").trim())
                }
            line.startsWith("- ") -> { append("•  "); appendInline(line.removePrefix("- ")) }
            line.startsWith("* ") -> { append("•  "); appendInline(line.removePrefix("* ")) }
            else -> appendInline(line)
        }
    }
}

/** Appends [text], turning `**bold**` spans into actual bold runs (leaves other characters untouched). */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}
