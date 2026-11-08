package tv.own.owntv.features.sporthub

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Disposable, versioned public-data cache. Never serialize private IPTV/profile/account objects. */
internal object HubCacheCodec {
    fun encode(entry: HubCacheEntry): String = JSONObject().put("version", 1)
        .put("attempted", entry.attemptedAt.toString()).put("retry", entry.retryAt.toString())
        .put("issue", entry.issue?.name ?: JSONObject.NULL)
        .put("snapshot", entry.snapshot?.let(::snapshot) ?: JSONObject.NULL).toString()

    fun decode(text: String): HubCacheEntry? = runCatching {
        require(text.length <= HUB_MAX_BODY)
        val root = JSONObject(text); require(root.getInt("version") == 1)
        HubCacheEntry(root.optJSONObject("snapshot")?.let(::readSnapshot), Instant.parse(root.getString("attempted")),
            Instant.parse(root.getString("retry")), root.optional("issue")?.let(HubIssue::valueOf))
    }.getOrNull()

    private fun team(t: HubTeam) = JSONObject().put("id", t.id).put("name", t.name)
        .put("short", t.shortName ?: JSONObject.NULL).put("identity", t.identity.name)
    private fun readTeam(o: JSONObject): HubTeam = HubTeam(o.getString("id").also { require(it.length in 1..160) },
        o.getString("name").also { require(it.length in 1..160 && it.none(Char::isISOControl)) },
        o.optional("short")?.also { require(it.length <= 80) }, HubTeamIdentity.valueOf(o.getString("identity")))
    private fun snapshot(s: HubSnapshot): JSONObject = JSONObject().put("league", s.league).put("season", s.season)
        .put("provider", s.provider.name).put("fetched", s.fetchedAt.toString())
        .put("coverage", JSONArray(s.coverage.map { it.name })).put("more", s.hasMore)
        .put("from", s.from?.toString() ?: JSONObject.NULL).put("to", s.to?.toString() ?: JSONObject.NULL)
        .put("quota", s.quota?.let { JSONObject().put("day", it.day.toString()).put("month", it.month.toString())
            .put("requests", it.requestsRemaining).put("rows", it.rowsRemaining) } ?: JSONObject.NULL)
        .put("matches", JSONArray().apply { s.matches.forEach { m -> put(JSONObject()
            .put("id", m.id).put("home", team(m.home)).put("away", team(m.away)).put("starts", m.startsAt.toString())
            .put("status", m.status.name).put("updated", m.sourceUpdatedAt?.toString() ?: JSONObject.NULL)
            .put("score", m.score?.let { JSONObject().put("home", it.home).put("away", it.away).put("stage", it.stage.name) } ?: JSONObject.NULL)) } })

    private fun readSnapshot(o: JSONObject): HubSnapshot {
        val league = o.getString("league").also { require(it.length in 1..120) }
        val season = o.getString("season").also { require(it.length in 1..20) }
        val rows = o.getJSONArray("matches"); require(rows.length() <= HUB_MAX_MATCHES)
        val matches = (0 until rows.length()).map { i -> val m = rows.getJSONObject(i)
            val score = m.optJSONObject("score")?.let { s -> HubScore(s.getInt("home").also { require(it in 0..1000) },
                s.getInt("away").also { require(it in 0..1000) }, HubResultStage.valueOf(s.getString("stage"))) }
            HubMatch(m.getString("id").also { require(it.length in 1..160) }, league, season,
                readTeam(m.getJSONObject("home")), readTeam(m.getJSONObject("away")), Instant.parse(m.getString("starts")),
                HubMatchStatus.valueOf(m.getString("status")), score, m.optional("updated")?.let(Instant::parse))
        }
        require(matches.distinctBy { it.id }.size == matches.size)
        val problems = o.getJSONArray("coverage"); require(problems.length() <= HubCoverageIssue.entries.size)
        val quota = o.optJSONObject("quota")?.let { HubQuota(LocalDate.parse(it.getString("day")), YearMonth.parse(it.getString("month")),
            it.getInt("requests").also { n -> require(n >= 0) }, it.getInt("rows").also { n -> require(n >= 0) }) }
        return HubSnapshot(league, season, HubProvider.valueOf(o.getString("provider")), matches, Instant.parse(o.getString("fetched")),
            (0 until problems.length()).map { HubCoverageIssue.valueOf(problems.getString(it)) }.toSet(), o.getBoolean("more"),
            o.optional("from")?.let(LocalDate::parse), o.optional("to")?.let(LocalDate::parse), quota)
    }
    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)
}
