package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test

class HockeyDiagnosticsTest {
    private class Store : HockeyStorage {
        val numbers = mutableMapOf<String, Long>(); val texts = mutableMapOf<String, String>()
        var fail = false
        override fun enabled() = true
        override fun blocked() = false
        override fun number(key: String) = numbers[key] ?: 0
        override fun journal(values: Map<String, Long>) { if (fail) error("Private exception text"); numbers.putAll(values) }
        override fun text(key: String) = texts[key]
        override fun text(key: String, value: String) { if (fail) error("Private exception text"); texts[key] = value }
        override fun block(code: Int?) = Unit
        override fun cache(key: String): String? = null
        override fun cache(key: String, value: String) = Unit
    }
    @Test fun actualAttemptsAndStoredFixtureCountsAreNotOverwrittenByCachePublicationOrStandings() {
        val store = Store(); val diagnostic = HockeyDiagnostics(store); val league = SportsCatalog.shl
        assertNull(diagnostic.lastAttempt(league))
        diagnostic.record(HockeyTrace(league.id, HockeyStage.SCHEDULE, HockeyOutcome.REQUESTED, 100))
        diagnostic.record(HockeyTrace(league.id, HockeyStage.SCHEDULE, HockeyOutcome.VALID_SUCCESS, 200, count = 364))
        assertEquals(364, diagnostic.cachedCount(league))
        diagnostic.record(HockeyTrace(league.id, HockeyStage.TABLE, HockeyOutcome.VALID_SUCCESS, 300, count = 14))
        diagnostic.record(HockeyTrace(league.id, HockeyStage.PUBLICATION, HockeyOutcome.PUBLISHED, 400, count = 364))
        assertEquals(100L, HockeyDiagnostics(store).lastAttempt(league))
        assertEquals(364, diagnostic.cachedCount(league))
    }
    @Test fun legacyValidatedCacheSuppliesItsOriginalFetchTimeWithoutInventingANewSuccess() {
        val store = Store(); val diagnostic = HockeyDiagnostics(store)
        diagnostic.record(HockeyTrace(SportsCatalog.shl.id, HockeyStage.CACHE, HockeyOutcome.CACHE_HIT, 200, count = 364, cacheAge = 100))
        assertEquals(100L, diagnostic.lastSuccess(SportsCatalog.shl))
        assertEquals(364, diagnostic.cachedCount(SportsCatalog.shl))
        diagnostic.record(HockeyTrace(SportsCatalog.shl.id, HockeyStage.CACHE, HockeyOutcome.CACHE_HIT, 300, count = 364, cacheAge = 200))
        assertEquals(100L, HockeyDiagnostics(store).lastSuccess(SportsCatalog.shl))
    }
    @Test fun boundedJournalSurvivesRecreationWithoutLosingLastSuccessfulFetch() {
        val store = Store(); val diagnostic = HockeyDiagnostics(store); val league = SportsCatalog.shl
        diagnostic.record(HockeyTrace(league.id, HockeyStage.SCHEDULE, HockeyOutcome.VALID_SUCCESS, 100, count = 364))
        repeat(100) { diagnostic.record(HockeyTrace(league.id, HockeyStage.CACHE, HockeyOutcome.CACHE_HIT, 200L + it, count = 364, cacheAge = it.toLong())) }
        val restored = HockeyDiagnostics(store)
        assertEquals(24, restored.events(league).size)
        assertEquals(100L, restored.lastSuccess(league))
        assertEquals(364, restored.cachedCount(league))
        assertTrue(restored.report().contains("source=SWEHOCKEY"))
        assertTrue(restored.events(SportsCatalog.allsvenskan).isEmpty())
    }
    @Test fun malformedPersistedMessagesNeverReachTheExport() {
        val store = Store()
        store.texts["hockey-trace:" + SportsCatalog.shl.id] = """[{"stage":"https://private-token","outcome":"VALID_SUCCESS","at":1}]"""
        val diagnostic = HockeyDiagnostics(store)
        assertTrue(diagnostic.events(SportsCatalog.shl).isEmpty())
        assertFalse(diagnostic.report().contains("private"))
        assertThrows(IllegalArgumentException::class.java) { HockeyTrace("private-league", HockeyStage.CACHE, HockeyOutcome.CACHE_HIT, 1) }
        assertThrows(IllegalArgumentException::class.java) { HockeyTrace(SportsCatalog.shl.id, HockeyStage.CACHE, HockeyOutcome.CACHE_HIT, 1, count = 401) }
    }
    @Test fun persistenceFailureRemainsVisibleWithoutFailingTheApplicationOrLoggingExceptionText() {
        val store = Store().apply { fail = true }
        val diagnostic = HockeyDiagnostics(store)
        diagnostic.record(HockeyTrace(SportsCatalog.shl.id, HockeyStage.SCHEDULE, HockeyOutcome.VALID_SUCCESS, 100, count = 364))
        assertEquals(HockeyOutcome.CACHE_WRITE_FAILED, diagnostic.events(SportsCatalog.shl).last().outcome)
        assertFalse(diagnostic.report().contains("Private"))
    }
}
