package tv.own.owntv.features.home

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.ui.theme.mpx

/** Additive Settings destinations; existing groups, deep links and stored values keep their IDs. */
enum class MinTvSettingsDestination { SPORT, TWITCH, HOME }

/** Observes existing flags without a network request or a second repository. */
@Composable
internal fun rememberCompanionFlags(prefs: SharedPreferences): Pair<Boolean, Boolean> {
    var flags by remember(prefs) { mutableStateOf(prefs.getBoolean("shl-enabled", false) to prefs.getBoolean("broadcast-enabled", false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "shl-enabled" || key == "broadcast-enabled")
                flags = prefs.getBoolean("shl-enabled", false) to prefs.getBoolean("broadcast-enabled", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return flags
}

@Composable
internal fun MinTvSettingsPage(destination: MinTvSettingsDestination, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings = koinInject<SettingsRepository>()
    val profile by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = lifecycle == Lifecycle.State.RESUMED
    val first = remember(destination) { FocusRequester() }
    LaunchedEffect(destination, profile) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
    StageFullPage(emptyList(), stringResource(when (destination) {
        MinTvSettingsDestination.SPORT -> R.string.mintv_my_hockey
        MinTvSettingsDestination.TWITCH -> R.string.mintv_setup_twitch
        MinTvSettingsDestination.HOME -> R.string.mintv_v08_customize
    }), "", onBack, modifier, rowsFocus = first) {
        when (destination) {
            MinTvSettingsDestination.SPORT -> HockeySettings(profile, Modifier.focusRequester(first))
            MinTvSettingsDestination.TWITCH -> TwitchCompanion(true, active, Modifier.focusRequester(first), settingsMode = true)
            MinTvSettingsDestination.HOME -> HomeLayoutSettings(profile, Modifier.focusRequester(first), first)
        }
    }
}

@Composable
private fun HockeySettings(profile: Long?, modifier: Modifier) {
    val repository = koinInject<ShlRepository>()
    val broadcasts = koinInject<TvmatchenBroadcastSource>()
    val flags = rememberCompanionFlags(repository.preferences)
    var teamsOpen by remember(profile) { mutableStateOf(false) }
    HomeButton(stringResource(R.string.mintv_choose_teams), { teamsOpen = profile != null }, modifier)
    TvText(stringResource(R.string.mintv_shl_source_notice), color = MinTvMuted, lines = 6)
    HomeButton(stringResource(if (flags.first) R.string.mintv_shl_disable else R.string.mintv_shl_enable), {
        repository.preferences.edit().putBoolean("shl-enabled", !flags.first).apply()
    })
    if (repository.preferences.getBoolean("shl-access-blocked", false))
        TvText(stringResource(R.string.mintv_shl_access_blocked), color = MinTvMuted, lines = 4)
    TvText(stringResource(R.string.mintv_broadcast_source_notice), color = MinTvMuted, lines = 4)
    HomeButton(stringResource(if (flags.second) R.string.mintv_broadcast_disable else R.string.mintv_broadcast_enable), {
        repository.preferences.edit().putBoolean("broadcast-enabled", !flags.second).apply()
    })
    if (broadcasts.blocked) TvText(stringResource(R.string.mintv_broadcast_blocked), color = MinTvMuted, lines = 4)
    // Same local diagnostic/report/export component. Opening Settings never refreshes a sports source.
    HockeyDiagnosticPanel(repository)
    if (teamsOpen && profile != null) SportsTeamPicker(profile) { teamsOpen = false }
}

@Composable
private fun HomeLayoutSettings(profile: Long?, modifier: Modifier, entry: FocusRequester) {
    val store = koinInject<HomeSections>()
    val settings = koinInject<SettingsRepository>()
    val previews by settings.livePreviewEnabled.collectAsStateWithLifecycle(initialValue = false)
    var hidden by remember(profile) { mutableStateOf<Set<HomeSection>>(emptySet()) }
    var order by remember(profile) { mutableStateOf(defaultHomeOrder) }
    var loaded by remember(profile) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile) { if (profile != null) { hidden = store.hidden(profile); order = store.order(profile); loaded = true } }
    LaunchedEffect(profile, loaded) {
        if (loaded) { withFrameNanos { }; withFrameNanos { }; entry.requestSafely() }
    }
    if (!loaded) { TvText(stringResource(R.string.mintv_companion_loading)); return }
    HomeButton(stringResource(if (previews) R.string.mintv_preview_on else R.string.mintv_preview_off),
        { scope.launch { settings.setLivePreviewEnabled(!previews) } }, modifier)
    order.forEachIndexed { index, section ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
            HomeButton(stringResource(if (section in hidden) R.string.mintv_show_home_section else R.string.mintv_hide_home_section,
                stringResource(homeSectionLabel(section))), { scope.launch { profile?.let {
                    val next = if (section in hidden) hidden - section else hidden + section
                    store.save(it, next); hidden = next
                } } }, Modifier.weight(1f))
            if (index > 3) DashboardAction(stringResource(R.string.mintv_v08_move_up, stringResource(homeSectionLabel(section))), {
                scope.launch { profile?.let {
                    val next = order.toMutableList().apply { set(index, order[index - 1]); set(index - 1, section) }
                    store.saveOrder(it, next); order = normalizedHomeOrder(next)
                } }
            })
        }
    }
    HomeButton(stringResource(R.string.mintv_v08_default_layout), { scope.launch { profile?.let {
        store.save(it, emptySet()); store.saveOrder(it, defaultHomeOrder); hidden = emptySet(); order = defaultHomeOrder
    } } })
}
