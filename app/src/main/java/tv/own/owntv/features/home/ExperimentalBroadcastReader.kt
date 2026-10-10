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
    private val reviewedTerms: (String) -> Boolean = TvmatchenHtml::termsUnchanged) : BroadcastMetadataSource {
    private val mutex = Mutex()

    override suspend fun assignment(fixture: BroadcastFixture): BroadcastAssignment? = mutex.withLock {
        if (!enabled() || state.blocked) return@withLock null
        val now = clock(); val instant = Instant.ofEpochMilli(now)
        state.assignments.removeAll { !it.expiresAt.isAfter(instant) }
        state.assignments.firstNotNullOfOrNull { BroadcastResolver.usable(fixture, it, instant) }?.let { return@withLock it }
        val today = instant.atZone(Stockholm).toLocalDate()
        if (fixture.league != "SHL" || fixture.date !in today..today.plusDays(2) ||
            fixture.faceoff.toEpochMilli() < now - 4 * 60 * 60_000L || fixture.date != fixture.faceoff.atZone(Stockholm).toLocalDate() ||
            ShlTeams.identity(fixture.home) == null || ShlTeams.identity(fixture.away) == null || now < state.retryAt) return@withLock null
        val key = fixtureKey(fixture)
        if (now - (state.attempted[key] ?: 0L) in 0 until interval) return@withLock null
        try {
            currentCoroutineContext().ensureActive()
            if (now - state.windowAt !in 0 until interval) {
                state.windowAt = now; state.requests = 0; state.fixtureRequests = 0
            }
            if (now - state.robotsAt !in 0 until 6 * 60 * 60_000L) {
                val body = page("/robots.txt", 32_768, false)
                if (!BroadcastRobots.permits(body, "/ishockey/shl") || !BroadcastRobots.permits(body, "/page/anvandarvillkor")) deny()
                state.robots = body; state.robotsAt = now; save()
            }
            if (now - state.termsAt !in 0 until 24 * 60 * 60_000L) {
                if (!reviewedTerms(page("/page/anvandarvillkor", 700_000))) deny()
                state.termsAt = now; save()
            }
            if (now - state.listingAt !in 0 until interval) {
                state.index = TvmatchenHtml.fixtureIndex(page("/ishockey/shl", 2_000_000), instant)
                state.listingAt = now; save()
            }
            val candidates = state.index[key].orEmpty()
            if (candidates.size > 2) return@withLock null
            if (state.fixtureRequests + candidates.size > 3 || state.requests + candidates.size > 6) return@withLock null
            val results = mutableListOf<BroadcastAssignment>()
            for (path in candidates) {
                currentCoroutineContext().ensureActive()
                TvmatchenHtml.assignment(page(path, 800_000), path, fixture, instant)?.let { results += it }
            }
            currentCoroutineContext().ensureActive()
            if (!enabled()) return@withLock null
            state.attempted[key] = now
            while (state.attempted.size > 16) state.attempted.remove(state.attempted.minBy { it.value }.key)
            state.failures = 0
            // More than one valid page is ambiguous. Never choose the first on the user's behalf.
            val value = results.singleOrNull()
            if (value != null) {
                state.assignments.removeAll { fixtureKey(it.fixture) == key }
                state.assignments += value
                while (state.assignments.size > 16) state.assignments.removeAt(0)
            }
            save(); value
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            state.failures = (state.failures + 1).coerceAtMost(4)
            state.retryAt = now + interval * (1L shl (state.failures - 1))
            save(); null
        }
    }

    private suspend fun page(path: String, limit: Int, robotsCheck: Boolean = true): String {
        currentCoroutineContext().ensureActive()
        check(enabled() && !state.blocked && state.requests < 6)
        require(path in listOf("/robots.txt", "/page/anvandarvillkor", "/ishockey/shl") ||
            path.matches(Regex("/match/[a-z0-9-]{1,140}-[0-9]{1,10}")))
        if (robotsCheck && !BroadcastRobots.permits(state.robots, path)) deny()
        if (path.startsWith("/match/")) { check(state.fixtureRequests < 3); state.fixtureRequests++ }
        state.requests++; save()
        val (status, body) = pages.read(path, limit)
        if (status in listOf(401, 403, 429)) deny()
        // No redirects, auth, robots fallback, challenge interpretation or cache on non-200 responses.
        require(status == 200 && body.toByteArray().size <= limit)
        return body
    }
    private fun deny(): Nothing { state.blocked = true; state.assignments.clear(); save(); error("broadcast access requires review") }
    companion object { const val interval = 2 * 60 * 60_000L }
}

/** Exact fixture key: manual or cached assignments do not carry over to rematches/reschedules. */
internal fun fixtureKey(fixture: BroadcastFixture): String = listOf(fixture.league,
    ShlTeams.identity(fixture.home) ?: teamTokens(fixture.home).joinToString("_"),
    ShlTeams.identity(fixture.away) ?: teamTokens(fixture.away).joinToString("_"),
    fixture.date.toString(), fixture.faceoff.toEpochMilli().toString()).joinToString("|")
