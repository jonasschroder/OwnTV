package tv.own.owntv.features.live.source

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.*

internal class ChannelSourceActions(val play: (String, ChannelEntity?) -> Unit, val open: (String, Long?) -> Unit, val isOpen: Boolean)
private data class PickerSession(val identity: String, val owner: Long, val openingDownTime: Long?)

/** One host per destination, same store/resolver and existing shared playback callback. No player.
 * All reads are local and bounded by SwedishChannelStore. Opening/committing never fetches EPG.
 */
@Composable
internal fun rememberChannelSourceSelection(store: SwedishChannelStore, db: OwnTVDatabase, vm: LiveViewModel,
    profile: Long, sources: List<Long>, onPlay: (ChannelEntity, String) -> Unit, onClosed: () -> Unit): ChannelSourceActions {
    val scope = rememberCoroutineScope()
    val access by vm.swedishRowContext.collectAsStateWithLifecycle()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val currentProfile by rememberUpdatedState(profile)
    val currentSources by rememberUpdatedState(sources)
    val currentPlay by rememberUpdatedState(onPlay)
    val closed by rememberUpdatedState(onClosed)
    var session by remember(profile, sources) { mutableStateOf<PickerSession?>(null) }
    var data by remember(profile, sources) { mutableStateOf<SourcePickerData?>(null) }
    var error by remember(profile, sources) { mutableStateOf<SourcePickerError?>(null) }
    var busy by remember(profile, sources) { mutableStateOf(false) }
    // A non-state latch closes the gap before recomposition after two rapid OK events.
    val actionInFlight = remember { booleanArrayOf(false) }
    fun trace(event: tv.own.owntv.player.LiveEntryEvent) = tv.own.owntv.player.LiveEntryDiagnostics.trace.record(0,
        android.os.SystemClock.elapsedRealtime(), tv.own.owntv.player.LiveEntryOrigin.SOURCE_PICKER, event = event)
    fun current() = currentProfile == profile && currentSources == sources &&
        vm.homeLibraryContext.value == (profile to sources) && lifecycle == Lifecycle.State.RESUMED
    suspend fun read(identity: String): SourcePickerData? {
        if (!current()) return null
        val choices = store.choices(profile)
        val library = store.library(profile, sources, choices)
        val group = library.groups.singleOrNull { it.identity == identity } ?: return null
        return SourcePickerData(group, choices, library.bounded)
    }
    suspend fun channel(variant: SwedishVariant): ChannelEntity? {
        val expected = access?.takeIf { it.profileId == profile && it.sourceIds == sources }
            ?: return null.also { trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_METADATA_CHANGED) }
        return vm.homeChannel(variant.id, profile)?.takeIf {
            current() && it.streamUrl.isNotBlank() && it.sourceId == variant.sourceId &&
                CustomizeKeys.channel(it) == variant.stableKey && vm.swedishContextCurrent(expected).also { valid ->
                    if (!valid) trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_METADATA_CHANGED)
                }
        }
    }
    fun dismiss() { session = null; data = null; error = null; closed() }
    LaunchedEffect(lifecycle) { if (lifecycle != Lifecycle.State.RESUMED) { session = null; data = null } }
    LaunchedEffect(session, access) {
        val expected = session ?: return@LaunchedEffect
        try {
            db.invalidationTracker.createFlow("channels", "sources", "profiles", "categories").collect {
                if (store.owner(profile) != expected.owner || !current()) { session = null; data = null; return@collect }
                data = read(expected.identity)
                if (data == null) error = SourcePickerError.UNAVAILABLE
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = SourcePickerError.UNAVAILABLE }
    }
    session?.let { expected ->
        ChannelSourcePicker(data, expected.openingDownTime, busy, error, onChoose = choose@{ selected, play ->
            if (actionInFlight[0] || !current()) return@choose
            actionInFlight[0] = true; busy = true; error = null
            scope.launch {
                try {
                    // Resolve by stable source key, not a stale Room ID after an ordinary refresh.
                    val fresh = read(expected.identity)
                    val variant = fresh?.let { ChannelSourceResolver.resolve(it.group, it.choices).alternatives
                        .singleOrNull { row -> row.stableKey == selected.stableKey } }
                    if (variant == null || channel(variant) == null || session != expected) {
                        error = SourcePickerError.UNAVAILABLE; return@launch
                    }
                    store.savePreferred(profile, expected.owner, expected.identity, variant.stableKey) {
                        current() && session == expected && read(expected.identity)?.let { now ->
                            ChannelSourceResolver.alternatives(now.group).any { it.stableKey == variant.stableKey && it.id == variant.id }
                        } == true && channel(variant) != null
                    }
                    val actual = channel(variant)
                    // Saved choices survive a later playback failure. Never start stale/removed data.
                    if (actual == null || session != expected || !current()) { error = SourcePickerError.UNAVAILABLE; return@launch }
                    session = null; data = null
                    if (play) currentPlay(actual, expected.identity) else closed()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = SourcePickerError.SAVE_FAILED }
                finally { busy = false; actionInFlight[0] = false }
            }
        }, onDismiss = ::dismiss)
    }
    return ChannelSourceActions(play = play@{ identity, exact ->
        trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_REQUESTED)
        if (actionInFlight[0]) { trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_BUSY); return@play }
        if (!current()) { trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_CONTEXT_CHANGED); return@play }
        actionInFlight[0] = true
        scope.launch {
            try {
                val fresh = read(identity)
                val chosen = fresh?.let { ChannelSourceResolver.resolve(it.group, it.choices,
                    exact = exact?.let(CustomizeKeys::channel)).selected }
                val actual = chosen?.let { channel(it) } ?: exact?.takeIf { identity.isEmpty() }?.let { original ->
                    // Unrecognized history/raw entry stays exact; a missing logical choice never
                    // redirects to a vaguely similar service.
                    vm.homeChannel(original.id, profile)?.takeIf { current() && it.streamUrl.isNotBlank() &&
                        it.sourceId == original.sourceId && CustomizeKeys.channel(it) == CustomizeKeys.channel(original) }
                }
                if (actual != null && current()) {
                    trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_DISPATCHED); currentPlay(actual, identity)
                } else trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_MISSING)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { trace(tv.own.owntv.player.LiveEntryEvent.SELECTION_FAILED) }
            finally { actionInFlight[0] = false }
        }
    }, open = open@{ identity, downTime ->
        if (!current() || actionInFlight[0] || session != null) return@open
        actionInFlight[0] = true
        scope.launch {
            try {
                val owner = store.owner(profile) ?: return@launch
                if (!current()) return@launch
                data = null; error = null; session = PickerSession(identity, owner, downTime)
            } finally { actionInFlight[0] = false }
        }
    }, isOpen = session != null)
}
