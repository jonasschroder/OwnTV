package tv.own.owntv.features.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.metadata.TrendingFeedPage

/** Shared by Core's import worker and visible discovery. Single flight, short coalescing, no job scope. */
internal class BoundedTrendingProvider(private val upstream: MetadataProvider,
    private val now: () -> Long = System::currentTimeMillis) : MetadataProvider by upstream {
    private val mutex = Mutex()
    private val pages = linkedMapOf<Pair<MetadataType, Int>, Pair<Long, TrendingFeedPage?>>()
    override suspend fun trendingPage(type: MetadataType, page: Int): TrendingFeedPage? = mutex.withLock {
        require(type in listOf(MetadataType.MOVIE, MetadataType.TV) && page in 1..5)
        val key = type to page
        pages[key]?.takeIf { now() - it.first in 0 until 60_000 }?.let { return@withLock it.second }
        val value = try {
            withTimeout(15_000) { withMetadataNetwork { upstream.trendingPage(type, page) } }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive(); null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { currentCoroutineContext().ensureActive(); null }
        currentCoroutineContext().ensureActive()
        val verified = value?.takeIf { it.page == page && it.candidates.size <= 20 && it.candidates.all { candidate -> candidate.type == type && candidate.tmdbId > 0 } }
        pages[key] = now() to verified
        while (pages.size > 10) pages.remove(pages.keys.first())
        verified
    }
}
