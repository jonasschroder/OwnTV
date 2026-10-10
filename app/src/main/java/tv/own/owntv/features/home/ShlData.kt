package tv.own.owntv.features.home

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Stockholm: ZoneId = ZoneId.of("Europe/Stockholm")
internal data class ShlGame(val id: String, val home: String, val away: String, val faceoff: Long, val result: String?) {
    val fbk get() = teamTokens(home).any { it == "farjestad" || it == "fbk" } || teamTokens(away).any { it == "farjestad" || it == "fbk" }
    fun on(date: LocalDate) = Instant.ofEpochMilli(faceoff).atZone(Stockholm).toLocalDate() == date
}
internal data class ShlStanding(val rank: Int, val team: String, val points: Int,
    val played: Int? = null, val goalDifference: Int? = null)
internal data class ShlSnapshot(val seasonId: String, val games: List<ShlGame>, val fetchedAt: Long, val sourceUpdatedAt: Long? = null)

/** No DOM or script execution. Only the named table, desktop cells and known columns are accepted. */
internal object SwehockeyParser {
    private val row = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val cell = Regex("<t[dh]\\b([^>]*?)(?:/\\s*>|>(.*?)</t[dh]>)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val tags = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")
    private val date = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val time = Regex("\\d{2}:\\d{2}")
    private val score = Regex("\\d{1,2}\\s*-\\s*\\d{1,2}")
    private val gameId = Regex("/(?:Game)/(?:GamePreview|GameSummary|LineUps)/(\\d+)")
    private val fixtureId = Regex("title=\"(\\d+)\"")

    private fun text(value: String): String = tags.replace(value, " ")
        .replace(Regex("&#(?:x([0-9a-fA-F]+)|(\\d+));")) {
            val n = it.groups[1]?.value?.toIntOrNull(16) ?: it.groups[2]?.value?.toIntOrNull()
            n?.takeIf { cp -> cp in 0..65535 }?.toChar()?.toString().orEmpty()
        }.replace("&nbsp;", " ").replace("&amp;", "&").replace('\u00a0', ' ')
        .let { whitespace.replace(it, " ").trim() }.take(160)

    private fun rows(html: String, title: String): List<Pair<String, List<String>>> {
        require(html.length <= 600_000)
        val start = Regex("<h2>\\s*${Regex.escape(title)}\\s*</h2>").find(html)?.range?.first ?: -1
        require(start >= 0)
        val end = html.indexOf("</table>", start)
        require(end > start)
        return row.findAll(html.substring(start, end)).take(450).map { r ->
            r.value to cell.findAll(r.value).filterNot { it.groupValues[1].contains("d-sm-none") }
                .map { text(it.groupValues[2]) }.toList()
        }.toList()
    }

    fun discover(html: String, league: String = "SHL"): String {
        require(html.length <= 600_000)
        require(league in listOf("SHL", "HockeyAllsvenskan"))
        val ids = Regex("href=\"/ScheduleAndResults/(?:Live|Overview)/(\\d+)\"[^>]*>\\s*${Regex.escape(league)}\\s*<")
            .findAll(html).map { it.groupValues[1] }.distinct().toList()
        require(ids.size == 1)
        return ids.single()
    }

    fun lastUpdated(html: String): Long? = Regex("Last update:[\\s\\u00a0]*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})")
        .find(html)?.groupValues?.get(1)?.let {
            LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(Stockholm).toInstant().toEpochMilli()
        }

