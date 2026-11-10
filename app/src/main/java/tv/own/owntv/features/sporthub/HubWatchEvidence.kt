package tv.own.owntv.features.sporthub

import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.home.*
import java.time.Instant
import java.time.Duration

internal enum class HubWatchConfidence { GUIDE, EVENT, MANUAL, LIKELY, UNKNOWN }
/** Evidence rank first, existing numeric service order within each rank. */
internal fun hubRankWatchChannels(channels: List<tv.own.owntv.core.database.entity.ChannelEntity>,
    confidence: (tv.own.owntv.core.database.entity.ChannelEntity) -> HubWatchConfidence) =
    sortHockeyChannels(channels).sortedBy { confidence(it).ordinal }

internal fun hubEventConfidence(fixture: SportFixture?, assignment: BroadcastAssignment?, channel: String, now: Instant): HubWatchConfidence {
    val expected = fixture ?: return HubWatchConfidence.UNKNOWN
    val usable = BroadcastResolver.usable(expected.broadcastFixture(), assignment, now) ?: return HubWatchConfidence.UNKNOWN
    if (usable.channels.none { it.linear && BroadcastResolver.matches(it, channel) } || BroadcastResolver.streamingBrand(channel)) return HubWatchConfidence.UNKNOWN
    return if (usable.evidence == BroadcastEvidence.CONFIRMED) HubWatchConfidence.EVENT else HubWatchConfidence.LIKELY
}
internal fun hubWatchConfidence(guide: Boolean, event: HubWatchConfidence, remembered: Boolean): HubWatchConfidence = when {
    guide -> HubWatchConfidence.GUIDE
    event == HubWatchConfidence.EVENT -> HubWatchConfidence.EVENT
    remembered -> HubWatchConfidence.MANUAL
    event == HubWatchConfidence.LIKELY -> HubWatchConfidence.LIKELY
    else -> HubWatchConfidence.UNKNOWN
}
internal data class HubProgrammeProof(val fixture: HubMatch, val programmeId: Long, val start: Long, val stop: Long,
    val checkedAt: Instant, val confidence: HubWatchConfidence = HubWatchConfidence.GUIDE) {
    fun usable(expected: HubMatch, now: Instant): Boolean = fixture == expected && start < stop && stop > now.toEpochMilli() &&
        !now.isBefore(checkedAt) && Duration.between(checkedAt, now).seconds <= 180 && expected.status != HubMatchStatus.REPORTED_FINAL
}

/** Exact title + both complete team names + kickoff interval. A channel name proves nothing. */
internal fun hubProgrammeProof(match: HubMatch, programme: EpgProgrammeEntity, known: List<HubMatch>, now: Instant): HubProgrammeProof? {
    if (match.status == HubMatchStatus.REPORTED_FINAL || programme.stopMs <= now.toEpochMilli() ||
        programme.startMs >= programme.stopMs || programme.stopMs - programme.startMs > 6 * 3_600_000L) return null
    val title = " " + hubTeamNameKey(programme.title.take(300)) + " "
    val competition = when (match.league) {
        "gb.premier" -> "premier league"; "se.allsvenskan" -> "allsvenskan"
        "us.nhl" -> "nhl"; "us.nfl" -> "nfl"; "us.nba" -> "nba"; "fi.liiga" -> "liiga"
        "de.del" -> "del"; "ch.national" -> "national league"; "cz.extraliga" -> "extraliga"
        "at.ice" -> "ice hockey league"; "eu.chl" -> "champions hockey league"
        else -> return null
    }
    val context = " " + hubTeamNameKey(programme.title.take(300) + " " + programme.description.orEmpty().take(4000)) + " "
    if (Regex("\\b(repris|replay|highlights|hojdpunkter|sammandrag|studio|magasin|damer|women|womens|u18|u20|junior|youth|traningsmatch|friendly)\\b").containsMatchIn(context)) return null
    if (!context.contains(" $competition ")) return null
    fun named(game: HubMatch): Boolean {
        val home = hubTeamNameKey(game.home.name); val away = hubTeamNameKey(game.away.name)
        return home.length >= 4 && away.length >= 4 && home != away && title.contains(" $home ") && title.contains(" $away ")
    }
    val start = match.startsAt.toEpochMilli()
    if (!named(match) || programme.startMs > start || programme.stopMs <= start || start - programme.startMs > 90 * 60_000L) return null
    if (known.distinctBy { it.id }.count { named(it) && it.startsAt.toEpochMilli() in programme.startMs until programme.stopMs } != 1) return null
    return HubProgrammeProof(match, programme.id, programme.startMs, programme.stopMs, now)
}

/** The established Swedish parser retains its alias, competition and ambiguity safeguards. */
internal class HubGuideMatcher(private val match: HubMatch, private val swedish: SportSnapshot?, selection: SportPreferences,
    private val known: List<HubMatch>, private val now: Instant) {
    // Build aliases once per bounded scan, not for every programme of every channel.
    private val parser = swedish?.let { HockeyEpgParser(selection.copy(teamIds =
        (selection.teamIds + listOf(match.home.id, match.away.id)).distinct()), now.toEpochMilli(), it.fixtures) }
    fun proof(programme: EpgProgrammeEntity): HubProgrammeProof? {
        if (parser == null) return hubProgrammeProof(match, programme, known, now)
        val parsed = parser.parse(programme) ?: return null
        val expected = swedish?.fixtures?.singleOrNull { it.id == match.id } ?: return null
        if (parsed.fixture != expected || parsed.stop <= now.toEpochMilli()) return null
        return HubProgrammeProof(match, programme.id, parsed.start, parsed.stop, now)
    }
}

/** Namespace new manual choices by profile lifetime; never adopt another owner's reused ID. */
internal fun hubChoiceFixture(match: HubMatch, owner: Long): BroadcastFixture = BroadcastFixture(
    match.league + "@" + owner, match.home.name, match.away.name,
    match.startsAt.atZone(HubStockholm).toLocalDate(), match.startsAt)
