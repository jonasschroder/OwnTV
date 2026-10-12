package tv.own.owntv.features.metadata

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call

/** Core's HTTP reader is synchronous. Link its actual calls to the visible request's cancellation. */
internal class MetadataNetwork {
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    @Volatile private var cancelled = false
    fun add(call: Call) { calls.add(call); if (cancelled) call.cancel() }
    fun remove(call: Call) { calls.remove(call) }
    fun cancel() { cancelled = true; calls.forEach(Call::cancel); calls.clear() }
    companion object { val current = ThreadLocal<MetadataNetwork?>() }
}

/** One temporary child, not a service/poller; cancellation closes sockets even during blocking IO. */
internal suspend fun <T> withMetadataNetwork(operation: suspend () -> T): T = coroutineScope {
    val network = MetadataNetwork()
    val observer = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { network.cancel() }
    }
    try { withContext(MetadataNetwork.current.asContextElement(network)) { operation() } }
    finally { network.cancel(); observer.cancel() }
}
