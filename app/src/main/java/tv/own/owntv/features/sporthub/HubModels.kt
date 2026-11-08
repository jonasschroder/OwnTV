package tv.own.owntv.features.sporthub

import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.Locale

/** Public sports facts only. IPTV entities, URLs, credentials and profile choices never enter this cache. */
internal enum class HubSport {
    ICE_HOCKEY, FOOTBALL, BASKETBALL, TENNIS, CRICKET, RACING, AMERICAN_FOOTBALL,
    MMA, BASEBALL, GOLF, RUGBY, RUGBY_LEAGUE, AUSSIE_RULES, VOLLEYBALL, LACROSSE;
    val preferencePrefix: String get() = when (this) {
        ICE_HOCKEY -> "hockey"
        else -> name.lowercase(Locale.ROOT).replace('_', '-')
    }
}
internal enum class HubProvider { SWEDISH, OPENLIGA, EVENTS24, PUCKAPI, API_SPORTS, ESPN, NONE }
internal enum class HubAccess { EXISTING_READER, VERIFIED_PUBLIC, AUTH_REQUIRED, PERMISSION_REQUIRED, UNVERIFIED }
internal enum class HubMatchStatus { SCHEDULED, REPORTED_FINAL, UNCONFIRMED }
internal enum class HubResultStage { UNSPECIFIED, REGULATION, EXTRA_TIME, PENALTIES }
internal enum class HubTeamIdentity { PROVIDER_ID, NAME_DERIVED }
internal enum class HubIssue { DISABLED, AUTH_REQUIRED, ACCESS_DENIED, RATE_LIMITED, WAITING, UNAVAILABLE, INVALID_DATA }
internal enum class HubCoverageIssue { INVALID_ROWS, CONFLICTING_IDS, PAST_WITHOUT_FINAL, FINAL_WITHOUT_SCORE, TIME_CONFLICT }
internal enum class HubFreshness { FRESH, STALE, INVALID_CLOCK }

internal data class HubLeague(val key: String, val sport: HubSport, val label: Int,
    val provider: HubProvider, val access: HubAccess, val sourceSlug: String? = null,
    val sourceName: String? = null, val country: String? = null, val sourceSeason: String? = null)
internal data class HubTeam(val id: String, val name: String, val shortName: String? = null,
    val identity: HubTeamIdentity = HubTeamIdentity.PROVIDER_ID)
internal data class HubScore(val home: Int, val away: Int, val stage: HubResultStage)
internal data class HubMatch(val id: String, val league: String, val season: String,
    val home: HubTeam, val away: HubTeam, val startsAt: Instant, val status: HubMatchStatus,
    val score: HubScore?, val sourceUpdatedAt: Instant? = null) {
    fun on(day: LocalDate): Boolean = startsAt.atZone(HubStockholm).toLocalDate() == day
}
internal val HubStockholm: ZoneId = ZoneId.of("Europe/Stockholm")
internal data class HubWindow(val from: LocalDate, val to: LocalDate) {
    init { require(from < to) }
    fun allowed(now: Instant): Boolean {
        val today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return from >= today.minusDays(7) && to <= today.plusDays(30)
    }
    companion object {
        fun upcoming(now: Instant): HubWindow {
            val today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
            return HubWindow(today.minusDays(1), today.plusDays(7))
        }
        fun stockholmDay(day: LocalDate): HubWindow = HubWindow(
            day.atStartOfDay(HubStockholm).toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
            day.plusDays(1).atStartOfDay(HubStockholm).toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1))
    }
}
internal const val HUB_FRESH_MS = 15 * 60_000L
internal data class HubSnapshot(val league: String, val season: String, val provider: HubProvider,
    val matches: List<HubMatch>, val fetchedAt: Instant, val coverage: Set<HubCoverageIssue> = emptySet(),
    val hasMore: Boolean = false, val from: LocalDate? = null, val to: LocalDate? = null,
    val quota: HubQuota? = null) {
    val teams: List<HubTeam> get() = matches.flatMap { listOf(it.home, it.away) }.distinctBy { it.id }
    fun freshness(now: Instant): HubFreshness {
        return when { now < fetchedAt -> HubFreshness.INVALID_CLOCK
            Duration.between(fetchedAt, now) < Duration.ofMillis(HUB_FRESH_MS) -> HubFreshness.FRESH
            else -> HubFreshness.STALE }
    }
}
internal data class HubQuota(val day: LocalDate, val month: YearMonth, val requestsRemaining: Int, val rowsRemaining: Int)
internal data class HubRead(val snapshot: HubSnapshot?, val issue: HubIssue? = null, val cached: Boolean = false,
    val retryAt: Instant? = null) {
    fun freshness(now: Instant): HubFreshness? = snapshot?.let {
        if (issue != null && it.freshness(now) != HubFreshness.INVALID_CLOCK) HubFreshness.STALE else it.freshness(now)
    }
}

/** Source IDs are namespaced by sport/provider, never by season or league membership. */
internal fun hubTeamId(sport: HubSport, provider: HubProvider, sourceId: Long): String {
    require(sourceId > 0)
    return sport.preferencePrefix + ":" + provider.name.lowercase(Locale.ROOT) + ":" + sourceId
}

internal fun hubCalendarDays(matches: List<HubMatch>, today: LocalDate): List<LocalDate> =
    (matches.map { it.startsAt.atZone(HubStockholm).toLocalDate() } + today).distinct().sorted()
