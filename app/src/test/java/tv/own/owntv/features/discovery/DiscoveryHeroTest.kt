package tv.own.owntv.features.discovery

import org.junit.Assert.*
import org.junit.Test

class DiscoveryHeroTest {
    @Test fun realImagePathsStayOnOfficialBoundedImageEndpoint() {
        assertEquals("https://image.tmdb.org/t/p/w780/real-art.jpg", DiscoveryHeroSelection.backdrop("/real-art.jpg"))
        for (path in listOf("https://evil.example/x.jpg", "//evil.example/x.jpg", "/../x.jpg", "/x.jpg?token=private", "/" + "a".repeat(260) + ".jpg"))
            assertNull(path, DiscoveryHeroSelection.backdrop(path))
    }
    @Test fun synopsisAndRatingsCannotIntroduceUnboundedOrInventedDetails() {
        assertNull(DiscoveryHeroSelection.synopsis("  "))
        assertEquals(1600, DiscoveryHeroSelection.synopsis("x".repeat(20000))!!.length)
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 11.0)) assertNull(DiscoveryHeroSelection.rating(value))
        assertEquals(8.1, DiscoveryHeroSelection.rating(8.1)!!, 0.0)
    }
}
