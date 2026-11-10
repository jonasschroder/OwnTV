package tv.own.owntv.features.sporthub

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.features.home.*
import tv.own.owntv.ui.theme.mpx

internal data class HubTile(val key: String, val title: String, val subtitle: String = "", val action: String? = null,
    val selected: Boolean = false)
internal data class HubSection(val key: String, val title: String, val tiles: List<HubTile>)

/** One contextual row, then native content. Stable logical columns and no network on focus. */
@Composable
internal fun NativeSportHubPage(title: String, status: String, sections: List<HubSection>, profile: Long?,
    first: FocusRequester, onEntry: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit,
    onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    val groups = sections.flatMap { section -> section.tiles.chunked(3).mapIndexed { i, tiles ->
        Triple(section.key + i, if (i == 0) section.title else "", tiles)
    } }
    val toolbar = listOf("hub-back", "hub-discover", "hub-calendar", "hub-mine")
    val rows = listOf(NativeRow("hub-toolbar", toolbar, false)) + groups.map { (key, _, tiles) ->
        NativeRow(key, tiles.filter { it.action != null }.map { it.key }, false)
    }
    val nav = rememberNativeRowsNavigation(rows, profile, first, onEntry, onHeader)
    LazyColumn(state = nav.list, contentPadding = PaddingValues(horizontal = 64.mpx, vertical = 28.mpx),
        verticalArrangement = Arrangement.spacedBy(22.mpx), modifier = modifier.fillMaxSize()
            .background(DesignLabBackground).then(nav.modifier).testTag("sport-hub").focusGroup()) {
        item(key = "hub-toolbar") {
            Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                TvText(title, size = 36, bold = true)
                if (status.isNotBlank()) TvText(status, size = 20, color = MinTvMuted, lines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    listOf(R.string.hub_back, R.string.hub_discover, R.string.hub_calendar, R.string.hub_my_teams)
                        .forEachIndexed { i, label -> HomeButton(stringResource(label), { onAction(toolbar[i]) },
                            nav.item(toolbar[i]).testTag(toolbar[i])) }
                }
            }
        }
        itemsIndexed(groups, key = { _, row -> row.first }) { _, (_, label, tiles) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                if (label.isNotBlank()) TvText(label, size = 26, bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(20.mpx), modifier = Modifier.fillMaxWidth()) {
                    tiles.forEach { tile ->
                        val item = Modifier.weight(1f).heightIn(min = 138.mpx)
                        if (tile.action != null) HomeSurface({ onAction(tile.action) },
                            item.then(nav.item(tile.key)).testTag(tile.key), highlighted = tile.selected) {
                            TvText(tile.title, size = 25, bold = true, lines = 2)
                            if (tile.subtitle.isNotBlank()) TvText(tile.subtitle, size = 20, color = MinTvMuted, lines = 4)
                        } else Column(item.clip(RoundedCornerShape(20.mpx)).neutralArtwork().padding(20.mpx), verticalArrangement = Arrangement.spacedBy(8.mpx)) {
                            TvText(tile.title, size = 25, bold = true, lines = 2)
                            if (tile.subtitle.isNotBlank()) TvText(tile.subtitle, size = 20, color = MinTvMuted, lines = 5)
                        }
                    }
                    repeat(3 - tiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
