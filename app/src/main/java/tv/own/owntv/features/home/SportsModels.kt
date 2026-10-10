package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import java.text.NumberFormat

internal enum class Sport { ICE_HOCKEY, FOOTBALL }
internal data class Competition(val id: String, val sport: Sport, val name: String, val scheduleAvailable: Boolean)
internal data class Team(val id: String, val sport: Sport, val name: String)
internal enum class MatchStatus { SCHEDULED, RESULT_SNAPSHOT }
internal data class Score(val home: Int, val away: Int)
internal data class SportFixture(val id: String, val competition: Competition, val homeTeam: Team, val awayTeam: Team,
    val kickoff: Instant, val status: MatchStatus, val score: Score?) {
    val home get() = homeTeam.name
    val away get() = awayTeam.name
    val faceoff get() = kickoff.toEpochMilli()
    val result get() = score?.let { NumberFormat.getIntegerInstance().format(it.home) + "–" + NumberFormat.getIntegerInstance().format(it.away) }
    fun on(date: LocalDate) = kickoff.atZone(Stockholm).toLocalDate() == date
    fun follows(selection: SportPreferences) = homeTeam.id in selection.teamIds || awayTeam.id in selection.teamIds
    fun hockeyGame() = ShlGame(id, home, away, faceoff, result)
    fun broadcastFixture() = BroadcastFixture(competition.name, home, away, kickoff.atZone(Stockholm).toLocalDate(), kickoff)
}
internal data class SportSnapshot(val competition: Competition, val seasonId: String, val fixtures: List<SportFixture>,
    val fetchedAt: Long, val sourceUpdatedAt: Long?)
internal data class SportPreferences(val teamIds: List<String> = emptyList(), val prominent: Boolean = true, val hideScores: Boolean = false) {
    val primaryTeamId get() = teamIds.firstOrNull()
    fun toggle(id: String): SportPreferences = copy(teamIds = if (id in teamIds) teamIds - id else teamIds + id)
    fun primary(id: String): SportPreferences = if (id in teamIds) copy(teamIds = listOf(id) + (teamIds - id)) else this
    fun move(id: String, offset: Int): SportPreferences {
        val index = teamIds.indexOf(id); val target = index + offset
        if (index < 0 || target !in teamIds.indices) return this
        return copy(teamIds = teamIds.toMutableList().apply { add(target, removeAt(index)) })
    }
}

