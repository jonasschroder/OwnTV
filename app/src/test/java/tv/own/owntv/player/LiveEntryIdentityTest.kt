package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class LiveEntryIdentityTest {
    @Test fun diagnosticsDistinguishNationalAndRegionalServicesWithoutRetainingProviderLabels() {
        for ((raw, expected) in listOf("SVT1 HD" to LiveLogicalChannel.SVT1,
            "SVT2" to LiveLogicalChannel.SVT2, "SE: SVT1 Värmland FHD" to LiveLogicalChannel.SVT1_VARMLAND,
            "SVT2 Varmland HD" to LiveLogicalChannel.SVT2_VARMLAND)) {
            assertEquals(expected, liveLogicalChannel(tv.own.owntv.features.live.SwedishChannels.recognize(raw)?.identity))
        }
        assertEquals(LiveLogicalChannel.OTHER, liveLogicalChannel("https://private.invalid/user/token"))
    }
    private val channel = ChannelEntity(id = 1, sourceId = 101, remoteId = "svt1", name = "SVT1 HD", streamUrl = "https://example.invalid/synthetic")
    @Test fun sourceAndDrmChangesAreRejectedButGuideArtworkAndDisplayRenamesDoNotBreakPlayback() {
        assertTrue(sameLiveStream(channel, channel.copy(name = "My channel", logoUrl = "https://example.invalid/logo", epgChannelId = "updated-guide", sortOrder = 99)))
        for (other in listOf(channel.copy(sourceId = 102), channel.copy(remoteId = "svt2"), channel.copy(streamUrl = "https://example.invalid/changed"), channel.copy(drmConfig = "changed"), channel.copy(httpHeaders = "changed"), channel.copy(manifestType = "dash")))
            assertFalse(sameLiveStream(channel, other))
        assertFalse(sameLiveStream(channel, null))
    }
    @Test fun freshTokenCanBeResolvedForTheSameRemoteServiceWithoutRedirectingANameOnlyChannel() {
        val fresh = channel.copy(streamUrl = "https://example.invalid/new-token")
        assertTrue(sameLiveSelection(channel, fresh))
        assertFalse(sameLiveSelection(channel.copy(remoteId = null), fresh.copy(remoteId = null)))
        assertTrue(sameLiveSelection(channel.copy(remoteId = null, name = "Custom label"), channel.copy(remoteId = null)))
    }
    @Test fun routineSourceSyncDoesNotAbortButChangedCredentialsOrOverridesDo() {
        val source = SourceEntity(id = 101, name = "Synthetic", type = SourceType.M3U, url = "https://example.invalid/list", createdAt = 0, lastSyncAt = 0)
        assertTrue(sameLiveSource(source, source.copy(lastSyncAt = 1234)))
        assertFalse(sameLiveSource(source, source.copy(username = "changed")))
        assertFalse(sameLiveSource(source, source.copy(userAgent = "changed")))
        assertFalse(sameLiveSource(source, source.copy(preferHls = true)))
        assertFalse(sameLiveSource(source, null))
    }
}
