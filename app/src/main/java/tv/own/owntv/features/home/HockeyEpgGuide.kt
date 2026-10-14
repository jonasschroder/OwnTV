package tv.own.owntv.features.home

import java.time.Instant
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.features.live.ChannelQuality
import tv.own.owntv.features.live.SwedishChannels
import tv.own.owntv.features.live.SwedishChoices
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

internal enum class HockeyChannelEvidence { BROADCAST_SCHEDULE, SPECIFIC_EPG, MANUAL, SUGGESTED, UNKNOWN }
internal data class EpgHockeyMatch(val fixture: SportFixture, val channelIds: List<Long>,
    val channelStops: Map<Long, Long>, val timeFromEpg: Boolean, val truncated: Boolean = false,
    val channelStarts: Map<Long, Long> = emptyMap())
internal data class HockeyEpgFixture(val fixture: SportFixture, val start: Long, val stop: Long, val timeFromEpg: Boolean)

/** Reused ids do not prove that a rescheduled or changed fixture still has the same EPG evidence. */
internal fun localHockeyEvidence(matches: List<EpgHockeyMatch>, fixture: SportFixture?): EpgHockeyMatch? =
    fixture?.let { expected -> matches.singleOrNull { it.fixture == expected } }

/** Verified broadcast names need no database lookup; EPG-only confirmation remains exact and bounded. */
internal suspend fun matchHockeyChannels(channels: List<ChannelEntity>, fixture: SportFixture,
    selection: SportPreferences, known: List<SportFixture>, assignment: BroadcastAssignment?, now: Long,
    read: suspend (ChannelEntity) -> List<EpgProgrammeEntity>): List<ChannelEntity> = withTimeout(8_000) {
    val broadcast = BroadcastResolver.usable(fixture.broadcastFixture(), assignment, Instant.ofEpochMilli(now))
    if (broadcast != null) return@withTimeout channels.filter { channel ->
        broadcast.channels.any { BroadcastResolver.matches(it, channel.name) }
    }
    val parser = HockeyEpgParser(selection, now, known)
    channels.filter { channel ->
        currentCoroutineContext().ensureActive()
        read(channel).any { parser.parse(it)?.fixture == fixture }
    }
}

/** Reuse Swedish visibility preferences; an explicit preferred UHD entry remains an owner's choice. */
internal fun hockeyGuideChannels(channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    choices: tv.own.owntv.features.live.SwedishChoices) = channels.filter { channel ->
    val name = tv.own.owntv.features.live.SwedishChannels.recognize(channel.name)
    name == null || name.identity !in choices.hidden && (choices.showUhd ||
        name.quality != tv.own.owntv.features.live.ChannelQuality.UHD ||
        choices.preferred[name.identity] == tv.own.owntv.core.customize.CustomizeKeys.channel(channel))
}

/** Call only after fixture evidence and a complete scan. Never choose between broadcasters/sources. */
internal fun preferredHockeyChannel(channels: List<ChannelEntity>, choices: SwedishChoices): ChannelEntity? {
    val rows = hockeyGuideChannels(channels, choices).distinctBy { it.id }
    if (rows.isEmpty()) return null
    rows.singleOrNull()?.takeIf { SwedishChannels.recognize(it.name) == null }?.let { return it }
    val named = rows.map { channel -> channel to (SwedishChannels.recognize(channel.name) ?: return null) }
    if (named.map { it.second.identity }.distinct().size != 1) return null
    val identity = named.first().second.identity
    rows.singleOrNull { CustomizeKeys.channel(it) == choices.preferred[identity] }?.let { return it }
    if (rows.map { it.sourceId }.distinct().size != 1) return null
    // Different EPG services can carry regional, audio or time-shift variants.
    if (rows.map { it.epgChannelId }.distinct().size != 1) return null
    val eligible = named.filter { it.second.quality != ChannelQuality.UHD }
    val rank = eligible.minOfOrNull { it.second.quality.automaticRank } ?: return null
    return eligible.filter { it.second.quality.automaticRank == rank }.singleOrNull()?.first
}

