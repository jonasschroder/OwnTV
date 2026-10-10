package tv.own.owntv.features.update

import androidx.compose.foundation.layout.*
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
    val focus = remember { FocusRequester() }
    var requestedInstall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { manager.resume(); if (checkOnOpen) manager.check(force = true) }
    LaunchedEffect(state) {
        // Only a live, explicit Update action can open the system prompt automatically.
        // After process recreation the durable Continue button requires another user action.
        if (state == MinTvUpdater.State.Confirmation && requestedInstall) {
            requestedInstall = false
            manager.openInstallerScreen(manager.confirmationIntent())
        }
    }
    val close = { manager.dismiss(); onDismiss() }
    StagePopup(onDismiss = close, title = stringResource(R.string.mintv_update_about), width = 950.mpx,
        // Redirect the actual focus entry into this dialog, rather than racing window attachment.
        modifier = Modifier.focusProperties { onEnter = { focus.requestFocus() } }.focusGroup(),
        buttons = {
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; focus.requestFocus() }
            StageButton(stringResource(R.string.settings_close), onClick = close, modifier = Modifier.focusRequester(focus), height = 56.mpx, textSize = 18)
            when (state) {
                is MinTvUpdater.State.Available -> StageButton(stringResource(R.string.update_now), onClick = { requestedInstall = true; scope.launch { if (manager.hasDownload()) manager.continueInstall() else manager.download() } }, height = 56.mpx, textSize = 18, tinted = true)
                MinTvUpdater.State.Permission -> StageButton(stringResource(R.string.mintv_update_permission_action), onClick = { manager.openInstallerScreen(manager.permissionIntent()) }, height = 56.mpx, textSize = 18, tinted = true)
                MinTvUpdater.State.Confirmation -> StageButton(stringResource(R.string.mintv_update_confirm), onClick = { manager.openInstallerScreen(manager.confirmationIntent()) }, height = 56.mpx, textSize = 18, tinted = true)
                MinTvUpdater.State.Checking, is MinTvUpdater.State.Downloading, MinTvUpdater.State.Installing -> Unit
                else -> StageButton(stringResource(R.string.settings_check_updates), onClick = { scope.launch { manager.check(force = true) } }, height = 56.mpx, textSize = 18, tinted = true)
            }
        }) {
        Text(stringResource(R.string.mintv_update_identity, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString(), manager.channelLabel), style = stageText(18, 600), color = StageColors.Text)
        val date = checked?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: stringResource(R.string.mintv_update_never)
        Text(stringResource(R.string.mintv_update_last_check, date), style = stageText(16, 400), color = StageColors.Muted)
        StageButton(stringResource(R.string.mintv_update_automatic, stringResource(if (automatic) R.string.common_on else R.string.common_off)),
            onClick = { scope.launch { settings.setUpdateCheckOnStart(!automatic) } }, height = 48.mpx, textSize = 16)
        Spacer(Modifier.height(16.mpx))
        val text = when (val current = state) {
            MinTvUpdater.State.Idle -> stringResource(R.string.mintv_update_ready)
            MinTvUpdater.State.Checking -> stringResource(R.string.update_checking)
            MinTvUpdater.State.UpToDate -> stringResource(R.string.update_latest, BuildConfig.VERSION_NAME)
            is MinTvUpdater.State.Available -> stringResource(R.string.mintv_update_available, current.candidate.version)
            is MinTvUpdater.State.Downloading -> stringResource(R.string.update_downloading, current.percent)
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
        Text(text, style = stageText(18, 400), color = StageColors.Muted)
        (state as? MinTvUpdater.State.Available)?.candidate?.notes?.let { notes ->
            Text(renderReleaseNotes(notes, StageColors.Text), style = stageText(17, 400), color = StageColors.Muted,
                modifier = Modifier.heightIn(max = 220.mpx).verticalScroll(rememberScrollState()))
        }
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
