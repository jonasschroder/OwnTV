package tv.own.owntv.features.home

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.SwedishChannels

/** Display ordering only. This never establishes fixture/broadcaster evidence or source equivalence. */
internal fun hockeyChannelNumber(raw: String): Int? {
    val label = SwedishChannels.recognize(raw)?.label ?: raw.trim()
    val match = Regex("^(?:TV4|TV)\\s+Hockey(?:\\s+Live\\s+([0-9]{1,2}))?(?:\\s+(?:HD|FHD|UHD|4K|SD))?$", RegexOption.IGNORE_CASE)
        .matchEntire(label) ?: return null
    return match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
}

internal fun sortHockeyChannels(channels: List<ChannelEntity>): List<ChannelEntity> =
    channels.sortedWith(compareBy<ChannelEntity> { hockeyChannelNumber(it.name) == null }
        .thenBy { hockeyChannelNumber(it.name) ?: Int.MAX_VALUE })
