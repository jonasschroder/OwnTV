package tv.own.owntv.features.home

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class HomeChannelDefaultsTest {
    private fun channel(id: Long, name: String, source: Long = 1, url: String = "https://example.invalid/configured") = ChannelEntity(id = id, sourceId = source, name = name, streamUrl = url)
    @Test fun matchesActualCountryAndQualityNames() {
        listOf("TV4 Hockey SE", "SE | TV4 HOCKEY HD", "TV4 Hockey SE FHD", "SE: TV4 Hockey FHD").forEach {
            assertEquals(it, initialHockeyChannel(listOf(channel(1, it)))?.name)
        }
    }
    @Test fun refusesSportLivePpvPlayAndUnavailableUrls() {
        assertNull(initialHockeyChannel(listOf("TV4 Sport Live 1", "TV4 Sport Live 2", "TV4 Sport Live 3", "TV4 Sport Live 4", "TV4 Play Hockey", "TV4 Hockey PPV", "NO EVENT STREAMING", "TV4 Hockey EXCLUSIVE").mapIndexed { i, name -> channel(i.toLong(), name) } + channel(99, "TV4 Hockey", url = "")))
    }
    @Test fun prefersDeclaredHdWithDeterministicTieBreak() {
        val channels = listOf(channel(4, "TV4 Hockey FHD"), channel(3, "TV4 Hockey SE"), channel(2, "SE TV4 Hockey HD"), channel(1, "TV4 Hockey HD"))
        assertEquals(1L, initialHockeyChannel(channels)?.id)
        assertEquals(initialHockeyChannel(channels), initialHockeyChannel(channels.reversed()))
    }
    @Test fun emptyStatesNeverClaimMissingSourceWhileLoading() {
        for (source in listOf(false, true)) for (channels in listOf(false, true)) {
            assertEquals(HomeLibraryStatus.LOADING, homeLibraryStatus(source, true, channels, false))
        }
        assertEquals(HomeLibraryStatus.NO_SOURCE, homeLibraryStatus(false, false, false, false))
        assertEquals(HomeLibraryStatus.NO_CHANNELS, homeLibraryStatus(true, false, false, false))
        assertEquals(HomeLibraryStatus.NO_FAVORITES, homeLibraryStatus(true, false, true, false))
        assertEquals(HomeLibraryStatus.READY, homeLibraryStatus(true, false, true, true))
    }
}
