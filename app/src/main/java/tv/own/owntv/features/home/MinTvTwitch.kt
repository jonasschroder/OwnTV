package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

@Composable
internal fun TwitchCompanion(visible: Boolean, active: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val repository = koinInject<TwitchStatus>()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mintv-companion", 0) }
    val playback = remember(context) { TwitchPlayback(context) }
    TwitchCompanionUi(visible, active, prefs, BuildConfig.TWITCH_CLIENT_ID,
        repository::connected, repository::status, repository::login, repository::disconnect, modifier, playback::players, playback::open, compact)
}

/** Presentation adapter; token storage and Twitch protocol remain in TwitchStatus. */
@Composable
internal fun TwitchCompanionUi(
    visible: Boolean, active: Boolean, prefs: android.content.SharedPreferences, configuredClientId: String,
    readConnection: suspend () -> Boolean, readStatus: suspend (Long) -> TwitchState,
    authorize: suspend (String, (TwitchActivation) -> Unit) -> Unit, disconnectAccount: suspend () -> Unit,
    modifier: Modifier = Modifier, listPlayers: () -> List<TwitchPlayer> = { emptyList() },
    openPlayer: (TwitchPlayer) -> Boolean = { false }, compact: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var playbackOpen by remember { mutableStateOf(false) }
    var players by remember { mutableStateOf<List<TwitchPlayer>>(emptyList()) }
    var launchFailed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<TwitchState>(TwitchState.Unavailable) }
    var setup by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(prefs.getString("twitch-client-id", null)?.takeIf { it.isNotBlank() } ?: configuredClientId) }
    var editClientId by remember { mutableStateOf(clientId.isBlank()) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<TwitchActivation?>(null) }
    var failure by remember { mutableStateOf<TwitchFailure?>(null) }
    var statusFailure by remember { mutableStateOf<TwitchFailure?>(null) }
    var disconnect by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var checkedAt by remember { mutableLongStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val entryFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    LaunchedEffect(generation) { connected = readConnection() }
    LaunchedEffect(active, visible, setup, generation) {
        if (!active || !visible || setup) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            state = try { readStatus(now).also { statusFailure = null } } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { statusFailure = error as? TwitchFailure ?: TwitchFailure(TwitchFailureKind.NETWORK, TwitchStage.STATUS); TwitchState.Unavailable }
            connected = readConnection()
            checkedAt = System.currentTimeMillis()
            delay(60_000)
        }
    }
    LaunchedEffect(active, connecting, setup) {
        if (!active) { playbackOpen = false; connecting = false; code = null; state = TwitchState.Unavailable; return@LaunchedEffect }
        if (!connecting || !setup) return@LaunchedEffect
        try {
            prefs.edit().putString("twitch-client-id", clientId).apply() // public identifier only
            authorize(clientId) { code = it }; setup = false; restore = true; generation++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error as? TwitchFailure ?: TwitchFailure(TwitchFailureKind.NETWORK) }
        finally { connecting = false; code = null }
    }
    LaunchedEffect(disconnect) {
        if (disconnect) { disconnectAccount(); state = TwitchState.Unavailable; generation++; disconnect = false }
    }
    LaunchedEffect(setup, restore, connecting) {
        withFrameNanos { }
        if (setup) connectFocus.requestSafely()
        else if (restore) { entryFocus.requestSafely(); restore = false }
    }
    val fresh = if (active && now - checkedAt <= 90_000) state else TwitchState.Unavailable
    val openSettings = {
        setup = true; failure = if (connected) statusFailure else null; editClientId = clientId.isBlank()
        connecting = !connected && clientId.isNotBlank()
    }
    val watch = {
        scope.launch {
            players = withContext(Dispatchers.IO) { runCatching { listPlayers() }.getOrDefault(emptyList()) }
            launchFailed = false; playbackOpen = true
        }; Unit
    }
    if (compact) TwitchQuickCard(fresh, connected, openSettings, watch, modifier)
    else {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.mpx), verticalAlignment = Alignment.CenterVertically) {
    TvCard({
        setup = true; failure = if (connected) statusFailure else null; editClientId = clientId.isBlank()
        connecting = !connected && clientId.isNotBlank()
    }, Modifier.weight(1f).then(modifier).focusRequester(entryFocus)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            TvText(stringResource(R.string.mintv_ohnepixel), size = 24, bold = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                TvText(when (fresh) {
                    TwitchState.Unavailable -> stringResource(R.string.mintv_companion_unavailable)
                    TwitchState.Offline -> stringResource(R.string.mintv_twitch_offline)
                    is TwitchState.Live -> stringResource(R.string.mintv_twitch_live, fresh.title, NumberFormat.getIntegerInstance().format(fresh.viewers))
                }, size = 21, color = if (fresh is TwitchState.Live) MinTvTeal else MinTvMuted)
                (fresh as? TwitchState.Live)?.category?.let { TvText(it, size = 19, color = MinTvMuted) }
            }
            TvText(stringResource(if (connected) R.string.mintv_companion_settings else R.string.mintv_twitch_connect), size = 20)
        }
    }
    if (fresh is TwitchState.Live) HomeButton(stringResource(R.string.mintv_twitch_watch), {
        scope.launch {
            players = withContext(Dispatchers.IO) { runCatching { listPlayers() }.getOrDefault(emptyList()) }
            launchFailed = false; playbackOpen = true
        }
    }, Modifier.testTag("twitch-watch"))
    }
    }
    if (playbackOpen) TwitchPlayerPicker(players, launchFailed, { player ->
        if (active && visible && fresh is TwitchState.Live && System.currentTimeMillis() - checkedAt in 0..90_000 && openPlayer(player)) { playbackOpen = false; restore = true }
        else launchFailed = true
    }, { playbackOpen = false; restore = true })
    if (setup) Dialog(onDismissRequest = { setup = false; connecting = false; code = null; restore = true }) {
        Column(Modifier.width(760.mpx).background(MinTvNavy, RoundedCornerShape(14.mpx)).padding(32.mpx), verticalArrangement = Arrangement.spacedBy(22.mpx)) {
            TvText(stringResource(R.string.mintv_twitch_connect), size = 30, bold = true)
            if (editClientId && !connecting) {
                TvText(stringResource(R.string.mintv_twitch_public_id_hint), size = 22, color = MinTvMuted, lines = 3)
                CompanionInput(clientId, Modifier.testTag("twitch-public-client-id")) { clientId = it.take(128) }
            }
            if (!connecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    HomeButton(stringResource(R.string.mintv_twitch_connect), { failure = null; connecting = true }, Modifier.focusRequester(connectFocus))
                    if (connected) HomeButton(stringResource(R.string.mintv_twitch_disconnect), { disconnect = true })
                }
            }
            if (!editClientId) HomeButton(stringResource(R.string.mintv_twitch_edit_id), {
                connecting = false; code = null; failure = null; editClientId = true
            })
            code?.let {
                val qr by produceState<android.graphics.Bitmap?>(null, it.verificationUri) {
                    value = withContext(Dispatchers.Default) { tv.own.owntv.core.companion.CompanionLink.renderQr(it.verificationUri, 256) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
                    qr?.let { bitmap -> Image(bitmap.asImageBitmap(), stringResource(R.string.mintv_twitch_qr), Modifier.size(170.mpx).testTag("twitch-activation-qr")) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                        TvText(it.verificationUri, size = 18, color = MinTvTeal, lines = 3)
                        TvText(it.code, size = 40, bold = true, lines = 1)
                    }
                }
                TvText(stringResource(R.string.mintv_twitch_activation_help), size = 20, color = MinTvMuted)
                TvText(stringResource(R.string.mintv_twitch_waiting_approval), size = 20, color = MinTvMuted)
            }
            if (connecting && code == null) TvText(stringResource(R.string.mintv_companion_loading))
            failure?.let { error ->
                TvText(stringResource(twitchFailureText(error.kind)), color = MinTvMuted, size = 20, lines = 4)
                error.stage?.let { stage ->
                    val label = stringResource(twitchStageText(stage))
                    TvText(if (error.status == null) stringResource(R.string.mintv_twitch_diagnostic_stage, label)
                        else stringResource(R.string.mintv_twitch_diagnostic_http, label, error.status), color = MinTvMuted, size = 18, lines = 2)
                }
            }
            HomeButton(stringResource(R.string.mintv_close), { setup = false; connecting = false; code = null; restore = true }, if (connecting) Modifier.focusRequester(connectFocus) else Modifier)
        }
    }
}

