package tv.own.owntv.features.live

internal const val CHANNEL_GRID_COLUMNS = 5
internal enum class ChannelGridDirection { LEFT, RIGHT, UP, DOWN }

/** Logical rows, not Compose's nearest rectangle. Null means the edge, never wrap a row. */
internal fun channelGridTarget(index: Int, count: Int, preferredColumn: Int, direction: ChannelGridDirection): Int? {
    if (index !in 0 until count) return null
    val row = index / CHANNEL_GRID_COLUMNS
    return when (direction) {
        ChannelGridDirection.LEFT -> (index - 1).takeIf { index % CHANNEL_GRID_COLUMNS > 0 }
        ChannelGridDirection.RIGHT -> (index + 1).takeIf { it < count && it / CHANNEL_GRID_COLUMNS == row }
        ChannelGridDirection.UP, ChannelGridDirection.DOWN -> {
            val targetRow = row + if (direction == ChannelGridDirection.UP) -1 else 1
            val first = targetRow * CHANNEL_GRID_COLUMNS
            if (targetRow < 0 || first >= count) null
            else minOf(first + preferredColumn.coerceIn(0, CHANNEL_GRID_COLUMNS - 1), count - 1)
        }
    }
}
