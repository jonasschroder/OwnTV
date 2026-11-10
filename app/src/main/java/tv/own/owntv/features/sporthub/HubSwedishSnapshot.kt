package tv.own.owntv.features.sporthub

import tv.own.owntv.features.home.*
import java.time.Instant

/** Adapt the existing authorized Swedish reader without replacing its club IDs or result owner. */
internal fun SportSnapshot.toHub(): HubSnapshot = HubSnapshot(competition.id, seasonId, HubProvider.SWEDISH,
    fixtures.map { game -> HubMatch(game.id, competition.id, seasonId,
        HubTeam(game.homeTeam.id, game.homeTeam.name), HubTeam(game.awayTeam.id, game.awayTeam.name), game.kickoff,
        if (game.status == MatchStatus.RESULT_SNAPSHOT) HubMatchStatus.REPORTED_FINAL
        else if (game.faceoff > fetchedAt) HubMatchStatus.SCHEDULED else HubMatchStatus.UNCONFIRMED,
        game.score?.let { HubScore(it.home, it.away, HubResultStage.UNSPECIFIED) }, sourceUpdatedAt?.let(Instant::ofEpochMilli)) },
    Instant.ofEpochMilli(fetchedAt))

internal fun hubSeason(league: HubLeague, now: Instant): String = league.sourceSeason
    ?: now.atZone(HubStockholm).let { (if (it.monthValue < 7 && league.sport == HubSport.ICE_HOCKEY) it.year - 1 else it.year).toString() }

/** Reviewed season bindings expire; the clock must never silently select a newer upstream season. */
internal fun hubBindingCurrent(league: HubLeague, now: Instant): Boolean {
    if (league.provider == HubProvider.SWEDISH) return true
    val start = league.sourceSeason?.substringBefore('-')?.toIntOrNull() ?: return league.access == HubAccess.VERIFIED_PUBLIC
    val date = now.atZone(HubStockholm).toLocalDate()
    return if (league.key == HubLeagues.allsvenskan.key) date.year == start
        else date < java.time.LocalDate.of(start + 1, 7, 1)
}

internal fun hubSwedishCompetition(league: String): Competition? = when (league) {
    SportsCatalog.shl.id -> SportsCatalog.shl
    SportsCatalog.allsvenskan.id -> SportsCatalog.allsvenskan
    else -> null
}
