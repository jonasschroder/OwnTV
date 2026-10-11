package tv.own.owntv.features.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.ui.theme.mpx

/** The same focus group stays mounted when its asynchronously loaded content changes. */
@Composable
internal fun MatchcenterTabs(section: MatchcenterSection, firstFocus: FocusRequester,
    onSelect: (MatchcenterSection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.mpx), modifier = Modifier.focusRestorer().focusGroup()) {
        MatchcenterSection.entries.forEachIndexed { index, tab ->
            TvCard({ onSelect(tab) }, Modifier.width(if (tab == MatchcenterSection.SETTINGS) 230.mpx else 200.mpx)
                .testTag("sports-tab-${tab.name}")
                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier), selected = tab == section) {
                TvText(stringResource(when (tab) {
                    MatchcenterSection.MATCHES -> R.string.mintv_matches
                    MatchcenterSection.TABLE -> R.string.mintv_shl_table
                    MatchcenterSection.FARJESTAD -> R.string.mintv_my_teams
                    MatchcenterSection.SETTINGS -> R.string.mintv_companion_settings
                }), size = 23, lines = 1, bold = true)
            }
        }
    }
}
