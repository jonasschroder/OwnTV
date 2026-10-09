package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

@Composable
internal fun ShlCompanion(visible: Boolean, active: Boolean, liveVm: LiveViewModel, profileId: Long?,
    favorites: List<ChannelEntity>, onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit) {
    val repository = koinInject<ShlRepository>()
    var enabled by remember { mutableStateOf(repository.preferences.getBoolean("shl-enabled", false)) }
    var expansion by remember { mutableStateOf(repository.preferences.getBoolean("shl-expand", true)) }
    var screen by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }
    var upcoming by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<ShlSnapshot?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var standings by remember { mutableStateOf<List<ShlStanding>>(emptyList()) }
    var standingsAt by remember { mutableLongStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by remember(profileId) { mutableStateOf<ShlGame?>(null) }
    var query by remember { mutableStateOf("") }
    var channels by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var confirmed by remember(profileId) { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    var matchedGame by remember(profileId) { mutableStateOf<ShlGame?>(null) }
    var matching by remember { mutableStateOf(false) }
    var matchFailed by remember { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    val today = Instant.ofEpochMilli(now).atZone(Stockholm).toLocalDate()
    val todaysGames = data?.games.orEmpty().filter { it.on(today) }.sortedBy { !it.fbk }
    val next = data?.games?.firstOrNull { it.fbk && it.faceoff >= now }
    val expanded = enabled && expansion && todaysGames.isNotEmpty()
    val available = active && (visible || screen || selected != null)

    LaunchedEffect(available, enabled) {
        if (available && enabled) while (true) { now = System.currentTimeMillis(); delay(30_000) }
    }
    LaunchedEffect(available, enabled) {
        if (!available || !enabled) return@LaunchedEffect
        data = repository.cached()
        var retry = 60_000L
        while (true) {
            loading = data == null
            try {
                data = repository.refresh(System.currentTimeMillis())
                failed = false
                retry = 60_000
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; retry = (retry * 2).coerceAtMost(15 * 60_000) }
            finally { loading = false }
            delay(retry) // cheap cache check; network TTL is 6h / 10min on matchdays
        }
    }
    LaunchedEffect(available, enabled, expanded, screen, showTable, data?.seasonId, today) {
        if (!available || !enabled || !(expanded || screen && showTable)) return@LaunchedEffect
        val season = data?.seasonId ?: return@LaunchedEffect
        while (true) {
            try { standings = repository.standings(season, System.currentTimeMillis()); standingsAt = repository.standingsFetchedAt }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { standings = emptyList(); standingsAt = 0 }
            delay(6 * 60 * 60_000L)
        }
    }
    val scheduleFresh = !failed && data?.let { now - it.fetchedAt in 0 until 10 * 60_000L } == true
    val chosen = selected?.let { old -> data?.games?.firstOrNull { it.id == old.id } }
    val gameToMatch = if (selected != null) chosen else todaysGames.firstOrNull { it.fbk }.takeIf { expanded }
    LaunchedEffect(available, profileId, gameToMatch, query, favorites, scheduleFresh) {
        channels = emptyList(); confirmed = emptyList(); matchedGame = null; matchFailed = false
        if (!available || gameToMatch == null || profileId == null) { matching = false; return@LaunchedEffect }
        val game = gameToMatch
        matching = true
        try {
            delay(300)
            val found = liveVm.homeSportsChannels(if (selected != null) query else "", favorites, profileId)
            val matches = withContext(Dispatchers.IO) {
                found.filter { channel -> scheduleFresh &&
                    liveVm.homeStoredProgrammes(channel, game.faceoff - 60 * 60_000, game.faceoff + 3 * 60 * 60_000).any { epg ->
                        ShlEpgMatcher.confirmed(game, epg.title, epg.description, epg.startMs, epg.stopMs,
                            data?.games.orEmpty().filter { kotlin.math.abs(it.faceoff - game.faceoff) < 3 * 60 * 60_000 })
                    }
                }
            }
            channels = found; confirmed = matches; matchedGame = game
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { matchFailed = true }
        finally { matching = false }
    }
    LaunchedEffect(restore, screen, selected) {
        if (restore && !screen && selected == null) { withFrameNanos { }; runCatching { entryFocus.requestFocus() }; restore = false }
    }
    fun close() { screen = false; showTable = false; selected = null; query = ""; restore = true }
    fun choose(game: ShlGame) { selected = game; query = "" }

    Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), modifier = Modifier.fillMaxWidth()) {
            CompanionText(stringResource(R.string.mintv_shl_compact, when {
                !enabled -> stringResource(R.string.mintv_shl_disabled)
                loading -> stringResource(R.string.mintv_companion_loading)
                data == null -> stringResource(R.string.mintv_companion_unavailable)
                expanded && todaysGames.any { it.fbk } -> stringResource(R.string.mintv_shl_matchday)
                next != null -> stringResource(R.string.mintv_shl_cached, gameLabel(next), dateLabel(data!!.fetchedAt))
                else -> stringResource(R.string.mintv_shl_next_unknown)
            }), Modifier.weight(1f))
            HomeButton(stringResource(R.string.mintv_shl_open), { screen = true }, Modifier.focusRequester(entryFocus))
            if (expanded) HomeButton(stringResource(R.string.mintv_shl_collapse), {
                expansion = false; repository.preferences.edit().putBoolean("shl-expand", false).apply()
            })
        }
        if (expanded) {
            todaysGames.firstOrNull { it.fbk }?.let { fbk ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                    CompanionText(gameLabel(fbk), Modifier.weight(1f))
                    val direct = confirmed.singleOrNull().takeIf { matchedGame == fbk && scheduleFresh && !matching }
                    HomeButton(if (direct != null) stringResource(R.string.mintv_shl_watch, direct.name) else stringResource(R.string.mintv_shl_choose), {
                        if (direct != null) onPlay(direct, confirmed) else choose(fbk)
                    })
                }
            }
            val others = todaysGames.filterNot { it.fbk }.take(2)
            if (others.isNotEmpty()) {
                val labels = others.map { stringResource(R.string.mintv_shl_pair, it.home, it.away) }
                CompanionText(labels.joinToString(stringResource(R.string.mintv_companion_separator)))
            }
            standings.firstOrNull { teamTokens(it.team).contains("farjestad") }?.let {
                CompanionText(stringResource(R.string.mintv_shl_rank, NumberFormat.getIntegerInstance().format(it.rank), NumberFormat.getIntegerInstance().format(it.points), dateLabel(standingsAt)))
            }
            CompanionText(snapshotLabel(data!!))
            if (failed) CompanionText(stringResource(R.string.mintv_companion_stale))
        }
    }
    if (screen || selected != null) CompanionDialog(onClose = ::close) {
        CompanionText(stringResource(R.string.mintv_shl_title))
        if (selected == null) {
            CompanionText(stringResource(R.string.mintv_shl_source_notice), lines = 6)
            if (repository.preferences.getBoolean("shl-access-blocked", false)) CompanionText(stringResource(R.string.mintv_shl_access_blocked))
            Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                HomeButton(stringResource(if (enabled) R.string.mintv_shl_disable else R.string.mintv_shl_enable), {
                    enabled = !enabled; repository.preferences.edit().putBoolean("shl-enabled", enabled).apply()
                    if (!enabled) { data = null; standings = emptyList() }
                })
                HomeButton(stringResource(if (expansion) R.string.mintv_shl_compact_only else R.string.mintv_shl_expand), {
                    expansion = !expansion; repository.preferences.edit().putBoolean("shl-expand", expansion).apply()
                })
                HomeButton(stringResource(R.string.mintv_shl_table), { showTable = !showTable })
            }
            if (loading) CompanionText(stringResource(R.string.mintv_companion_loading))
            if (data == null) CompanionText(stringResource(R.string.mintv_companion_unavailable))
            if (failed && data != null) CompanionText(stringResource(R.string.mintv_companion_stale))
            data?.let { snapshot -> CompanionText(snapshotLabel(snapshot)) }
            if (showTable) {
                if (standings.isEmpty()) CompanionText(stringResource(R.string.mintv_companion_unavailable))
                else {
                    CompanionText(stringResource(R.string.mintv_shl_regular_table, dateLabel(standingsAt)))
                    standings.forEach { s -> CompanionText(stringResource(R.string.mintv_shl_table_row,
                        NumberFormat.getIntegerInstance().format(s.rank), s.team, NumberFormat.getIntegerInstance().format(s.points))) }
                }
            } else {
                val future = data?.games.orEmpty().filter { it.faceoff >= now }
                val useFuture = upcoming || todaysGames.isEmpty()
                val lastPage = ((future.size - 1).coerceAtLeast(0) / 14)
                val currentPage = page.coerceIn(0, lastPage)
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    HomeButton(stringResource(if (upcoming) R.string.mintv_shl_today else R.string.mintv_shl_upcoming), { upcoming = !upcoming; page = 0 })
                    if (useFuture && currentPage > 0) HomeButton(stringResource(R.string.mintv_shl_previous_page), { page = currentPage - 1 })
                    if (useFuture && currentPage < lastPage) HomeButton(stringResource(R.string.mintv_shl_next_page), { page = currentPage + 1 })
                }
                val games = if (useFuture) future.drop(currentPage * 14).take(14).sortedBy { !it.fbk } else todaysGames
                games.forEach { game -> HomeButton(gameLabel(game), { choose(game) }, Modifier.fillMaxWidth()) }
            }
        } else {
            val game = chosen
            if (game == null) CompanionText(stringResource(R.string.mintv_companion_unavailable)) else {
            CompanionText(gameLabel(game))
            CompanionText(stringResource(R.string.mintv_shl_lineup_unavailable))
            CompanionText(stringResource(R.string.mintv_shl_local_epg))
            if (matching) CompanionText(stringResource(R.string.mintv_companion_loading))
            if (matchFailed) CompanionText(stringResource(R.string.mintv_companion_unavailable))
            if (!matching && confirmed.isEmpty()) CompanionText(stringResource(R.string.mintv_shl_no_match))
            confirmed.takeIf { matchedGame == game && scheduleFresh }.orEmpty().take(24).forEach { channel ->
                HomeButton(stringResource(R.string.mintv_shl_watch, channel.name), { close(); onPlay(channel, confirmed) }, Modifier.fillMaxWidth())
            }
            CompanionText(stringResource(R.string.mintv_shl_manual))
            CompanionInput(query) { query = it.take(80) }
            channels.filterNot { it in confirmed }.take(24).forEach { channel ->
                HomeButton(channel.name, { val list = channels; close(); onPlay(channel, list) }, Modifier.fillMaxWidth())
            }
            }
        }
    }
}

