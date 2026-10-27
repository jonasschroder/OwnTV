package tv.own.owntv.features.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.EpgShift

/** Exact-fixture window follows the same profile mapping and authorization as the local guide. */
internal suspend fun storedHockeyWindow(dao: EpgDao, channel: ChannelEntity,
    custom: SectionCustomizations, globalShift: Int, from: Long, to: Long,
    accessible: suspend () -> Boolean): List<EpgProgrammeEntity> = withContext(Dispatchers.IO) {
    if (!accessible()) return@withContext emptyList()
    val key = (custom.epgMatchResolver.epgIdFor(channel) ?: channel.epgChannelId)
        ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
    val shift = EpgShift.minutesFor(custom, channel, globalShift)
    val rows = dao.programmesForChannel(key, EpgShift.toStored(from, shift), EpgShift.toStored(to, shift))
        .take(128).map { EpgShift.apply(it, shift) }
    if (accessible()) rows else emptyList()
}

/** The same local mapping/offset rules as Live TV, with authorization on both sides of the read. */
internal suspend fun storedHockeyProgrammes(dao: EpgDao, channel: ChannelEntity,
    custom: SectionCustomizations, globalShift: Int, now: Long,
    accessible: suspend () -> Boolean): List<EpgProgrammeEntity> = withContext(Dispatchers.IO) {
    if (!accessible()) return@withContext emptyList()
    val key = (custom.epgMatchResolver.epgIdFor(channel) ?: channel.epgChannelId)
        ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
    val shift = EpgShift.minutesFor(custom, channel, globalShift)
    val rows = dao.upcoming(key, EpgShift.toStored(now, shift), 128).first()
        .map { EpgShift.apply(it, shift) }.filter { it.startMs <= now + 7 * 24 * 60 * 60_000L }
    if (accessible()) rows else emptyList()
}
