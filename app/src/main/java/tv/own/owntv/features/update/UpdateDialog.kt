package tv.own.owntv.features.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.platform.LocalResources
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
    LaunchedEffect(Unit) { manager.resume(); if (checkOnOpen && checkUpdateOnEntry(manager.state.value)) manager.requestCheck() }
    var freeBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state) { if (state is MinTvUpdater.State.Available) freeBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { manager.availableStorage() } }
    LaunchedEffect(state) {
        // Only a live, explicit Update action can open the system prompt automatically.
        // After process recreation the durable Continue button requires another user action.
        if (state == MinTvUpdater.State.Confirmation && requestedInstall) {
            requestedInstall = false
            manager.openInstallerScreen(manager.confirmationIntent())
        }
    }
    // Closing presentation must not reset verified state or cancel an in-app download.
    UpdatePresentation(state, manager.channelLabel, automatic, checked, cached = manager.hasDownload(), availableBytes = freeBytes,
        onDismiss = {
            // Keep active/verified work when merely closing the window. Acknowledged terminal
            // installer outcomes still use the original cleanup so a later retry can recover.
            val current = manager.state.value
            if (current is MinTvUpdater.State.Failed || current == MinTvUpdater.State.Cancelled || current == MinTvUpdater.State.Installed) manager.dismiss()
            onDismiss()
        },
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
    onDismiss: () -> Unit, onAutomatic: () -> Unit, onAction: () -> Unit,
    cached: Boolean = false, availableBytes: Long? = null) {
    val resources = LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val notesFocus = remember { FocusRequester() }
    val bodyScroll = rememberScrollState()
    var bodyFocused by remember { mutableStateOf(false) }
    val scrollStep = with(LocalDensity.current) { 80.mpx.toPx() }
    val automaticFocus = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val experience = updateExperience(state, cached)
    val candidate = (state as? MinTvUpdater.State.Available)?.candidate
    val notes = candidate?.notes?.takeIf { it.isNotBlank() }
    val initialAction = remember { state is MinTvUpdater.State.Available || state == MinTvUpdater.State.Permission || state == MinTvUpdater.State.Confirmation }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { if (initialAction) actionFocus.requestFocus() else first.requestFocus() }
    }
    LaunchedEffect(experience.busy) {
        if (experience.busy) { withFrameNanos { }; runCatching { first.requestFocus() } }
    }
    val later = state is MinTvUpdater.State.Available || state == MinTvUpdater.State.Permission || state == MinTvUpdater.State.Confirmation
    val action = when (experience.action) {
        UpdateAction.DOWNLOAD -> R.string.v016_update_now
        UpdateAction.INSTALL -> R.string.v016_update_install_cached
        UpdateAction.PERMISSION -> R.string.mintv_update_permission_action
        UpdateAction.CONFIRM -> R.string.mintv_update_confirm
        else -> R.string.settings_check_updates
    }
    StagePopup(onDismiss = onDismiss, title = stringResource(if (state == MinTvUpdater.State.Idle || state == MinTvUpdater.State.UpToDate) R.string.mintv_update_about else R.string.v016_update_title), width = 1080.mpx, scroll = false, panelColor = StageSurfaces.Panel,
        modifier = Modifier.testTag("update-surface").focusProperties { onEnter = { if (initialAction && !experience.busy) actionFocus.requestFocus() else first.requestFocus() } }.focusGroup(),
        buttons = { CompositionLocalProvider(LocalResources provides resources) {
            StageButton(stringResource(if (later) R.string.update_later else if (experience.busy) R.string.v016_update_continue_browsing else R.string.settings_close), onClick = onDismiss,
                modifier = Modifier.focusRequester(first).focusProperties { up = notesFocus }, height = 56.mpx, textSize = 20)
            if (!experience.busy) StageButton(stringResource(action), onClick = onAction,
                modifier = Modifier.focusRequester(actionFocus).focusProperties { up = notesFocus; left = first }, height = 56.mpx, textSize = 20, tinted = true)
        } }) { CompositionLocalProvider(LocalResources provides resources) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = stageText(23, 700), color = StageColors.Text)
                Text(stringResource(R.string.mintv_v08_version_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()), style = stageText(18, 500), color = StageColors.Muted, modifier = Modifier.testTag("update-current-version"))
            }
            Text(channel, style = stageText(18, 600), color = StageSurfaces.Cyan)
        }
        Spacer(Modifier.height(16.mpx))
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().testTag("update-body")
            .focusRequester(notesFocus).focusProperties { up = first; down = automaticFocus }
            .onPreviewKeyEvent { event ->
                val delta = when (event.key) {
                    Key.DirectionDown -> scrollStep.takeIf { bodyScroll.canScrollForward }
                    Key.DirectionUp -> (-scrollStep).takeIf { bodyScroll.canScrollBackward }
                    else -> null
                }
                if (delta == null) false else {
                    if (event.type == KeyEventType.KeyDown) scope.launch { bodyScroll.scrollBy(delta) }
                    true
                }
            }.onFocusChanged { bodyFocused = it.isFocused }
            .border(2.mpx, if (bodyFocused) StageSurfaces.Cyan else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.mpx))
            .focusable().verticalScroll(bodyScroll).padding(4.mpx)) {
        Text(stringResource(when (experience.step) {
            UpdateStep.DISCOVER -> R.string.v016_update_step_check
            UpdateStep.REVIEW -> R.string.v016_update_step_review
            UpdateStep.DOWNLOAD, UpdateStep.VERIFY -> R.string.v016_update_step_download
            UpdateStep.PERMISSION, UpdateStep.CONFIRM -> R.string.v016_update_step_install
            UpdateStep.DONE -> R.string.v016_update_step_done
            UpdateStep.ISSUE -> R.string.v016_update_step_issue
        }), style = stageText(18, 700), color = StageSurfaces.Cyan)
        val text = when (val current = state) {
            MinTvUpdater.State.Idle -> stringResource(R.string.mintv_v08_not_checked)
            MinTvUpdater.State.Checking -> stringResource(R.string.mintv_v08_checking_hint)
            MinTvUpdater.State.UpToDate -> stringResource(R.string.update_latest, BuildConfig.VERSION_NAME)
            MinTvUpdater.State.Installed -> stringResource(R.string.mintv_v08_installed)
            is MinTvUpdater.State.Available -> stringResource(if (cached) R.string.v016_update_cached_verified else R.string.mintv_update_available, current.candidate.version)
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
        Text(text, style = stageText(if (experience.step == UpdateStep.ISSUE) 21 else 25,
            if (experience.step == UpdateStep.ISSUE) 500 else 700), color = StageColors.Text, modifier = Modifier.testTag("update-status"))
        candidate?.let {
            Text(stringResource(R.string.mintv_v08_version_target, it.version, it.code.toString()), style = stageText(19, 600), color = StageColors.Text)
            val size = android.text.format.Formatter.formatShortFileSize(context, it.bytes)
            Text(stringResource(R.string.v016_update_download_size, size), style = stageText(18, 400), color = StageColors.Muted)
            availableBytes?.let { free ->
                Text(stringResource(R.string.v016_update_free_space, android.text.format.Formatter.formatShortFileSize(context, free)),
                    style = stageText(17, 400), color = StageColors.Muted)
                if (!cached && free < updateRequiredSpace(it.bytes)) Text(stringResource(R.string.mintv_update_storage), style = stageText(18, 600), color = StageColors.Text)
            }
            if ((state as? MinTvUpdater.State.Available)?.interrupted == true && !cached)
                Text(stringResource(R.string.v016_update_interrupted), style = stageText(18, 600), color = StageSurfaces.Cyan)
        }
        if (state is MinTvUpdater.State.Downloading) {
            tv.own.owntv.features.home.ProgrammeProgress(state.percent / 100f, Modifier.padding(vertical = 14.mpx))
            if (state.totalBytes > 0) Text(stringResource(R.string.v016_update_byte_progress,
                android.text.format.Formatter.formatShortFileSize(context, state.receivedBytes), android.text.format.Formatter.formatShortFileSize(context, state.totalBytes)),
                style = stageText(18, 400), color = StageColors.Muted)
            Text(stringResource(R.string.mintv_v08_background_download), style = stageText(17, 400), color = StageColors.Muted)
        }
        if (candidate != null || state is MinTvUpdater.State.Downloading || state == MinTvUpdater.State.Verifying)
            Text(stringResource(R.string.v016_update_install_explanation), style = stageText(17, 400), color = StageColors.Muted)
        notes?.let {
            Spacer(Modifier.height(14.mpx))
            Text(stringResource(R.string.mintv_v08_notes), style = stageText(21, 700), color = StageColors.Text)
            Text(renderReleaseNotes(it, StageColors.Text), style = stageText(18, 400), color = StageColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(8.mpx))
        }
        }
        Spacer(Modifier.height(12.mpx))
        val date = checked?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
            ?: stringResource(R.string.mintv_update_never)
        Text(stringResource(R.string.mintv_update_last_check, date), style = stageText(16, 400), color = StageColors.Muted)
        StageButton(stringResource(R.string.mintv_update_automatic, stringResource(if (automatic) R.string.common_on else R.string.common_off)),
            onClick = onAutomatic, modifier = Modifier.focusRequester(automaticFocus).focusProperties { up = notesFocus; down = first }, height = 48.mpx, textSize = 18)
    } }
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
