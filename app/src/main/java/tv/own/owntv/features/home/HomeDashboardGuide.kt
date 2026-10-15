package tv.own.owntv.features.home

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import org.koin.compose.koinInject
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.features.live.*

internal data class HomeGuideState(val at: Long, val rows: Map<SwedishRowKey, SwedishRowInfo>)

/** PR #10 cache + stored reader, at most 16 keys and 32 summaries. No provider HTTP fallback.
 * The caller supplies only a visible hero/rail plus small prefetch; context changes clear immediately.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun rememberHomeDashboardGuide(vm: LiveViewModel, profile: Long?, enabled: Boolean,
    keys: List<SwedishRowKey>): HomeGuideState {
    val db: OwnTVDatabase = koinInject()
    val library by vm.homeLibraryContext.collectAsStateWithLifecycle()
    val observed by vm.swedishRowContext.collectAsStateWithLifecycle()
    val context = observed?.takeIf { it.profileId == profile && library.first == profile && it.sourceIds == library.second }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = enabled && lifecycle == Lifecycle.State.RESUMED && context != null
    var revision by remember(context) { mutableIntStateOf(0) }
    val cache = remember(context, revision) { SwedishRowCache() }
    var rows by remember(context, revision, keys) { mutableStateOf<Map<SwedishRowKey, SwedishRowInfo>>(emptyMap()) }
    var at by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, context) {
        if (active) db.invalidationTracker.createFlow("epg_programmes", "channels", "categories", "profiles", "sources")
            .debounce(350).collect { revision++ }
    }
    LaunchedEffect(active, context, cache, keys) {
        if (!active || keys.isEmpty()) return@LaunchedEffect
        delay(100) // rapid remote scrolling cancels before touching SQLite
        while (true) {
            val time = System.currentTimeMillis()
            rows = try { cache.load(keys, time) { requested, moment -> vm.swedishStoredRows(requested, context, moment, db) } }
                catch (timeout: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); emptyMap() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyMap() }
            at = time
            val boundary = rows.values.minOfOrNull { it.expires(time) } ?: (time + 30_000)
            delay(minOf(5_000L, boundary - System.currentTimeMillis()).coerceAtLeast(100))
        }
    }
    return HomeGuideState(at, if (active) rows else emptyMap())
}
