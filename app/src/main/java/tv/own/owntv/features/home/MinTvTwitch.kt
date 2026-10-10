package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

@Composable
internal fun TwitchCompanion(visible: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val repository = koinInject<TwitchStatus>()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mintv-companion", 0) }
    var state by remember { mutableStateOf<TwitchState>(TwitchState.Unavailable) }
    var setup by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(prefs.getString("twitch-client-id", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.TWITCH_CLIENT_ID) }
    var editClientId by remember { mutableStateOf(clientId.isBlank()) }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<TwitchActivation?>(null) }
    var failure by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var checkedAt by remember { mutableLongStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val entryFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    LaunchedEffect(generation) { connected = repository.connected() }
    LaunchedEffect(active, visible, setup, generation) {
        if (!active || !visible || setup) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            state = try { repository.status(now) } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { TwitchState.Unavailable }
            checkedAt = System.currentTimeMillis()
            delay(60_000)
        }
    }
    LaunchedEffect(active, connecting, setup) {
        if (!active) { connecting = false; code = null; state = TwitchState.Unavailable; return@LaunchedEffect }
        if (!connecting || !setup) return@LaunchedEffect
        try {
            prefs.edit().putString("twitch-client-id", clientId).apply() // public identifier only
            repository.login(clientId) { code = it }; setup = false; restore = true; generation++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
        finally { connecting = false; code = null }
    }
    LaunchedEffect(disconnect) {
        if (disconnect) { repository.disconnect(); state = TwitchState.Unavailable; generation++; disconnect = false }
    }
    LaunchedEffect(setup, restore) {
        withFrameNanos { }
        if (setup) runCatching { connectFocus.requestFocus() }
        else if (restore) { runCatching { entryFocus.requestFocus() }; restore = false }
    }
    val fresh = if (active && now - checkedAt <= 90_000) state else TwitchState.Unavailable
    TvCard({ setup = true; failure = false }, Modifier.fillMaxWidth().then(modifier).focusRequester(entryFocus)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx)) {
            TvText(stringResource(R.string.mintv_ohnepixel), size = 24, bold = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                TvText(when (fresh) {
                    TwitchState.Unavailable -> stringResource(R.string.mintv_companion_unavailable)
                    TwitchState.Offline -> stringResource(R.string.mintv_twitch_offline)
                    is TwitchState.Live -> stringResource(R.string.mintv_twitch_live, fresh.title, NumberFormat.getIntegerInstance().format(fresh.viewers))
                }, size = 21, color = if (fresh is TwitchState.Live) MinTvTeal else MinTvMuted)
            }
            TvText(stringResource(if (connected) R.string.mintv_companion_settings else R.string.mintv_twitch_connect), size = 20)
        }
    }
    if (setup) Dialog(onDismissRequest = { setup = false; connecting = false; code = null; restore = true }) {
        Column(Modifier.width(760.mpx).background(MinTvNavy, RoundedCornerShape(14.mpx)).padding(32.mpx), verticalArrangement = Arrangement.spacedBy(22.mpx)) {
            TvText(stringResource(R.string.mintv_twitch_connect), size = 30, bold = true)
            if (editClientId && !connecting) {
                TvText(stringResource(R.string.mintv_twitch_public_id_hint), size = 22, color = MinTvMuted, lines = 3)
                CompanionInput(clientId) { clientId = it.take(128) }
            }
            if (!connecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    HomeButton(stringResource(R.string.mintv_twitch_connect), { failure = false; connecting = true }, Modifier.focusRequester(connectFocus))
                    if (!editClientId) HomeButton(stringResource(R.string.mintv_twitch_edit_id), { editClientId = true })
                    if (connected) HomeButton(stringResource(R.string.mintv_twitch_disconnect), { disconnect = true })
                }
            }
            code?.let {
                TvText(it.verificationUri, size = 22, color = MinTvTeal, lines = 3)
                TvText(it.code, size = 44, bold = true, lines = 1)
                TvText(stringResource(R.string.mintv_twitch_activation_help), size = 22, color = MinTvMuted)
            }
            if (connecting && code == null) TvText(stringResource(R.string.mintv_companion_loading))
            if (failure) {
                TvText(stringResource(R.string.mintv_twitch_connection_failed), color = MinTvMuted)
                CompanionInput(clientId) { clientId = it.take(128) }
            }
            HomeButton(stringResource(R.string.mintv_close), { setup = false; connecting = false; code = null; restore = true })
        }
    }
}