/** Club IDs never contain a league or season. Membership is source metadata, not a preference. */
internal object SportsCatalog {
    val shl = Competition("se.shl", Sport.ICE_HOCKEY, "SHL", true)
    val allsvenskan = Competition("se.hockeyallsvenskan", Sport.ICE_HOCKEY, "HockeyAllsvenskan", true)
    // Club selection is available; competition membership/fixtures need a verified football source.
    val football = Competition("se.football.unverified", Sport.FOOTBALL, "", false)
    val competitions = listOf(shl, allsvenskan, football)
    private val shlClubs = listOf("Färjestad BK", "Frölunda HC", "IF Björklöven", "Skellefteå AIK", "Brynäs IF", "Rögle BK",
        "IF Malmö Redhawks", "Växjö Lakers HC", "Luleå HF", "Örebro HK", "Linköping HC", "Djurgårdens IF", "HV 71", "Timrå IK")
    private val haClubs = listOf("Leksands IF", "AIK", "Kalmar HC", "MoDo Hockey", "Södertälje SK", "Almtuna IS", "IK Oskarshamn",
        "Mora IK", "Östersunds IK", "BIK Karlskoga", "Västerås IK", "Vimmerby HC", "Nybro Vikings IF", "Visby/Roma HK")
    private val footballClubs = listOf("Degerfors IF", "AIK", "Djurgårdens IF", "IFK Göteborg", "IF Elfsborg", "Malmö FF", "Hammarby IF",
        "IFK Norrköping", "BK Häcken", "IK Sirius", "Halmstads BK", "IF Brommapojkarna", "Kalmar FF", "Östers IF", "Mjällby AIF", "GAIS")
    private val aliases = mapOf(
        "Färjestad BK" to listOf("farjestad", "fbk"), "Frölunda HC" to listOf("frolunda", "fhc"),
        "IF Björklöven" to listOf("bjorkloven", "loven"), "Skellefteå AIK" to listOf("skelleftea", "saik"),
        "IF Malmö Redhawks" to listOf("malmo", "redhawks"), "Växjö Lakers HC" to listOf("vaxjo", "lakers"),
        "HV 71" to listOf("hv", "hv71"), "BIK Karlskoga" to listOf("karlskoga", "bik"),
        "Leksands IF" to listOf("leksand", "leksands", "lif"), "MoDo Hockey" to listOf("modo"),
    )
    private fun make(name: String, sport: Sport): Team {
        val key = if (sport == Sport.ICE_HOCKEY) ShlTeams.identity(name) ?: aliases[name]?.first()
            else null
        return Team((if (sport == Sport.ICE_HOCKEY) "hockey:" else "football:") +
            (key ?: teamTokens(name).filter(String::isNotEmpty).joinToString("-")), sport, name)
    }
    val teams = (shlClubs + haClubs).map { make(it, Sport.ICE_HOCKEY) }.distinctBy { it.id } + footballClubs.map { make(it, Sport.FOOTBALL) }
    val farjestadId = make("Färjestad BK", Sport.ICE_HOCKEY).id
    fun team(id: String): Team? = teams.firstOrNull { it.id == id }
    fun inCompetition(competition: Competition): List<Team> = when (competition.id) {
        shl.id -> shlClubs.map { make(it, Sport.ICE_HOCKEY) }
        allsvenskan.id -> haClubs.map { make(it, Sport.ICE_HOCKEY) }
        else -> footballClubs.map { make(it, Sport.FOOTBALL) }
    }
    fun required(selection: SportPreferences): List<Competition> = competitions.filter { c ->
        inCompetition(c).any { it.id in selection.teamIds }
    }
    fun identity(name: String, sport: Sport): Team? {
        val tokens = teamTokens(name).filter(String::isNotEmpty)
        return teams.filter { it.sport == sport }.filter { team ->
            val exact = teamTokens(team.name).filter(String::isNotEmpty)
            exact == tokens || if (sport == Sport.ICE_HOCKEY) {
                ShlTeams.identity(name)?.let { it == ShlTeams.identity(team.name) } ?: aliases[team.name]?.any { it in tokens } == true
            } else false
        }.singleOrNull()
    }
    fun fixture(competition: Competition, game: ShlGame): SportFixture {
        val score = game.result?.split('-')?.map(String::toIntOrNull)?.takeIf { it.size == 2 && it.all { n -> n != null } }
            ?.let { Score(it[0]!!, it[1]!!) }
        return SportFixture(competition.id + ":" + game.id, competition,
            identity(game.home, competition.sport) ?: make(game.home, competition.sport),
            identity(game.away, competition.sport) ?: make(game.away, competition.sport), Instant.ofEpochMilli(game.faceoff),
            if (score != null) MatchStatus.RESULT_SNAPSHOT else MatchStatus.SCHEDULED, score)
    }
}

internal fun personalFixtures(fixtures: List<SportFixture>, selection: SportPreferences, now: Long): List<SportFixture> =
    fixtures.filter { it.follows(selection) && (it.on(stockholmDay(now)) || it.faceoff >= now) }
        .distinctBy { it.id }.sortedBy { it.faceoff }

/** Visibility and explicit opt-in precede even reading the network-backed repository. */
internal fun sportsRequestCompetitions(selection: SportPreferences, active: Boolean, visible: Boolean,
    matchcenterOpen: Boolean, enabled: Boolean): List<Competition> =
    if (active && enabled && (visible && selection.prominent || matchcenterOpen))
        SportsCatalog.required(selection).filter { it.scheduleAvailable } else emptyList()
