package tv.own.owntv.features.home

import java.security.MessageDigest
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Reads only ordinary public HTML. No script execution, private API or outgoing link requests. */
internal object TvmatchenHtml {
    const val reviewedTermsHash = "cfc7b62e2b9c50d12e10d4270ecf7db083910c964bd44150ba55700211dc5516"
    private val scripts = Regex("<script>self\\.__next_f\\.push\\((\\[.*?)\\)</script>", RegexOption.DOT_MATCHES_ALL)
    private val links = Regex("href=\"(/match/[a-z0-9-]{1,140}-[0-9]{1,10})\"")
    private val channelText = Regex("<span[^>]*class=\"rt-match-channel-list__channel-text\"[^>]*>([^<]{1,120})")
    private val tags = Regex("<[^>]+>")
    private val whitespace = Regex("[\\s\\u00a0]+")

    fun text(html: String): String = whitespace.replace(tags.replace(html, " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'"), " ").trim()

    fun termsUnchanged(html: String): Boolean {
        require(html.length <= 700_000)
        val start = html.indexOf("<div class=\"article\">").takeIf { it >= 0 } ?: return false
        val contentStart = start + "<div class=\"article\">".length
        val end = html.indexOf("</div>", contentStart).takeIf { it > contentStart } ?: return false
        val hash = MessageDigest.getInstance("SHA-256").digest(text(html.substring(contentStart, end)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash == reviewedTermsHash
    }

    fun fixtureLinks(html: String): List<String> {
        require(html.length <= 2_000_000)
        require(html.contains("/ishockey/shl"))
        // Only rendered public anchors; never discover URLs in hydration scripts or other leagues.
        return links.findAll(html.substringBefore("<script>self.__next_f")).map { it.groupValues[1] }
            .distinct().take(128).toList()
    }

    fun candidates(paths: List<String>, fixture: BroadcastFixture): List<String> = paths.filter {
        val slug = it.removePrefix("/match/").substringBeforeLast('-')
        val words = teamTokens(slug)
        val home = ShlTeams.identity(fixture.home)
        val away = ShlTeams.identity(fixture.away)
        home != null && away != null && home != away &&
            ShlTeams.identities(words) == setOf(home, away)
    }.take(2)

    fun fixtureIndex(html: String, now: Instant): Map<String, List<String>> {
        val anchors = fixtureLinks(html).toSet()
        val today = now.atZone(Stockholm).toLocalDate()
        val index = mutableMapOf<String, MutableList<String>>()
        scripts.findAll(html).take(160).forEach { script ->
            val chunk = JSONArray(script.groupValues[1]).optString(1)
            if (!chunk.contains("\"fixturesByTimeData\"") || (0L..2L).none { chunk.contains("\"${today.plusDays(it)}\"") }) return@forEach
            require(chunk.length <= 300_000)
            val groups = JSONArray(chunk.substringAfter(':')).getJSONObject(3).getJSONObject("data").getJSONObject("fixturesByTimeData")
            for (day in (0L..2L).map { today.plusDays(it) }) {
                val group = groups.optJSONObject(day.toString()) ?: continue
                require(group.getString("date") == day.toString())
                for (kind in listOf("oldFixtures", "newFixtures")) {
                    // Live SHL HTML contains a mixed hockey index (33 entries on 10 Oct).
                    // Bound the index, then retain SHL only; never fetch other league pages.
                    val rows = group.getJSONArray(kind); require(rows.length() <= 64)
                    for (i in 0 until rows.length()) {
                        val record = rows.getJSONObject(i)
                        if (record.getString("league") != "SHL") continue
                        val timestamp = record.getString("date")
                        require(timestamp.endsWith('Z') || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(timestamp))
                        val faceoff = java.time.OffsetDateTime.parse(timestamp).toInstant()
                        val fixture = BroadcastFixture("SHL", record.getString("home_team"), record.getString("visiting_team"),
                            faceoff.atZone(Stockholm).toLocalDate(), faceoff)
                        require(fixture.date == day)
                        if (ShlTeams.identity(fixture.home) == null || ShlTeams.identity(fixture.away) == null) continue
                        val path = "/match/${record.getString("fixture_slug")}-${record.getLong("fixture_id")}"
                        if (path !in anchors) continue
                        index.getOrPut(fixtureKey(fixture)) { mutableListOf() }.add(path)
                        require(index.size <= 32)
                    }
                }
            }
        }
        // Empty or changed indexed markup cannot validate a date/time before page discovery.
        require(index.isNotEmpty())
        return index.mapValues { it.value.distinct().take(3) }
    }

    fun assignment(html: String, path: String, wanted: BroadcastFixture, now: Instant): BroadcastAssignment? {
        require(html.length <= 800_000)
        require(path.matches(Regex("/match/[a-z0-9-]{1,140}-[0-9]{1,10}")))
        require(html.contains("href=\"https://www.tvmatchen.nu$path\""))
        require(html.contains("href=\"/ishockey/shl\""))
        val visible = channelText.findAll(html.substringBefore("<script>self.__next_f")).map { text(it.groupValues[1]) }.toSet()
        val records = scripts.findAll(html).take(160).mapNotNull { script ->
            val chunk = runCatching { JSONArray(script.groupValues[1]).optString(1) }.getOrNull() ?: return@mapNotNull null
            fixtureRecord(chunk)
        }.toList()
        val record = records.singleOrNull() ?: return null
        require(path == "/match/${record.getString("fixture_slug")}-${record.getLong("fixture_id")}")
        require(!record.getBoolean("is_archived"))
        // Explicit Z/offset is mandatory. The rendered 13:15 alone has no verified timezone.
        val timestamp = record.getString("date")
        require(timestamp.endsWith('Z') || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(timestamp))
        val faceoff = java.time.OffsetDateTime.parse(timestamp).toInstant()
        val fixture = BroadcastFixture(record.getString("league"), record.getString("home_team"),
            record.getString("visiting_team"), faceoff.atZone(Stockholm).toLocalDate(), faceoff)
        val channels = record.getJSONArray("channels")
        require(channels.length() in 1..12)
        val broadcasts = (0 until channels.length()).mapNotNull { index ->
            val item = channels.getJSONObject(index)
            val name = item.getString("shortname")
            // Require a visible channel label too, excluding invisible ads/bookmakers/hydration extras.
            if (name !in visible) null else BroadcastChannel(name, !item.getBoolean("is_streaming"))
        }.distinct()
        return BroadcastResolver.usable(wanted, BroadcastAssignment(fixture, broadcasts, now,
            now.plusSeconds(2 * 60 * 60), "TVmatchen"), now)
    }

    private fun fixtureRecord(chunk: String): JSONObject? {
        val marker = "\"fixture\":{"
        val start = chunk.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length - 1) ?: return null
        // A bounded balanced-object scan handles braces inside JSON strings without interpreting JS.
        var depth = 0; var quoted = false; var escaped = false
        for (i in start until minOf(chunk.length, start + 32_000)) {
            val c = chunk[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return JSONObject(chunk.substring(start, i + 1))
            }
        }
        return null
    }
}

/** RFC-style longest matching allow/disallow rule for our honest named user agent. Unknown syntax stops. */
internal object BroadcastRobots {
    fun permits(body: String, path: String): Boolean {
        require(body.length <= 32_768 && path.startsWith('/') && !body.contains('<'))
        data class Group(val agents: MutableList<String> = mutableListOf(), val rules: MutableList<Pair<Boolean, String>> = mutableListOf())
        val groups = mutableListOf<Group>(); var group = Group(); var rulesSeen = false
        for (line in body.lineSequence()) {
            val value = line.substringBefore('#').trim()
            if (value.isEmpty()) continue
            require(value.contains(':'))
            val key = value.substringBefore(':').trim().lowercase(java.util.Locale.ROOT)
            val rule = value.substringAfter(':').trim()
            when (key) {
                "user-agent" -> { if (rulesSeen) { groups += group; group = Group(); rulesSeen = false }; group.agents += rule.lowercase(java.util.Locale.ROOT) }
                "allow", "disallow" -> { require(group.agents.isNotEmpty()); rulesSeen = true; if (rule.isNotEmpty()) group.rules += (key == "allow") to rule }
                "sitemap" -> Unit
                // A requested delay or unknown access directive needs review, never silently ignored.
                else -> return false
            }
        }
        groups += group
        require(groups.any { it.agents.isNotEmpty() })
        val specific = groups.filter { it.agents.any { a -> a != "*" && "mintv-experimentalbroadcast".contains(a) } }
        val selected = specific.ifEmpty { groups.filter { "*" in it.agents } }
        if (selected.isEmpty()) return false
        val matching = selected.flatMap { it.rules }.filter { (_, pattern) ->
            val end = pattern.endsWith('$')
            val regex = pattern.removeSuffix("$").split('*').joinToString(".*") { Regex.escape(it) }
            Regex("^" + regex + if (end) "$" else "").containsMatchIn(path)
        }
        val longest = matching.maxOfOrNull { it.second.replace("*", "").removeSuffix("$").length } ?: return true
        return matching.filter { it.second.replace("*", "").removeSuffix("$").length == longest }.any { it.first }
    }
}
