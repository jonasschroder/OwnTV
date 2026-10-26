package tv.own.owntv.features.home

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.SwedishChannels

/** Display ordering only. This never establishes fixture/broadcaster evidence or source equivalence. */
internal fun hockeyChannelNumber(raw: String): Int? {
    val label = SwedishChannels.recognize(raw)?.label ?: raw.trim()
    if (label.equals("TV4 Hockey", true)) return 0
    return Regex("^TV4 Sport Live ([1-9][0-9]{0,2})$", RegexOption.IGNORE_CASE)
        .matchEntire(label)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun sortHockeyChannels(channels: List<ChannelEntity>): List<ChannelEntity> =
    channels.sortedWith(compareBy<ChannelEntity> { hockeyChannelNumber(it.name) == null }
        .thenBy { hockeyChannelNumber(it.name) ?: Int.MAX_VALUE })

/** A manual choice is useful but never upgrades itself into a verified hero playback action. */
internal fun verifiedSportHeroChannel(direct: ChannelEntity?, manual: ChannelEntity?, reliable: List<ChannelEntity>, complete: Boolean): ChannelEntity? =
    direct?.takeIf { channel -> manual == null && complete && reliable.any {
        it.id == channel.id && it.sourceId == channel.sourceId &&
            tv.own.owntv.core.customize.CustomizeKeys.channel(it) == tv.own.owntv.core.customize.CustomizeKeys.channel(channel)
    } }

/** A rail is channel browsing, not event confirmation: use the normal saved-source/quality policy. */
internal fun logicalHockeyChannels(channels: List<ChannelEntity>, choices: tv.own.owntv.features.live.SwedishChoices): List<ChannelEntity> {
    val candidates = hockeyGuideChannels(channels, choices).filter { hockeyChannelNumber(it.name) != null }
    val variants = candidates.mapNotNull { ch -> SwedishChannels.recognize(ch.name)?.let { name ->
        tv.own.owntv.features.live.SwedishVariant(ch.id, ch.sourceId, tv.own.owntv.core.customize.CustomizeKeys.channel(ch), ch.name, name, ch.number)
    } }
    return sortHockeyChannels(SwedishChannels.group(variants, choices.order, choices.hidden).mapNotNull { group ->
        val chosen = group.preferred(choices.preferred[group.identity], choices.showUhd)
        candidates.singleOrNull { it.id == chosen?.id && it.sourceId == chosen.sourceId }
    })
}
