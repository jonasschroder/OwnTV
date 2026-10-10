package tv.own.owntv.features.home

import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Replaceable metadata source, independent from schedule/standings and IPTV playback. */
internal fun interface BroadcastMetadataSource {
    suspend fun assignment(fixture: BroadcastFixture): BroadcastAssignment?
}

/** Default implementation intentionally has no HTTP client, cache, timer or background work. */
internal object NoBroadcastMetadata : BroadcastMetadataSource {
    override suspend fun assignment(fixture: BroadcastFixture): BroadcastAssignment? = null
}

internal data class BroadcastFixture(val league: String, val home: String, val away: String,
    val date: LocalDate, val faceoff: Instant) {
    companion object {
        fun from(game: ShlGame) = BroadcastFixture("SHL", game.home, game.away,
            Instant.ofEpochMilli(game.faceoff).atZone(Stockholm).toLocalDate(), Instant.ofEpochMilli(game.faceoff))
    }
}
internal data class BroadcastChannel(val name: String, val linear: Boolean)
internal data class BroadcastAssignment(val fixture: BroadcastFixture, val channels: List<BroadcastChannel>,
    val checkedAt: Instant, val expiresAt: Instant, val source: String)

internal object BroadcastResolver {
    /** Exact identity and instant: reschedules, home/away changes and expired records fail closed. */
    fun usable(fixture: BroadcastFixture, value: BroadcastAssignment?, now: Instant): BroadcastAssignment? = value?.takeIf {
        fixture.league == "SHL" && it.fixture.league == fixture.league &&
            teamId(fixture.home) != null && teamId(fixture.away) != null &&
            teamId(fixture.home) != teamId(fixture.away) &&
            teamId(fixture.home) == teamId(it.fixture.home) && teamId(fixture.away) == teamId(it.fixture.away) &&
            fixture.date == it.fixture.date && fixture.faceoff == it.fixture.faceoff &&
            fixture.date == fixture.faceoff.atZone(Stockholm).toLocalDate() &&
            !it.checkedAt.isAfter(now) && it.expiresAt.isAfter(now) &&
            it.expiresAt.toEpochMilli() - it.checkedAt.toEpochMilli() <= 24 * 60 * 60_000L &&
            it.expiresAt.isAfter(it.checkedAt) && it.source.isNotBlank() &&
            it.channels.isNotEmpty() && it.channels.size <= 8 && it.channels.all { c -> c.name.isNotBlank() && c.name.length <= 120 }
    }

    /** Strip only known country/quality wrappers. Channel numbers and remaining tokens stay exact. */
    fun channelKey(name: String): String {
        val tokens = name.uppercase(Locale.ROOT).split(Regex("[^A-Z0-9]+"))
            .filter(String::isNotEmpty).toMutableList()
        val wrappers = setOf("SE", "SWE", "SWEDEN", "SD", "HD", "FHD", "UHD", "4K", "8K")
        while (tokens.firstOrNull() in wrappers) tokens.removeAt(0)
        while (tokens.lastOrNull() in wrappers) tokens.removeAt(tokens.lastIndex)
        return tokens.joinToString(" ")
    }

    /** These are not broadcaster identities. Only exact-fixture EPG evidence can recommend them. */
    fun eventPlaceholder(name: String): Boolean {
        val tokens = channelKey(name).split(' ')
        return "PPV" in tokens || "EXCLUSIVE" in tokens ||
            tokens.windowed(3).any { it == listOf("NO", "EVENT", "STREAMING") }
    }

    fun searchTokens(broadcast: BroadcastChannel): List<String> =
        channelKey(broadcast.name).split(' ').filter { it.isNotEmpty() }.take(12)

    fun matches(broadcast: BroadcastChannel, libraryName: String): Boolean {
        // A streaming brand is never promoted to a linear service. A non-linear exact configured
        // entry may still be chosen manually, using the provider's existing URL and authorisation.
        return broadcast.linear && !eventPlaceholder(libraryName) &&
            !channelKey(broadcast.name).startsWith("TV4 PLAY") && channelKey(broadcast.name) == channelKey(libraryName)
    }

    private fun teamId(name: String): String? = ShlTeams.identity(name)
}

internal object ShlTeams {
    private val teams = listOf(
        listOf("farjestad", "fbk"), listOf("frolunda", "fhc"), listOf("skelleftea", "saik"),
        listOf("malmo", "redhawks"), listOf("lulea", "lhf"), listOf("brynas", "bif"),
        listOf("vaxjo", "lakers"), listOf("timra", "tik"), listOf("orebro", "ohk"),
        listOf("linkoping", "lhc"), listOf("rogle", "rbk"), listOf("leksand", "lif"),
        listOf("djurgarden", "dif"), listOf("bjorkloven", "loven"), listOf("hv", "hv71"),
    )
    fun identity(name: String): String? {
        val words = teamTokens(name)
        return teams.filter { team -> team.any { it in words } }.singleOrNull()?.first()
    }
    fun identities(words: List<String>): Set<String> = teams.filter { team -> team.any { it in words } }.map { it.first() }.toSet()
}
