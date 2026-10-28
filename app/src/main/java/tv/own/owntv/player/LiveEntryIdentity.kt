package tv.own.owntv.player

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity

/** Re-read rows, preserving explicit source choices and display renames. Never trust a reused ID. */
internal fun sameLiveSelection(selected: ChannelEntity, fresh: ChannelEntity?): Boolean = fresh != null &&
    selected.id == fresh.id && selected.sourceId == fresh.sourceId && selected.remoteId == fresh.remoteId &&
    (selected.remoteId != null || selected.streamUrl == fresh.streamUrl)

/** Harmless logo/EPG/order/label changes must not randomly reject a valid tune during a sync. */
internal fun sameLiveStream(selected: ChannelEntity, fresh: ChannelEntity?): Boolean = fresh != null && sameLiveSelection(selected, fresh) &&
    selected.streamUrl == fresh?.streamUrl && selected.httpHeaders == fresh.httpHeaders &&
    selected.drmConfig == fresh.drmConfig && selected.manifestType == fresh.manifestType && selected.directSource == fresh.directSource

internal fun sameLiveSource(source: SourceEntity, fresh: SourceEntity?): Boolean =
    fresh != null && source.copy(lastSyncAt = fresh.lastSyncAt) == fresh