/** Local evidence only: no HTTP, channel-name guesses or external schedule prerequisite. */
internal class HockeyEpgParser(private val selection: SportPreferences, private val now: Long,
    private val known: List<SportFixture> = emptyList()) {
    private val competitions = listOf(SportsCatalog.shl, SportsCatalog.allsvenskan)
    private val aliases = competitions.associateWith { league -> SportsCatalog.inCompetition(league).associateWith(SportsCatalog::epgAliases) }
    private val excluded = setOf("repris", "replay", "highlights", "sammandrag", "hojdpunkter", "studio", "magasin",
        "studion", "analys", "eftersnack", "infor", "preview", "sdhl", "dam", "damer", "u18", "u20", "junior", "traningsmatch")
    private val pairSeparator = Regex("[–—-]")
    private val pairWords = setOf("vs", "mot", "moter", "versus")
    private fun position(tokens: List<String>, words: List<String>): Int {
        if (words.isEmpty()) return -1
        for (i in 0..(tokens.size - words.size)) if (words.indices.all { tokens[i + it] == words[it] }) return i
        return -1
    }

    fun parse(programme: EpgProgrammeEntity): HockeyEpgFixture? {
        val start = programme.startMs; val stop = programme.stopMs
        if (stop <= now || start >= stop || stop - start !in 30 * 60_000L..5 * 60 * 60_000L ||
            start < now - 5 * 60 * 60_000L || start > now + 7 * 24 * 60 * 60_000L) return null
        val raw = listOf(programme.title.take(240), programme.description.orEmpty().take(4000))
        val tokens = raw.flatMap { teamTokens(it).filter(String::isNotEmpty) }
        if (tokens.any { it in excluded }) return null
        val leagues = competitions.filter { league -> if (league == SportsCatalog.shl) "shl" in tokens
            else "hockeyallsvenskan" in tokens || position(tokens, listOf("hockey", "allsvenskan")) >= 0 }
        if (leagues.size != 1) return null
        val league = leagues.single()
        val members = aliases.getValue(league).keys.map { it.id }.toSet()
        if (SportsCatalog.teams.filter { it.sport == Sport.ICE_HOCKEY && it.id !in members }.any {
            position(tokens, teamTokens(it.name).filter(String::isNotEmpty)) >= 0
        }) return null
        if (aliases.getValue(league).filterKeys { it.id in selection.teamIds }.values.none { names ->
            names.any { position(tokens, it) >= 0 }
        }) return null
        val teams = aliases.getValue(league).mapNotNull { (team, names) ->
            names.map { position(tokens, it) }.filter { it >= 0 }.minOrNull()?.let { team to it }
        }.sortedBy { it.second }.map { it.first }
        if (teams.size != 2 || teams.none { it.id in selection.teamIds }) return null
        val explicitPair = raw.any { field ->
            val words = teamTokens(pairSeparator.replace(field, " vs ")).filter(String::isNotEmpty)
            val positions = teams.map { team -> aliases.getValue(league).getValue(team).mapNotNull { name ->
                position(words, name).takeIf { it >= 0 }?.let { it to name.size }
            }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenByDescending { it.second }).firstOrNull() }
            if (positions.any { it == null }) false else {
                val ordered = positions.filterNotNull().sortedBy { it.first }
                val begin = ordered[0].first + ordered[0].second
                if (begin > ordered[1].first) false else {
                    val between = words.subList(begin, ordered[1].first)
                    between.size <= 3 && between.any { it in pairWords }
                }
            }
        }
        if (!explicitPair) return null
        val identities = teams.map { it.id }.toSet()
        val matches = known.filter { it.competition == league && setOf(it.homeTeam.id, it.awayTeam.id) == identities &&
            kotlin.math.abs(it.faceoff - start) <= 60 * 60_000L && stop > it.faceoff }
        if (matches.size > 1) return null
        val verified = matches.singleOrNull()
        // Without a known fixture, a pair/league alone is still insufficient to assert a live game.
        if (verified == null && tokens.none { it in listOf("live", "direkt", "direktsandning", "direktsant") }) return null
        val fixture = verified ?: SportFixture("epg:${league.id}:${identities.sorted().joinToString(":")}:$start", league,
            teams[0], teams[1], Instant.ofEpochMilli(start), MatchStatus.SCHEDULED, null)
        return HockeyEpgFixture(fixture, start, stop, verified == null)
    }
}

/** Small visible guide. Exact fixture identities keep concurrent games and channel numbers separate. */
internal class HockeyEpgCollector(selection: SportPreferences, now: Long, known: List<SportFixture> = emptyList()) {
    private val parser = HockeyEpgParser(selection, now, known)
    private val matches = linkedMapOf<String, EpgHockeyMatch>()
    private val blockedSlots = mutableMapOf<Long, List<Pair<Long, Long>>>()
    private val seenChannels = mutableSetOf<Long>()
    fun add(channelId: Long, programme: EpgProgrammeEntity) {
        if (channelId <= 0) return
        if (channelId !in seenChannels && seenChannels.size >= 128) return
        seenChannels += channelId
        val value = parser.parse(programme) ?: return
        if (blockedSlots[channelId].orEmpty().any { value.start < it.second && it.first < value.stop }) return
        val conflicts = matches.values.filter { match -> match.fixture.id != value.fixture.id && channelId in match.channelIds &&
            value.start < (match.channelStops[channelId] ?: 0) && (match.channelStarts[channelId] ?: Long.MAX_VALUE) < value.stop }
        if (conflicts.isNotEmpty()) {
            val from = minOf(value.start, conflicts.minOf { it.channelStarts.getValue(channelId) })
            val until = maxOf(value.stop, conflicts.maxOf { it.channelStops.getValue(channelId) })
            blockedSlots[channelId] = (blockedSlots[channelId].orEmpty() + (from to until)).takeLast(32)
            conflicts.forEach { match ->
                val updated = match.copy(channelIds = match.channelIds - channelId, channelStops = match.channelStops - channelId,
                    channelStarts = match.channelStarts - channelId)
                if (updated.channelIds.isEmpty()) matches.remove(match.fixture.id) else matches[match.fixture.id] = updated
            }
            return
        }
        if (value.fixture.id !in matches && matches.size >= 32) {
            val last = matches.values.maxBy { it.fixture.faceoff }
            if (value.fixture.faceoff >= last.fixture.faceoff) return
            matches.remove(last.fixture.id)
        }
        val previous = matches[value.fixture.id]
        matches[value.fixture.id] = EpgHockeyMatch(value.fixture, ((previous?.channelIds.orEmpty()) + channelId).distinct().take(128),
            previous?.channelStops.orEmpty() + (channelId to value.stop), value.timeFromEpg,
            channelStarts = previous?.channelStarts.orEmpty() + (channelId to value.start))
    }
    fun result(truncated: Boolean = false): List<EpgHockeyMatch> = matches.values.sortedBy { it.fixture.faceoff }
        .map { it.copy(truncated = truncated) }
}