internal fun twitchStageText(stage: TwitchStage) = when (stage) {
    TwitchStage.DEVICE -> R.string.mintv_twitch_stage_device
    TwitchStage.POLL -> R.string.mintv_twitch_stage_poll
    TwitchStage.VALIDATION -> R.string.mintv_twitch_stage_validation
    TwitchStage.STORAGE -> R.string.mintv_twitch_stage_storage
    TwitchStage.REFRESH -> R.string.mintv_twitch_stage_refresh
    TwitchStage.STATUS -> R.string.mintv_twitch_stage_status
}

internal fun twitchFailureText(kind: TwitchFailureKind) = when (kind) {
    TwitchFailureKind.CLIENT -> R.string.mintv_twitch_error_client
    TwitchFailureKind.ACCESS -> R.string.mintv_twitch_error_access
    TwitchFailureKind.DENIED -> R.string.mintv_twitch_error_denied
    TwitchFailureKind.EXPIRED -> R.string.mintv_twitch_error_expired
    TwitchFailureKind.NETWORK, TwitchFailureKind.TIMEOUT -> R.string.mintv_twitch_error_network
    TwitchFailureKind.RATE_LIMITED -> R.string.mintv_twitch_error_rate
    TwitchFailureKind.RESPONSE -> R.string.mintv_twitch_error_response
    TwitchFailureKind.AUTHENTICATION -> R.string.mintv_twitch_error_auth
    TwitchFailureKind.STORAGE -> R.string.mintv_twitch_error_storage
}

@Composable
internal fun TwitchPlayerPicker(players: List<TwitchPlayer>, failed: Boolean, onSelect: (TwitchPlayer) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { withFrameNanos { }; first.requestSafely() }
        Column(Modifier.width(760.mpx).background(MinTvNavy, RoundedCornerShape(14.mpx)).padding(32.mpx), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            TvText(stringResource(R.string.mintv_twitch_watch), size = 30, bold = true)
            if (players.isEmpty()) TvText(stringResource(R.string.mintv_twitch_no_player), size = 22, color = MinTvMuted, lines = 4)
            players.forEachIndexed { index, player ->
                HomeButton(stringResource(if (player.direct) R.string.mintv_twitch_open_direct else R.string.mintv_twitch_open_manual, player.name),
                    { onSelect(player) }, if (index == 0) Modifier.focusRequester(first) else Modifier)
            }
            if (players.any { !it.direct }) TvText(stringResource(R.string.mintv_twitch_manual_help), size = 21, color = MinTvMuted, lines = 3)
            if (failed) TvText(stringResource(R.string.mintv_twitch_launch_failed), size = 21, color = MinTvMuted, lines = 3)
            HomeButton(stringResource(R.string.mintv_close), onDismiss, if (players.isEmpty()) Modifier.focusRequester(first) else Modifier)
        }
    }
}
