package tv.own.owntv.features.sporthub

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal const val HUB_MAX_BODY = 2_000_000
internal const val HUB_MAX_MATCHES = 1_000

/** JSON numeric coercion must not turn fractional/overflowing source IDs into another entity. */
internal fun JSONObject.hubId(field: String): Long {
    val raw = get(field)
    require(raw is Int || raw is Long)
    return (raw as Number).toLong().also { require(it > 0) }
}

/** The documented API's explicit UTC time and result stages, not league-name/channel guesses. */
internal fun parseOpenLiga(text: String, league: HubLeague, season: String, now: Instant): HubSnapshot {
    require(league.provider == HubProvider.OPENLIGA && league.access == HubAccess.VERIFIED_PUBLIC)
    require(season.matches(Regex("[0-9]{4}")) && text.length <= HUB_MAX_BODY)
    val rows = JSONArray(text)
    require(rows.length() <= HUB_MAX_MATCHES)
    val issues = mutableSetOf<HubCoverageIssue>()
    val parsed = (0 until rows.length()).mapNotNull { i ->
        runCatching {
            val row = rows.getJSONObject(i)
            require(row.getString("leagueShortcut").equals(league.sourceSlug, ignoreCase = true))
            require(row.get("leagueSeason").toString() == season)
            val id = row.hubId("matchID")
            val at = Instant.parse(row.getString("matchDateTimeUTC"))
            fun team(field: String): HubTeam {
                val obj = row.getJSONObject(field)
                val name = obj.getString("teamName").trim()
                require(name.isNotEmpty() && name.length <= 160 && name.none { it.isISOControl() })
                val short = obj.optString("shortName").trim().takeIf { it.isNotEmpty() && it.length <= 80 && it.none(Char::isISOControl) }
                // Deliberately ignore teamIconUrl: API availability is not an artwork licence.
                return HubTeam(hubTeamId(league.sport, HubProvider.OPENLIGA, obj.hubId("teamId")), name, short)
            }
            val home = team("team1"); val away = team("team2"); require(home.id != away.id)
            val finished = row.get("matchIsFinished") as? Boolean ?: error("Invalid completion flag")
            val score = if (finished) openLigaFinalScore(row.optJSONArray("matchResults")) else null
            if (finished && score == null) issues += HubCoverageIssue.FINAL_WITHOUT_SCORE
            if (!finished && at < now.minusSeconds(4 * 3600)) issues += HubCoverageIssue.PAST_WITHOUT_FINAL
            HubMatch("${league.sport.name}:${HubProvider.OPENLIGA.name}:$id", league.key, season, home, away, at,
                if (finished) HubMatchStatus.REPORTED_FINAL else if (at > now) HubMatchStatus.SCHEDULED else HubMatchStatus.UNCONFIRMED,
                score, row.optString("lastUpdateDateTime").takeIf { it.isNotEmpty() }?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }.onFailure { issues += HubCoverageIssue.INVALID_ROWS }.getOrNull()
    }
    val matches = parsed.groupBy { it.id }.mapNotNull { (_, same) ->
        if (same.distinct().size == 1) same.first() else { issues += HubCoverageIssue.CONFLICTING_IDS; null }
    }.sortedWith(compareBy<HubMatch> { it.startsAt }.thenBy { it.id })
    require(rows.length() == 0 || matches.isNotEmpty())
    return HubSnapshot(league.key, season, HubProvider.OPENLIGA, matches, now, issues)
}

/** Extra time / shootout supersede regulation; never sum period scores or invent zeroes. */
private fun openLigaFinalScore(rows: JSONArray?): HubScore? {
    rows ?: return null
    if (rows.length() > 16) return null
    val known = (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val stage = when (row.optString("resultTypeKind")) {
            "After90Minutes" -> HubResultStage.REGULATION
            "AfterExtraTime" -> HubResultStage.EXTRA_TIME
            "AfterPenalties" -> HubResultStage.PENALTIES
            else -> return@mapNotNull null
        }
        val home = row.opt("pointsTeam1") as? Number ?: return@mapNotNull null
        val away = row.opt("pointsTeam2") as? Number ?: return@mapNotNull null
        if (home.toDouble() != home.toInt().toDouble() || away.toDouble() != away.toInt().toDouble() ||
            home.toInt() !in 0..1_000 || away.toInt() !in 0..1_000) return@mapNotNull null
        HubScore(home.toInt(), away.toInt(), stage)
    }
    val highest = known.maxOfOrNull { it.stage.ordinal } ?: return null
    return known.filter { it.stage.ordinal == highest }.distinct().singleOrNull()
}
