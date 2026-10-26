package tv.own.owntv.features.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/** Abstract decoration only: never implies programme imagery, a team logo or a broadcaster. */
internal fun Modifier.neutralArtwork(accent: Color = MinTvTeal): Modifier = drawBehind {
    drawRect(Brush.linearGradient(listOf(Color(0xFF14272B), Color(0xFF101A24))))
    drawCircle(accent.copy(alpha = .09f), size.height * .7f, Offset(size.width * .84f, size.height * .1f))
    drawPath(Path().apply {
        moveTo(size.width * .38f, size.height)
        lineTo(size.width * .73f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        close()
    }, accent.copy(alpha = .045f))
    drawLine(accent.copy(alpha = .12f), Offset(size.width * .55f, size.height), Offset(size.width * .9f, 0f), 1f)
}
