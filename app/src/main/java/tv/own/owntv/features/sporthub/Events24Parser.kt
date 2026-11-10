package tv.own.owntv.features.sporthub

import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

internal val events24Sports = mapOf(HubSport.FOOTBALL to 1, HubSport.BASKETBALL to 2, HubSport.ICE_HOCKEY to 3,
    HubSport.VOLLEYBALL to 4, HubSport.RUGBY to 5, HubSport.BASEBALL to 8,
    HubSport.AMERICAN_FOOTBALL to 9, HubSport.CRICKET to 11, HubSport.AUSSIE_RULES to 21, HubSport.LACROSSE to 22)
private val combining = Regex("\\p{M}+")
private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")
internal fun hubTeamNameKey(name: String): String = combining.replace(Normalizer.normalize(name, Normalizer.Form.NFKD), "")
    .lowercase(Locale.ROOT).replace(wordSeparators, " ").trim()

/** This API publishes names, not team IDs. Do not present derived keys as native provider IDs. */
internal fun events24Team(name: String, sport: HubSport, country: String): HubTeam {
    val clean = name.trim()
    require(clean.isNotEmpty() && clean.length <= 160 && clean.none(Char::isISOControl))
    val input = country + ":" + hubTeamNameKey(clean)
    require(input.length > country.length + 1)
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).take(16)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    return HubTeam(sport.preferencePrefix + ":events24:" + digest, clean, identity = HubTeamIdentity.NAME_DERIVED)
}

internal fun parseEvents24(text: String, league: HubLeague, season: String, now: Instant,
    window: HubWindow = HubWindow.upcoming(now)): HubSnapshot {
    require(league.provider == HubProvider.EVENTS24 && league.access == HubAccess.VERIFIED_PUBLIC)
    require(league.sourceSeason == null || season == league.sourceSeason)
    val country = requireNotNull(league.country)
    require(text.length <= HUB_MAX_BODY && league.sourceName != null && country.matches(Regex("[A-Z]{2}")) && window.allowed(now))
    val expectedId = league.sourceSlug?.toLongOrNull() ?: error("Missing tournament binding")
    val root = JSONObject(text)
    val rows = root.getJSONArray("data"); require(rows.length() <= 100)
    val issues = mutableSetOf<HubCoverageIssue>()
    val parsed = (0 until rows.length()).mapNotNull { i -> runCatching {
        val r = rows.getJSONObject(i)
        require(r.hubId("tournamentId") == expectedId && r.getString("tournament").trim() == league.sourceName)
        require(r.hubId("sportId") == events24Sports[league.sport]?.toLong() && r.getString("country") == country)
        val id = r.hubId("id")
        val at = Instant.parse(r.getString("startsAt"))
        require(!at.atZone(ZoneOffset.UTC).toLocalDate().isBefore(window.from) && at.atZone(ZoneOffset.UTC).toLocalDate().isBefore(window.to))
        // The documented startsAt UTC field is authoritative. Local timezone metadata is
        // corroboration only: host/device tzdb versions differ (Canada changed in Nov 2026).
        // Never "repair" UTC using an older local rules database, or silently lose fixtures.
        val local = runCatching { LocalDateTime.parse(r.getString("localStartsAt")) }.getOrNull()
        val zone = runCatching { ZoneId.of(r.getString("timeZone")) }.getOrNull()
        if (local == null || zone == null) issues += HubCoverageIssue.TIME_UNCORROBORATED
        else if (zone.rules.getValidOffsets(local).none { local.toInstant(it) == at }) issues += HubCoverageIssue.TIME_CONFLICT
        val home = events24Team(r.getString("teamHome"), league.sport, country)
        val away = events24Team(r.getString("teamAway"), league.sport, country); require(home.id != away.id)
        val category = r.getString("category")
        require(category in setOf("upcoming", "result"))
        val score = if (category == "result") r.optString("result").let { result ->
            Regex("([0-9]{1,3})[:-]([0-9]{1,3})").matchEntire(result.trim())?.destructured?.let { (h,a) ->
                HubScore(h.toInt(), a.toInt(), HubResultStage.UNSPECIFIED)
            }
        } else null
        if (category == "result" && score == null) issues += HubCoverageIssue.FINAL_WITHOUT_SCORE
        if (category == "upcoming" && at < now.minusSeconds(4 * 3600)) issues += HubCoverageIssue.PAST_WITHOUT_FINAL
        HubMatch("${league.sport.name}:${HubProvider.EVENTS24.name}:$id", league.key, season, home, away, at,
            if (category == "result") HubMatchStatus.REPORTED_FINAL else if (at > now) HubMatchStatus.SCHEDULED else HubMatchStatus.UNCONFIRMED,
            score, r.optString("updatedAt").takeIf { it.isNotEmpty() }?.let { Instant.parse(it) })
    }.onFailure { issues += HubCoverageIssue.INVALID_ROWS }.getOrNull() }
    val matches = parsed.groupBy { it.id }.mapNotNull { (_,same) ->
        if (same.distinct().size == 1) same.first() else { issues += HubCoverageIssue.CONFLICTING_IDS; null }
    }.sortedWith(compareBy<HubMatch> { it.startsAt }.thenBy { it.id })
    require(rows.length() == 0 || matches.isNotEmpty())
    val usage = root.optJSONObject("usage")
    val quota = usage?.let {
        val day = LocalDate.parse(it.getString("day"))
        val month = YearMonth.parse(it.getString("month")); require(month == YearMonth.from(day))
        HubQuota(day, month, it.getInt("requestsRemaining").also { n -> require(n in 0..100) },
            it.getInt("rowsRemaining").also { n -> require(n in 0..5_000) })
    }
    require(quota != null)
    return HubSnapshot(league.key, season, HubProvider.EVENTS24, matches, now, issues,
        root.optString("nextCursor").let { it.isNotBlank() && it != "null" }, window.from, window.to, quota)
}
