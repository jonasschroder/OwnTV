package tv.own.owntv.features.home

import java.time.Instant
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.SwedishChoices

internal data class HockeyCardBroadcast(val label: String?, val playable: ChannelEntity?)

/** A confirmed name is useful even when no unique, authorized source can be played. */
internal fun hockeyCardBroadcast(fixture: SportFixture, local: EpgHockeyMatch?, localChannels: Map<Long, ChannelEntity>,
    assignment: BroadcastAssignment?, resolved: List<ChannelEntity>, complete: Boolean,
    choices: SwedishChoices, now: Long): HockeyCardBroadcast {
    val evidence = local?.takeIf { it.fixture == fixture }
    val external = BroadcastResolver.usable(fixture.broadcastFixture(), assignment, Instant.ofEpochMilli(now))
    val names = external?.channels?.filter { it.linear }?.map { it.name }.orEmpty()
    val rows = hockeyGuideChannels(if (evidence != null) evidence.channelIds.mapNotNull { id ->
        localChannels[id]?.takeIf { (evidence.channelStops[id] ?: 0L) > now &&
            (evidence.channelStarts[id] ?: Long.MAX_VALUE) < (evidence.channelStops[id] ?: 0L) }
    } else resolved.filter { channel -> external?.channels?.any { BroadcastResolver.matches(it, channel.name) } == true }, choices)
        .filterNot { BroadcastResolver.streamingBrand(it.name) }
    val label = (if (evidence != null) rows.map { it.name } else names).distinct().joinToString().takeIf { it.isNotBlank() }
    return HockeyCardBroadcast(label, preferredHockeyChannel(rows, choices).takeIf {
        fixture.status == MatchStatus.SCHEDULED && if (evidence != null) !evidence.truncated else complete
    })
}

/** The foreground cache expires independently of the programme stop time, including offline failures. */
internal fun freshHockeyGuide(matches: List<EpgHockeyMatch>, checkedAt: Long, now: Long): List<EpgHockeyMatch> =
    if (checkedAt > 0 && now >= checkedAt && now - checkedAt <= 3 * 60_000L) unexpiredHockeyMatches(matches, now) else emptyList()
