@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package tv.own.owntv.features.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.EpgNowNext

internal data class SwedishGuideSnapshot(val context: SwedishRowContext, val channel: SwedishRowKey,
    val revision: Int, val guide: EpgNowNext?) {
    fun forChannel(actual: ChannelEntity?, expected: SwedishRowContext?, at: Long): EpgNowNext? {
        if (expected != context || actual == null || channel != SwedishRowKey(actual.id, actual.sourceId, CustomizeKeys.channel(actual))) return null
        val data = guide ?: return null
        val now = data.now?.takeIf { it.startMs <= at && at < it.stopMs }
            ?: data.next?.takeIf { it.startMs <= at && at < it.stopMs }
        return data.copy(now = now, next = data.next?.takeIf { it.startMs > at && it.startMs < it.stopMs },
            upcoming = data.upcoming.filter { it.startMs > at }, nowDetails = data.nowDetails.takeIf { now == data.now })
    }
}
private data class FocusedGuideRequest(val context: SwedishRowContext?, val channel: ChannelEntity?, val revision: Int)

/** Same single focused-channel reader: clear immediately, debounce IO, cancel superseded results.
 * The row reader never calls this/provider fallback; the existing selected pane still can.
 */
internal fun swedishFocusedGuide(context: Flow<SwedishRowContext?>, channel: Flow<ChannelEntity?>, revision: Flow<Int>,
    resetContext: () -> Unit,
    read: suspend (ChannelEntity, SwedishRowContext, Boolean) -> EpgNowNext?): Flow<SwedishGuideSnapshot?> {
    var previous: FocusedGuideRequest? = null
    return combine(context, channel, revision) { c, ch, r -> FocusedGuideRequest(c, ch, r) }
        .distinctUntilChanged().transformLatest { request ->
            emit(null)
            val c = request.context ?: return@transformLatest
            val ch = request.channel?.takeIf { it.sourceId in c.sourceIds } ?: return@transformLatest
            delay(350)
            // Core's shared reader caches by numeric channel ID, not profile/mapping/offset.
            // Invalidate the whole previous context, including channels revisited later.
            if (previous?.context != c) resetContext()
            val invalidate = previous?.context != c || previous?.revision != request.revision
            val data = try { read(ch, c, invalidate) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            previous = request
            emit(SwedishGuideSnapshot(c, SwedishRowKey(ch.id, ch.sourceId, CustomizeKeys.channel(ch)), request.revision, data))
        }
}
