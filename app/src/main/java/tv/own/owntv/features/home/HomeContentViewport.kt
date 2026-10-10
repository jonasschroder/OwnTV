package tv.own.owntv.features.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.ui.theme.stageText

/** Reserve the measured shell status area even when lazy content scrolls upward. */
internal fun Modifier.homeContentViewport(reservedTop: Dp): Modifier = padding(top = reservedTop).clipToBounds()

@Composable
internal fun HomeProgrammeDescription(description: String, modifier: Modifier = Modifier) {
    Text(description, modifier = modifier.fillMaxWidth(), style = stageText(18, 400), color = Color.LightGray,
        maxLines = 3, overflow = TextOverflow.Ellipsis)
}
