package tv.own.owntv.features.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.model.MediaType

class HomeChannelAccessTest {
    private val profile = ProfileEntity(id = 1, name = "Synthetic", avatarColor = 0, createdAt = 1)
    private val folder = CategoryEntity(id = 10, sourceId = 1, name = "Sport", mediaType = MediaType.LIVE)
    private val row = ChannelEntity(id = 1, sourceId = 1, categoryId = 10, name = "TV4 Hockey", streamUrl = "http://127.0.0.1:9/unavailable")
    private val initial = HomeChannelAccess(profile, setOf(1), SectionCustomizations())

    @Test fun repeatedIdsAndSharedCategoriesReadOncePerOperation() = runTest {
        var accesses = 0; var channelReads = 0; var categoryReads = 0
        val result = readHomeChannels(listOf(1, 1, 2), { accesses++; initial },
            { channelReads++; row.copy(id = it) }, { categoryReads++; folder }, { emptyList() })
        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(2, accesses); assertEquals(2, channelReads); assertEquals(1, categoryReads)
    }
    @Test fun switchingProfileRevokingSourceReusingOwnerOrHidingDuringReadFailsClosed() = runTest {
        val changed = listOf(null, initial.copy(profile = profile.copy(id = 2)),
            initial.copy(profile = profile.copy(createdAt = 2)), initial.copy(sources = emptySet()),
            initial.copy(custom = SectionCustomizations(hiddenItems = mapOf(CustomizeKeys.channel(row) to "Hidden"))))
        for (next in changed) {
            var calls = 0
            assertTrue(readHomeChannels(listOf(1), { if (calls++ == 0) initial else next }, { row }, { folder }, { emptyList() }).isEmpty())
        }
    }
    @Test fun customCategoryExceptionSurvivesButCannotExposeKidsAdultContent() = runTest {
        val hidden = initial.copy(custom = SectionCustomizations(hiddenCategories = setOf(CustomizeKeys.category(folder))))
        assertTrue(readHomeChannels(listOf(1), { hidden }, { row }, { folder }, { emptyList() }).isEmpty())
        assertEquals(1, readHomeChannels(listOf(1), { hidden }, { row }, { folder }, { listOf("custom:visible") }).size)
        val kids = hidden.copy(profile = profile.copy(isKids = true))
        assertTrue(readHomeChannels(listOf(1), { kids }, { row }, { folder.copy(name = "XXX") }, { listOf("custom:visible") }).isEmpty())
    }
    @Test fun nativeFavouritesPreserveLegacyVisibilityOrderAndFailClosedForMissingProfile() {
        val rows = listOf(row, row.copy(id = 2, sourceId = 2))
        assertEquals(listOf(row), nativeHomeFavorites(rows, setOf(1), profile, SectionCustomizations(), listOf(folder)))
        assertTrue(nativeHomeFavorites(rows, setOf(1), null, SectionCustomizations(), listOf(folder)).isEmpty())
        assertTrue(nativeHomeFavorites(rows, setOf(1), profile, SectionCustomizations(hiddenCategories = setOf(CustomizeKeys.category(folder))), listOf(folder)).isEmpty())
        assertTrue(nativeHomeFavorites(rows, setOf(1), profile.copy(isKids = true), SectionCustomizations(), listOf(folder.copy(name = "XXX"))).isEmpty())
    }
    @Test fun cancellationIsNotPublishedAsAnEmptySuccess() = runTest {
        try {
            readHomeChannels(listOf(1), { initial }, { throw CancellationException() }, { folder }, { emptyList() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedRequestIsRejected() = runTest {
        readHomeChannels((1L..129L).toList(), { initial }, { row }, { folder }, { emptyList() })
    }
}
