package tv.own.owntv.features.sporthub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

internal data class HubHttp(val status: Int, val body: String, val retryAfterMs: Long? = null, val receivedAt: Instant? = null)
internal fun interface HubTransport { suspend fun fetch(league: HubLeague, season: String, now: Instant, window: HubWindow): HubHttp }
internal interface HubCache { suspend fun read(key: String): HubCacheEntry?; suspend fun write(key: String, value: HubCacheEntry) }
internal data class HubCacheEntry(val snapshot: HubSnapshot?, val attemptedAt: Instant, val retryAt: Instant, val issue: HubIssue?)

/** Demand-driven, public-only cache. No timer, profile data, background discovery or focus I/O. */
internal class HubSourceReader(private val transport: HubTransport, private val cache: HubCache) {
    private val mutex = Mutex()
    suspend fun read(league: HubLeague, season: String, now: Instant, allowed: () -> Boolean,
        window: HubWindow = HubWindow.upcoming(now)): HubRead = mutex.withLock {
        if (!allowed()) return@withLock HubRead(null, HubIssue.DISABLED)
        if (league.access != HubAccess.VERIFIED_PUBLIC || league.provider !in setOf(HubProvider.OPENLIGA, HubProvider.EVENTS24))
            return@withLock HubRead(null, if (league.access == HubAccess.AUTH_REQUIRED) HubIssue.AUTH_REQUIRED else HubIssue.ACCESS_DENIED)
        require(season.matches(Regex("[0-9]{4}(?:-[0-9]{2,4})?")))
        if (league.provider == HubProvider.EVENTS24 && !window.allowed(now)) return@withLock HubRead(null, HubIssue.INVALID_DATA)
        val key = league.key + ":" + season + if (league.provider == HubProvider.EVENTS24) ":${window.from}:${window.to}" else ""
        val old = cache.read(key)?.takeIf { entry -> entry.snapshot?.let {
            it.league == league.key && it.season == season && it.provider == league.provider
        } != false }
        if (!allowed()) return@withLock HubRead(null, HubIssue.DISABLED)
        val validOld = old?.snapshot?.takeUnless { it.freshness(now) == HubFreshness.INVALID_CLOCK }
        if (old != null && (now < old.retryAt || now < old.attemptedAt))
            return@withLock HubRead(validOld, old.issue ?: HubIssue.INVALID_DATA.takeIf { now < old.attemptedAt }, cached = true, retryAt = old.retryAt)
        val providerBlock = cache.read(league.provider.name)
        if (providerBlock != null && (now < providerBlock.retryAt || now < providerBlock.attemptedAt))
            return@withLock HubRead(validOld, if (now < providerBlock.attemptedAt) HubIssue.INVALID_DATA else providerBlock.issue ?: HubIssue.WAITING,
                cached = true, retryAt = providerBlock.retryAt)
        val result = try { transport.fetch(league, season, now, window) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { HubHttp(0, "") }
        currentCoroutineContext().ensureActive()
        if (!allowed()) return@withLock HubRead(null, HubIssue.DISABLED)
        val received = result.receivedAt ?: now
        if (received < now) return@withLock HubRead(null, HubIssue.INVALID_DATA)
        var issue: HubIssue? = when (result.status) {
            200 -> null
            401 -> HubIssue.AUTH_REQUIRED
            403 -> HubIssue.ACCESS_DENIED
            429 -> HubIssue.RATE_LIMITED
            else -> HubIssue.UNAVAILABLE
        }
        val next = if (issue == null) runCatching {
            when (league.provider) {
                HubProvider.OPENLIGA -> parseOpenLiga(result.body, league, season, received)
                HubProvider.EVENTS24 -> parseEvents24(result.body, league, season, received, window)
                else -> error("Unsupported sports provider")
            }
        }
            .onFailure { issue = HubIssue.INVALID_DATA }.getOrNull() else null
        val delay = when (issue) {
            null -> if (league.provider == HubProvider.EVENTS24) 86_400_000L else HUB_FRESH_MS
            HubIssue.AUTH_REQUIRED, HubIssue.ACCESS_DENIED -> Long.MAX_VALUE
            HubIssue.RATE_LIMITED -> (result.retryAfterMs ?: 60_000L).coerceAtLeast(60_000L)
            else -> 60_000L
        }
        val retryAt = if (delay == Long.MAX_VALUE) Instant.MAX else runCatching { received.plusMillis(delay) }.getOrDefault(Instant.MAX)
        if (!allowed()) return@withLock HubRead(null, HubIssue.DISABLED)
        val retained = if (issue == HubIssue.AUTH_REQUIRED || issue == HubIssue.ACCESS_DENIED) null else next ?: validOld
        if (issue in setOf(HubIssue.AUTH_REQUIRED, HubIssue.ACCESS_DENIED, HubIssue.RATE_LIMITED))
            cache.write(league.provider.name, HubCacheEntry(null, received, retryAt, issue))
        else if (league.provider == HubProvider.EVENTS24) {
            val quota = next?.quota
            val monthEnd = quota?.takeIf { it.rowsRemaining < 100 }?.month?.plusMonths(1)?.atDay(1)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()
            val dayEnd = quota?.takeIf { it.requestsRemaining < 2 }?.day?.plusDays(1)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()
            val waitUntil = maxOf(received.plusSeconds(7), monthEnd ?: received, dayEnd ?: received)
            cache.write(league.provider.name, HubCacheEntry(null, received, waitUntil,
                HubIssue.RATE_LIMITED.takeIf { monthEnd != null || dayEnd != null }))
        }
        cache.write(key, HubCacheEntry(retained, received, retryAt, issue))
        currentCoroutineContext().ensureActive()
        if (!allowed()) return@withLock HubRead(null, HubIssue.DISABLED)
        HubRead(retained, issue, cached = next == null && retained != null, retryAt = retryAt)
    }
}
