package tv.own.owntv.features.home

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface BroadcastPageReader { suspend fun read(path: String, limit: Int): Pair<Int, String> }
internal data class BroadcastReadState(
    var blocked: Boolean = false, var windowAt: Long = 0, var requests: Int = 0, var fixtureRequests: Int = 0,
    var robotsAt: Long = 0, var robots: String = "", var termsAt: Long = 0,
    var listingAt: Long = 0, var index: Map<String, List<String>> = emptyMap(),
    var failures: Int = 0, var retryAt: Long = 0,
    val assignments: MutableList<BroadcastAssignment> = mutableListOf(),
    val attempted: MutableMap<String, Long> = mutableMapOf(),
)

/** Personal public-page experiment. Persist budgets BEFORE requests, including cancelled requests. */
internal class ExperimentalBroadcastReader(private val pages: BroadcastPageReader,
    private val enabled: () -> Boolean, private val state: BroadcastReadState,
    private val save: () -> Unit, private val clock: () -> Long = System::currentTimeMillis,
    private val reviewedTerms: (String) -> Boolean = TvmatchenHtml::termsUnchanged,
    private val report: (BroadcastLookupStatus) -> Unit = {}) : BroadcastMetadataSource {
    private val mutex = Mutex()
    private var stage = BroadcastLookupStage.ELIGIBILITY
    private fun status(outcome: BroadcastLookupOutcome, http: Int? = null) = report(
        BroadcastLookupStatus(outcome, clock(), stage, state.retryAt.takeIf { it > clock() }, http))
    private fun persist() {
        try { save() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw BroadcastLookupFailure(BroadcastLookupOutcome.CACHE_ERROR) }
    }

    override suspend fun assignment(fixture: BroadcastFixture): BroadcastAssignment? = mutex.withLock {
        stage = BroadcastLookupStage.ELIGIBILITY
        if (!enabled()) { status(BroadcastLookupOutcome.DISABLED); return@withLock null }
        if (state.blocked) { status(BroadcastLookupOutcome.ACCESS_BLOCKED); return@withLock null }
        val now = clock(); val instant = Instant.ofEpochMilli(now)
        stage = BroadcastLookupStage.CACHE
        state.assignments.removeAll { !it.expiresAt.isAfter(instant) }
        state.assignments.firstNotNullOfOrNull { BroadcastResolver.usable(fixture, it, instant) }?.let { cached ->
            if (cached.channels.none { it.linear }) {
                status(BroadcastLookupOutcome.NO_LINEAR_CHANNEL); return@withLock null
            }
            status(BroadcastLookupOutcome.CACHE_HIT); return@withLock cached
        }
        stage = BroadcastLookupStage.ELIGIBILITY
        val today = instant.atZone(Stockholm).toLocalDate()
        if (fixture.league != "SHL" || fixture.date != fixture.faceoff.atZone(Stockholm).toLocalDate() ||
            ShlTeams.identity(fixture.home) == null || ShlTeams.identity(fixture.away) == null ||
            ShlTeams.identity(fixture.home) == ShlTeams.identity(fixture.away)) {
            status(BroadcastLookupOutcome.INVALID_FIXTURE); return@withLock null
        }
        if (fixture.date !in today..today.plusDays(2) || fixture.faceoff.toEpochMilli() < now - 4 * 60 * 60_000L) {
            status(BroadcastLookupOutcome.OUTSIDE_WINDOW); return@withLock null
        }
        if (now < state.retryAt) { status(BroadcastLookupOutcome.BACKOFF); return@withLock null }
        val key = fixtureKey(fixture)
        if (now - (state.attempted[key] ?: 0L) in 0 until interval) {
            status(BroadcastLookupOutcome.RATE_LIMITED); return@withLock null
        }
        try {
            currentCoroutineContext().ensureActive()
            if (now - state.windowAt !in 0 until interval) {
                state.windowAt = now; state.requests = 0; state.fixtureRequests = 0
            }
            if (now - state.robotsAt !in 0 until 6 * 60 * 60_000L) {
                val body = page("/robots.txt", 32_768, false)
                if (!BroadcastRobots.permits(body, "/ishockey/shl") || !BroadcastRobots.permits(body, "/page/anvandarvillkor")) deny()
                state.robots = body; state.robotsAt = now; persist()
            }
            if (now - state.termsAt !in 0 until 24 * 60 * 60_000L) {
                if (!reviewedTerms(page("/page/anvandarvillkor", 700_000))) deny()
                state.termsAt = now; persist()
            }
            if (now - state.listingAt !in 0 until interval) {
                state.index = TvmatchenHtml.fixtureIndex(page("/ishockey/shl", 2_000_000), instant)
                state.listingAt = now; persist()
            }
            val candidates = state.index[key].orEmpty()
            if (candidates.size > 2) { status(BroadcastLookupOutcome.AMBIGUOUS); return@withLock null }
            if (state.fixtureRequests + candidates.size > 3 || state.requests + candidates.size > 6) {
                status(BroadcastLookupOutcome.RATE_LIMITED); return@withLock null
            }
            val results = mutableListOf<BroadcastAssignment>()
            for (path in candidates) {
                currentCoroutineContext().ensureActive()
                TvmatchenHtml.assignment(page(path, 800_000), path, fixture, instant)?.let { results += it }
            }
            currentCoroutineContext().ensureActive()
            if (!enabled()) { status(BroadcastLookupOutcome.DISABLED); return@withLock null }
            state.attempted[key] = now
            while (state.attempted.size > 16) state.attempted.remove(state.attempted.minBy { it.value }.key)
            state.failures = 0
            // More than one valid page is ambiguous. Never choose the first on the user's behalf.
            val value = results.singleOrNull()?.takeIf { it.channels.any { channel -> channel.linear } }
            if (value != null) {
                state.assignments.removeAll { fixtureKey(it.fixture) == key }
                state.assignments += value
                while (state.assignments.size > 16) state.assignments.removeAt(0)
            }
            persist()
            status(when { value != null -> BroadcastLookupOutcome.VERIFIED
                results.size > 1 -> BroadcastLookupOutcome.AMBIGUOUS
                results.singleOrNull()?.channels?.none { it.linear } == true -> BroadcastLookupOutcome.NO_LINEAR_CHANNEL
                candidates.isEmpty() -> BroadcastLookupOutcome.NOT_FOUND
                else -> BroadcastLookupOutcome.FIELDS_REJECTED })
            value
        } catch (cancelled: CancellationException) { status(BroadcastLookupOutcome.CANCELLED); throw cancelled }
        catch (error: Exception) {
            state.failures = (state.failures + 1).coerceAtMost(4)
            state.retryAt = now + interval * (1L shl (state.failures - 1))
            val failure = error as? BroadcastLookupFailure
            try { persist() } catch (_: BroadcastLookupFailure) {
                status(BroadcastLookupOutcome.CACHE_ERROR); return@withLock null
            }
            status(failure?.outcome ?: if (error is java.io.IOException) BroadcastLookupOutcome.NETWORK_ERROR else BroadcastLookupOutcome.FORMAT_ERROR,
                failure?.http)
            null
        }
    }

    private suspend fun page(path: String, limit: Int, robotsCheck: Boolean = true): String {
        currentCoroutineContext().ensureActive()
        if (!enabled()) throw BroadcastLookupFailure(BroadcastLookupOutcome.DISABLED)
        if (state.blocked) throw BroadcastLookupFailure(BroadcastLookupOutcome.ACCESS_BLOCKED)
        if (state.requests >= 6) throw BroadcastLookupFailure(BroadcastLookupOutcome.RATE_LIMITED)
        stage = when (path) { "/robots.txt" -> BroadcastLookupStage.ROBOTS
            "/page/anvandarvillkor" -> BroadcastLookupStage.TERMS
            "/ishockey/shl" -> BroadcastLookupStage.LISTING
            else -> BroadcastLookupStage.FIXTURE }
        require(path in listOf("/robots.txt", "/page/anvandarvillkor", "/ishockey/shl") ||
            path.matches(Regex("/match/[a-z0-9-]{1,140}-[0-9]{1,10}")))
        if (robotsCheck && !BroadcastRobots.permits(state.robots, path)) deny()
        if (path.startsWith("/match/")) { check(state.fixtureRequests < 3); state.fixtureRequests++ }
        state.requests++; persist()
        val (status, body) = pages.read(path, limit)
        if (status in listOf(401, 403, 429)) deny(status)
        // No redirects, auth, robots fallback, challenge interpretation or cache on non-200 responses.
        if (status != 200) throw BroadcastLookupFailure(BroadcastLookupOutcome.HTTP_ERROR, status)
        if (body.toByteArray().size > limit) throw BroadcastLookupFailure(BroadcastLookupOutcome.FORMAT_ERROR)
        return body
    }
    private fun deny(http: Int? = null): Nothing {
        state.blocked = true; state.assignments.clear(); persist()
        throw BroadcastLookupFailure(BroadcastLookupOutcome.ACCESS_BLOCKED, http)
    }
    companion object { const val interval = 2 * 60 * 60_000L }
}

/** Exact fixture key: manual or cached assignments do not carry over to rematches/reschedules. */
internal fun fixtureKey(fixture: BroadcastFixture): String = listOf(fixture.league,
    ShlTeams.identity(fixture.home) ?: teamTokens(fixture.home).joinToString("_"),
    ShlTeams.identity(fixture.away) ?: teamTokens(fixture.away).joinToString("_"),
    fixture.date.toString(), fixture.faceoff.toEpochMilli().toString()).joinToString("|")
