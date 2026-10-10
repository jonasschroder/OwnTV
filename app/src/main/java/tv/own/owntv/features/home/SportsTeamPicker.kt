package tv.own.owntv.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

/** Optional, local-only team selection; opening this picker never fetches a sports page. */
@Composable
internal fun SportsTeamPicker(profileId: Long, onDismiss: () -> Unit) {
    val store = koinInject<SportsPreferences>()
    val revision by store.changes.collectAsStateWithLifecycle()
    var value by remember(profileId) { mutableStateOf<SportPreferences?>(null) }
    var sport by rememberSaveable(profileId) { mutableStateOf<Sport?>(null) }
    var competitionId by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val list = rememberLazyListState()
    val competition = SportsCatalog.competitions.firstOrNull { it.id == competitionId }
    LaunchedEffect(profileId, revision) {
        try { value = store.read(profileId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    LaunchedEffect(sport, competitionId, value != null) {
        list.scrollToItem(0)
        withFrameNanos { }; runCatching { first.requestFocus() }.onFailure { runCatching { close.requestFocus() } }
    }
    fun back() { when { competitionId != null -> competitionId = null; sport != null -> sport = null; else -> onDismiss() } }
    fun save(change: (SportPreferences) -> SportPreferences) {
        if (saving) return
        val previous = value ?: return
        saving = true
        scope.launch {
            try { val next = change(previous); store.save(profileId, next); value = next; error = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = ::back)
        Column(Modifier.width(1180.mpx).heightIn(max = 860.mpx).background(MinTvNavy).padding(32.mpx).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TvText(stringResource(R.string.mintv_my_teams), size = 34, bold = true)
                HomeButton(stringResource(R.string.mintv_back), ::back, Modifier.focusRequester(close))
            }
            if (error) TvText(stringResource(R.string.mintv_sports_save_failed), color = MinTvMuted)
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                val selection = value
                when {
                    selection == null -> item { TvText(stringResource(R.string.mintv_companion_loading)) }
                    sport == null -> {
                        item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                            HomeButton(stringResource(R.string.mintv_ice_hockey), { sport = Sport.ICE_HOCKEY }, Modifier.focusRequester(first))
                            HomeButton(stringResource(R.string.mintv_football), { sport = Sport.FOOTBALL })
                        } }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                            HomeButton(stringResource(if (selection.prominent) R.string.mintv_sports_hide_home else R.string.mintv_sports_show_home), { save { it.copy(prominent = !it.prominent) } })
                            HomeButton(stringResource(if (selection.hideScores) R.string.mintv_sports_show_scores else R.string.mintv_sports_hide_scores), { save { it.copy(hideScores = !it.hideScores) } })
                        } }
                        if (selection.teamIds.isEmpty()) item { TvText(stringResource(R.string.mintv_sports_no_teams), color = MinTvMuted) }
                        items(selection.teamIds, key = { it }) { id ->
                            val team = SportsCatalog.team(id)
                            Column(verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                                TvText(team?.name ?: stringResource(R.string.mintv_team_unavailable), size = 27, bold = true,
                                    color = if (id == selection.primaryTeamId) MinTvTeal else androidx.compose.ui.graphics.Color.White)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                                    if (id != selection.primaryTeamId) HomeButton(stringResource(R.string.mintv_primary_team), { save { it.primary(id) } })
                                    if (selection.teamIds.indexOf(id) > 0) HomeButton(stringResource(R.string.mintv_move_up), { save { it.move(id, -1) } })
                                    if (selection.teamIds.indexOf(id) < selection.teamIds.lastIndex) HomeButton(stringResource(R.string.mintv_move_down), { save { it.move(id, 1) } })
                                    HomeButton(stringResource(R.string.mintv_remove_team), { save { it.toggle(id) } })
                                }
                            }
                        }
                    }
                    competition == null -> {
                        items(SportsCatalog.competitions.filter { it.sport == sport }, key = { it.id }) { c ->
                            HomeButton(if (c == SportsCatalog.football) stringResource(R.string.mintv_swedish_football) else c.name,
                                { competitionId = c.id }, Modifier.fillMaxWidth().then(if (c == SportsCatalog.competitions.firstOrNull { it.sport == sport }) Modifier.focusRequester(first) else Modifier))
                        }
                    }
                    else -> {
                        if (!competition.scheduleAvailable) item { TvText(stringResource(R.string.mintv_schedule_unavailable), color = MinTvMuted) }
                        items(SportsCatalog.inCompetition(competition), key = { it.id }) { team ->
                            TvCard({ save { it.toggle(team.id) } }, Modifier.fillMaxWidth().then(if (team == SportsCatalog.inCompetition(competition).firstOrNull()) Modifier.focusRequester(first) else Modifier), selected = team.id in selection.teamIds) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TvText(team.name, size = 27, bold = true)
                                    TvText(stringResource(if (team.id in selection.teamIds) R.string.mintv_team_following else R.string.mintv_follow_team), color = MinTvTeal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
