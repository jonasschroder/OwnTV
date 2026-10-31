package tv.own.owntv.features.live

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.features.catalog.readCatalog

internal data class SwedishRowContext(val profileId: Long, val sourceIds: List<Long>,
    val custom: SectionCustomizations, val offset: Int)
private data class StoredRowKey(val epgKey: String, val shift: Int)

/** Existing bundled Room connection/index, one batch, at most two title-only programmes per key.
 * EPG keys deliberately span independent XMLTV sources, exactly like Core's nowPlaying/upcoming.
 * Authorize the IPTV channels/profile on both sides; never restrict guide source IDs to playlist IDs.
 */
internal suspend fun storedSwedishRows(db: OwnTVDatabase, channels: List<ChannelEntity>,
    context: SwedishRowContext, at: Long, accessible: suspend () -> Boolean): Map<SwedishRowKey, SwedishRowInfo> = withContext(Dispatchers.IO) {
    if (!accessible()) return@withContext emptyMap()
    val rows = channels.distinctBy { it.id }.take(SWEDISH_EPG_WINDOW)
    val requests = rows.map { ch ->
        val key = (context.custom.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
            ?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        ch to key?.let { StoredRowKey(it, EpgShift.minutesFor(context.custom, ch, context.offset)) }
    }
    val keys = requests.mapNotNull { it.second }.distinct()
    val found = HashMap<StoredRowKey, Pair<SwedishProgramme?, SwedishProgramme?>>()
    if (keys.isNotEmpty()) {
        val values = keys.joinToString(",") { "(?,?,?)" }
        val arguments = keys.flatMapIndexed { index, key -> listOf<Any>(index, key.epgKey, EpgShift.toStored(at, key.shift)) }.toTypedArray()
        // Each scalar probe has its own LIMIT 1 and uses (epgChannelId,startMs). No wide time-window
        // projection, unbounded GROUP BY, descriptions, provider fallback or guide-wide allocation.
        val coroutine = currentCoroutineContext()
        db.readCatalog("""
            WITH requested(slot,epgKey,at) AS (VALUES $values)
            SELECT r.slot,n.id,substr(n.title,1,240),n.startMs,n.stopMs,
                   x.id,substr(x.title,1,240),x.startMs,x.stopMs
            FROM requested r
            LEFT JOIN epg_programmes n ON n.id=(
                SELECT id FROM epg_programmes WHERE epgChannelId=r.epgKey
                AND startMs<=r.at AND startMs>r.at-86400000 AND stopMs>r.at AND stopMs>startMs
                AND trim(title)!='' ORDER BY startMs DESC,id DESC LIMIT 1)
            LEFT JOIN epg_programmes x ON x.id=(
                SELECT id FROM epg_programmes WHERE epgChannelId=r.epgKey
                AND startMs>r.at AND startMs>=coalesce(n.stopMs,r.at) AND startMs<=r.at+86400000
                AND stopMs>startMs AND trim(title)!='' ORDER BY startMs ASC,id ASC LIMIT 1)
        """.trimIndent(), arguments, tv.own.owntv.features.catalog.NativeReadKind.GUIDE_SUMMARIES) { cursor ->
            while (cursor.moveToNext()) {
                coroutine.ensureActive()
                val key = keys[cursor.getInt(0)]; val delta = key.shift * 60_000L
                fun programme(column: Int): SwedishProgramme? = if (cursor.isNull(column)) null else
                    SwedishProgramme(cursor.getLong(column), cursor.getString(column + 1),
                        cursor.getLong(column + 2) + delta, cursor.getLong(column + 3) + delta)
                found[key] = programme(1) to programme(5)
            }
        }
    }
    if (!accessible()) return@withContext emptyMap()
    requests.associate { (ch, key) -> SwedishRowKey(ch.id, ch.sourceId,
        tv.own.owntv.core.customize.CustomizeKeys.channel(ch)) to
        SwedishRowInfo(ch.displayLogoUrl, found[key]?.first, found[key]?.second).at(at) }
}
