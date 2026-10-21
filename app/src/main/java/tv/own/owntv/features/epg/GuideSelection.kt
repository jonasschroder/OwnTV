package tv.own.owntv.features.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** A single selection for header and cursor, independent of whether a toolbar or row has UI focus. */
@Composable
internal fun rememberGuideSelection(
    channel: ChannelEntity?,
    cursorTime: Long,
    windowStart: Long,
    windowEnd: Long,
    revision: Int,
    loadRows: suspend (ChannelEntity) -> List<EpgProgrammeEntity>,
    loadFull: suspend (Long) -> EpgProgrammeEntity?,
): EpgProgrammeEntity? {
    // Keying the state, not just its producer, prevents one frame of another row's old details.
    val programmes by key(channel, windowStart, windowEnd, revision) {
        produceState<List<EpgProgrammeEntity>?>(null) {
            channel?.let { ch ->
                value = loadRows(ch)
                repeat(2) {
                    if (value?.isNotEmpty() == true) return@produceState
                    delay(3_000)
                    value = loadRows(ch)
                }
            }
        }
    }
    val programme = programmeAt(programmes, cursorTime)
    val full by key(programme, revision) {
        produceState<EpgProgrammeEntity?>(null) {
            value = programme?.id?.takeIf { it > 0 }?.let { loadFull(it) }
        }
    }
    return guideProgrammeDetails(programme, full)
}
