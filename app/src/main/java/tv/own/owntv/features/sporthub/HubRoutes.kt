package tv.own.owntv.features.sporthub

import java.time.Instant
import java.time.LocalDate

internal enum class HubPage { DISCOVER, SPORTS, CALENDAR, MINE, LEAGUE, TEAM, MATCH, TABLE, SOURCES }
internal data class HubRoute(val page: HubPage, val league: String? = null, val entity: String? = null) {
    val key get() = listOf(page.name, league.orEmpty(), entity.orEmpty()).joinToString("/")
    companion object {
        fun decode(key: String): HubRoute? = runCatching {
            require(key.length <= 400)
            val parts = key.split('/'); require(parts.size == 3)
            HubRoute(HubPage.valueOf(parts[0]), parts[1].takeIf(String::isNotEmpty), parts[2].takeIf(String::isNotEmpty))
        }.getOrNull()
    }
}
internal fun hubTeamMatches(matches: List<HubMatch>, team: String): List<HubMatch> =
    matches.filter { it.home.id == team || it.away.id == team }.distinctBy { it.id }.sortedBy { it.startsAt }
internal fun hubDayMatches(matches: List<HubMatch>, day: LocalDate): List<HubMatch> =
    matches.filter { it.on(day) }.distinctBy { it.id }.sortedBy { it.startsAt }
internal fun hubCurrentMatch(snapshot: HubSnapshot?, id: String?, now: Instant): HubMatch? =
    snapshot?.takeIf { it.freshness(now) != HubFreshness.INVALID_CLOCK }?.matches?.singleOrNull { it.id == id }

/** Saved routes never carry channels, credentials or provider URLs. */
internal fun hubPush(history: String, route: HubRoute): String =
    (history.split('\n').filter(String::isNotEmpty).takeLast(7) + route.key).joinToString("\n")
internal fun hubPop(history: String): String? = history.substringBeforeLast('\n', "").takeIf(String::isNotEmpty)
