@file:OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package tv.own.owntv.features.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

/** One cancellable local reader. Cached/failed refreshes do not scan the catalog again. */
internal fun discoveryShelves(invalidations: Flow<Unit>, candidateRevisions: StateFlow<Long>, refreshFailed: StateFlow<Boolean>,
    load: suspend () -> DiscoveryShelf): Flow<DiscoveryShelf> {
    val databaseChanges = flow {
        var initial = true
        emitAll(invalidations.debounce {
            val timeout = if (initial) 0L else 750L
            initial = false
            timeout
        })
    }
    val loaded = flow {
        var previous = DiscoveryShelf()
        combine(databaseChanges, candidateRevisions) { _, _ -> Unit }.mapLatest {
            try { load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { previous.copy(failed = true) }
        }.collect { previous = it; emit(it) }
    }
    return combine(loaded, refreshFailed) { shelf, failed -> shelf.copy(failed = shelf.failed || failed) }
}