    fun schedule(html: String): List<ShlGame> {
        var lastDate: String? = null
        val rows = rows(html, "Schedule and Results")
        require(rows.firstOrNull()?.second?.take(4) == listOf("Date", "Time", "Game", "Result"))
        val games = rows.drop(1).map { (raw, cells) ->
            require(cells.size >= 4)
            date.matchEntire(cells[0])?.let { lastDate = it.value }
            val pair = cells[2].split(Regex("\\s+-\\s+"))
            require(pair.size == 2 && pair.none { it.isBlank() })
            require(lastDate != null && time.matches(cells[1]))
            val id = gameId.find(raw)?.groupValues?.get(1) ?: fixtureId.find(raw)?.groupValues?.get(1)
            require(id != null)
            val whenMs = LocalDateTime.parse("$lastDate ${cells[1]}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atZone(Stockholm).toInstant().toEpochMilli()
            ShlGame(id, pair[0], pair[1], whenMs, score.find(cells[3])?.value?.replace(" ", ""))
        }
        require(games.isNotEmpty() && games.size <= 400 && games.map { it.id }.distinct().size == games.size)
        return games.sortedBy { it.faceoff }
    }

    fun standings(html: String): List<ShlStanding> {
        val rows = rows(html, "Standings")
        require(rows.firstOrNull()?.second?.take(6) == listOf("RK", "Team", "GP", "W", "T", "L"))
        require(rows.first().second.getOrNull(7) == "TP")
        val ranks = rows.drop(1).filterNot { (raw, cells) ->
            cells == listOf("") && raw.contains("colspan=\"12\"") && raw.contains("<hr ")
        }.map { (_, cells) ->
            val rank = cells.first().toInt()
            require(cells.size >= 8)
            val played = cells[2].toIntOrNull()?.takeIf { it in 0..100 }
            val goals = Regex("(\\d{1,3}):(\\d{1,3})(?:\\s*\\(([+-]?\\d{1,3})\\))?").matchEntire(cells[6])
            val difference = goals?.let {
                val value = it.groupValues[1].toInt() - it.groupValues[2].toInt()
                require(it.groupValues[3].isEmpty() || it.groupValues[3].toInt() == value)
                value
            }
            ShlStanding(rank, cells[1], cells[7].toInt(), played, difference)
        }
        require(ranks.size == 14 && ranks.map { it.rank }.toSet() == (1..14).toSet())
        return ranks
    }

    /** Conservative: any robots disallow rule suspends the experimental reader. Never bypass it. */
    fun robotsPermit(body: String): Boolean {
        val lines = body.lineSequence().map { it.substringBefore('#').trim() }.filter(String::isNotEmpty).toList()
        require(lines.any { it.startsWith("User-agent:", ignoreCase = true) })
        return lines.none { it.startsWith("Disallow:", ignoreCase = true) && it.substringAfter(':').trim().isNotEmpty() }
    }
}

internal fun teamTokens(value: String): List<String> = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").split(Regex("[^a-z0-9]+"))

/** Both teams and a faceoff-adjacent live broadcast are mandatory; league-only listings never qualify. */
internal object ShlEpgMatcher {
    private val aliases = listOf(
        listOf("farjestad", "fbk"), listOf("frolunda", "fhc"), listOf("skelleftea", "saik"),
        listOf("malmo", "redhawks"), listOf("lulea", "lhf"), listOf("brynas", "bif"),
        listOf("vaxjo", "lakers"), listOf("timra", "tik"), listOf("orebro", "ohk"),
        listOf("linkoping", "lhc"), listOf("rogle", "rbk"), listOf("leksand", "lif"),
        listOf("djurgarden", "dif"), listOf("bjorkloven", "loven"), listOf("hv", "hv71"),
        listOf("karlskoga", "bik"), listOf("modo"), listOf("aik"), listOf("sodertalje", "ssk"),
        listOf("oskarshamn", "iko"), listOf("vasteras", "vik"), listOf("almtuna", "ais"),
        listOf("ostersund", "oik"), listOf("vimmerby", "vhc"), listOf("nybro", "vikings"),
        listOf("mora", "mik"), listOf("visby", "roma"), listOf("kalmar", "khc"),
    )
    private fun teamAliases(name: String): List<String> {
        val tokens = teamTokens(name)
        return aliases.firstOrNull { group -> group.any { it in tokens } } ?: tokens.filter { it.length > 3 }
    }
    fun confirmed(game: ShlGame, title: String, description: String?, start: Long, stop: Long, concurrent: List<ShlGame>): Boolean {
        if (start >= stop || kotlin.math.abs(start - game.faceoff) > 60 * 60_000L || stop <= game.faceoff || stop - start > 5 * 60 * 60_000L) return false
        val titleTokens = teamTokens(title)
        val tokens = (titleTokens + teamTokens(description.orEmpty().take(4000))).toSet()
        if (tokens.any { it in listOf("repris", "replay", "highlights", "sammandrag", "studio") }) return false
        fun both(g: ShlGame) = teamAliases(g.home).any { it in tokens } && teamAliases(g.away).any { it in tokens }
        return both(game) && concurrent.none { it.id != game.id && both(it) }
    }
}
