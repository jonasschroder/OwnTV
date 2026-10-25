package tv.own.owntv.features.live

import org.junit.Assert.*
import org.junit.Test

class ChannelGridNavigationTest {
    @Test fun verticalMovementKeepsColumnAcrossPartialRow() {
        assertEquals(9, channelGridTarget(4, 12, 4, ChannelGridDirection.DOWN))
        assertEquals(11, channelGridTarget(9, 12, 4, ChannelGridDirection.DOWN))
        assertEquals(9, channelGridTarget(11, 12, 4, ChannelGridDirection.UP))
    }
    @Test fun boundariesNeverWrapOrInventUnavailableItems() {
        assertNull(channelGridTarget(4, 20, 4, ChannelGridDirection.RIGHT))
        assertNull(channelGridTarget(5, 20, 0, ChannelGridDirection.LEFT))
        assertNull(channelGridTarget(19, 20, 4, ChannelGridDirection.DOWN))
        assertNull(channelGridTarget(0, 0, 0, ChannelGridDirection.DOWN))
        assertNull(channelGridTarget(0, 20, 0, ChannelGridDirection.UP))
    }
    @Test fun allTwentyChannelsAreReachableByRowsAndColumns() {
        val seen = mutableSetOf<Int>()
        for (column in 0..4) {
            var index: Int? = column
            while (index != null) { seen += index; index = channelGridTarget(index, 20, column, ChannelGridDirection.DOWN) }
        }
        assertEquals((0..19).toSet(), seen)
    }
}
