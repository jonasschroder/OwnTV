package tv.own.owntv.features.sporthub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.own.owntv.features.home.SportPreferences
import java.time.Instant

/** Already-read public facts only. No HTTP, profile choices, IPTV entities or background work. */
internal object HubSessionSnapshots {
    private val current = MutableStateFlow<Map<String,HubRead>>(emptyMap())
    val reads = current.asStateFlow()
    fun publish(read: HubRead) {
        val snapshot = read.snapshot ?: return
        val league = HubLeagues.find(snapshot.league) ?: return
        if (league.provider == HubProvider.SWEDISH || snapshot.provider != league.provider) return
        current.update { it + (league.key to read) }
    }
}
internal data class HubOverviewMatch(val match: HubMatch, val source: HubRead)
internal fun hubOverviewMatches(reads: Map<String,HubRead>, preferences: SportPreferences, now: Instant): List<HubOverviewMatch> {
    val follows = preferences.hubTeams.map { it.id }.toSet()
    return reads.values.filter { it.snapshot?.provider in preferences.hubProviders }.flatMap { read ->
        val snapshot = read.snapshot ?: return@flatMap emptyList()
        val league = HubLeagues.find(snapshot.league) ?: return@flatMap emptyList()
        if (snapshot.provider != league.provider || !hubBindingCurrent(league,now) || read.freshness(now) == HubFreshness.INVALID_CLOCK) return@flatMap emptyList()
        snapshot.matches.filter { (it.home.id in follows || it.away.id in follows) &&
            (it.startsAt >= now || it.on(now.atZone(HubStockholm).toLocalDate())) }.map { HubOverviewMatch(it,read) }
    }.distinctBy { it.match.id }.sortedBy { it.match.startsAt }.take(12)
}