@Composable
internal fun TwitchCompanion(visible: Boolean, active: Boolean) {
    val repository = koinInject<TwitchStatus>()
    var state by remember { mutableStateOf<TwitchState>(TwitchState.Unavailable) }
    var setup by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var checkedAt by remember { mutableLongStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val entryFocus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
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
        try { repository.login(clientId) { code = it }; setup = false; restore = true; generation++ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
        finally { connecting = false; code = null }
    }
    LaunchedEffect(disconnect) {
        if (disconnect) { repository.disconnect(); state = TwitchState.Unavailable; generation++; disconnect = false }
    }
    LaunchedEffect(setup, restore) {
        if (!setup && restore) { withFrameNanos { }; runCatching { entryFocus.requestFocus() }; restore = false }
    }
    val fresh = if (active && now - checkedAt <= 90_000) state else TwitchState.Unavailable
    Row(horizontalArrangement = Arrangement.spacedBy(18.mpx), modifier = Modifier.fillMaxWidth()) {
        CompanionText(stringResource(R.string.mintv_twitch_status, when (fresh) {
            TwitchState.Unavailable -> stringResource(R.string.mintv_companion_unavailable)
            TwitchState.Offline -> stringResource(R.string.mintv_twitch_offline)
            is TwitchState.Live -> stringResource(R.string.mintv_twitch_live, fresh.title, NumberFormat.getIntegerInstance().format(fresh.viewers))
        }), Modifier.weight(1f))
        HomeButton(stringResource(R.string.mintv_twitch_setup), { setup = true; failure = false }, Modifier.focusRequester(entryFocus))
    }
    if (setup) CompanionDialog(onClose = { setup = false; connecting = false; code = null; restore = true }) {
        CompanionText(stringResource(R.string.mintv_twitch_setup_notice), lines = 6)
        if (!connecting) {
            CompanionInput(clientId) { clientId = it.take(128) }
            HomeButton(stringResource(R.string.mintv_twitch_connect), { failure = false; connecting = true })
            HomeButton(stringResource(R.string.mintv_twitch_disconnect), { disconnect = true })
        }
        code?.let { CompanionText(stringResource(R.string.mintv_twitch_code, it)) }
        if (connecting && code == null) CompanionText(stringResource(R.string.mintv_companion_loading))
        if (failure) CompanionText(stringResource(R.string.mintv_companion_unavailable))
    }
}

@Composable
private fun CompanionDialog(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
    Dialog(onDismissRequest = onClose) {
        LazyColumn(Modifier.width(1040.mpx).heightIn(max = 850.mpx).background(Color(0xFF101820)).padding(28.mpx),
            verticalArrangement = Arrangement.spacedBy(16.mpx)) {
            item { HomeButton(stringResource(R.string.mintv_close), onClose, Modifier.focusRequester(closeFocus)) }
            item { Column(verticalArrangement = Arrangement.spacedBy(14.mpx), content = content) }
        }
    }
}

@Composable
private fun CompanionInput(value: String, onChange: (String) -> Unit) {
    BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Color.White),
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1B2B36)).padding(18.mpx))
}

@Composable
private fun CompanionText(value: String, modifier: Modifier = Modifier, lines: Int = 3) {
    Text(value, modifier = modifier, style = stageText(18, 500), color = Color.LightGray, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

private fun dateLabel(ms: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    .withLocale(Locale.getDefault()).withZone(Stockholm).format(Instant.ofEpochMilli(ms))

@Composable
private fun gameLabel(game: ShlGame): String = stringResource(R.string.mintv_shl_game,
    game.home, game.away, dateLabel(game.faceoff), game.result ?: stringResource(R.string.mintv_shl_scheduled))

@Composable
private fun snapshotLabel(snapshot: ShlSnapshot): String = stringResource(R.string.mintv_shl_snapshot,
    dateLabel(snapshot.fetchedAt), snapshot.sourceUpdatedAt?.let(::dateLabel) ?: stringResource(R.string.mintv_companion_unavailable))
