package tv.own.owntv.features.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** No producer/observer survives its last legacy Home consumer. A later consumer starts fresh. */
internal suspend fun whileHomeObserved(count: StateFlow<Int>, observe: suspend CoroutineScope.() -> Unit) {
    count.map { it > 0 }.distinctUntilChanged().collectLatest { active ->
        if (active) coroutineScope { observe() }
    }
}
