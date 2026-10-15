package tv.own.owntv.features.live

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.parser.XtEpgEntry

@OptIn(ExperimentalCoroutinesApi::class)
class SwedishFocusedGuideTest {
    private val owner = SwedishRowContext(1, listOf(10), SectionCustomizations(), 0)
    private val channel = ChannelEntity(id = 1, sourceId = 10, name = "TV4 HD", remoteId = "tv4", streamUrl = "https://example.invalid/fixture")
    private val guide = EpgNowNext(XtEpgEntry("Stored", "Existing description", 0, 10_000), XtEpgEntry("Next", null, 10_000, 20_000))

    @Test fun rapidFocusReadsOnlyFinalChannelAndClearsPreviousDataImmediately() = runTest {
        val context = MutableStateFlow<SwedishRowContext?>(owner)
        val selected = MutableStateFlow<ChannelEntity?>(channel)
        val revision = MutableStateFlow(0)
        val reads = mutableListOf<Long>(); val values = mutableListOf<SwedishGuideSnapshot?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            swedishFocusedGuide(context, selected, revision, {}) { ch, _, _ -> reads += ch.id; guide }.collect { values += it }
        }
        runCurrent(); advanceTimeBy(350); runCurrent()
        assertEquals(guide, values.last()!!.guide)
        for (id in 2L..100L) { selected.value = channel.copy(id = id, remoteId = "$id"); runCurrent(); advanceTimeBy(10) }
        assertNull(values.last()); assertEquals(listOf(1L), reads)
        advanceTimeBy(350); runCurrent()
        assertEquals(listOf(1L, 100L), reads)
        assertEquals(100L, values.last()!!.channel.id)
    }

    @Test fun profileMappingOffsetAndSynchronizationChangesInvalidateOnlyCurrentPane() = runTest {
        val context = MutableStateFlow<SwedishRowContext?>(owner)
        val selected = MutableStateFlow<ChannelEntity?>(channel)
        val revision = MutableStateFlow(0)
        val requests = mutableListOf<Pair<SwedishRowContext, Boolean>>()
        val values = mutableListOf<SwedishGuideSnapshot?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            swedishFocusedGuide(context, selected, revision, {}) { _, c, invalidate -> requests += c to invalidate; guide }.collect { values += it }
        }
        runCurrent(); advanceTimeBy(350); runCurrent()
        selected.value = channel.copy(id = 2); runCurrent(); advanceTimeBy(350); runCurrent()
        assertFalse(requests.last().second)
        for (next in listOf(owner.copy(profileId = 2), owner.copy(offset = 60),
            owner.copy(custom = SectionCustomizations(epgMatches = mapOf(CustomizeKeys.channel(channel) to "manual"))))) {
            context.value = next; runCurrent(); assertNull(values.last())
            advanceTimeBy(350); runCurrent(); assertEquals(next to true, requests.last())
        }
        revision.value++; runCurrent(); assertNull(values.last()); advanceTimeBy(350); runCurrent()
        assertTrue(requests.last().second)
        context.value = owner.copy(sourceIds = listOf(99)); runCurrent(); advanceTimeBy(1000); runCurrent()
        assertNull(values.last())
    }

    @Test fun interruptedReadAndReaderFailureCannotPublishStaleMetadataOrCrash() = runTest {
        val context = MutableStateFlow<SwedishRowContext?>(owner)
        val selected = MutableStateFlow<ChannelEntity?>(channel)
        val revision = MutableStateFlow(0)
        val values = mutableListOf<SwedishGuideSnapshot?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            swedishFocusedGuide(context, selected, revision, {}) { ch, _, _ ->
                if (ch.id == 1L) withContext(NonCancellable) { delay(500); guide } else throw IllegalStateException("Synthetic missing guide")
            }.collect { values += it }
        }
        runCurrent(); advanceTimeBy(351); runCurrent()
        context.value = owner.copy(profileId = 2); selected.value = channel.copy(id = 2); runCurrent()
        advanceTimeBy(1500); runCurrent()
        assertFalse(values.filterNotNull().any { it.context.profileId == 1L })
        assertEquals(2L, values.last()!!.context.profileId); assertNull(values.last()!!.guide)
    }

    @Test fun snapshotRequiresFullIdentityAndCurrentContextAndDropsExpiredDetails() {
        val snapshot = SwedishGuideSnapshot(owner, SwedishRowKey(channel.id, channel.sourceId, CustomizeKeys.channel(channel)), 1, guide)
        assertEquals("Existing description", snapshot.forChannel(channel, owner, 5_000)!!.now!!.description)
        assertNull(snapshot.forChannel(channel.copy(remoteId = "reused-id"), owner, 5_000))
        assertNull(snapshot.forChannel(channel.copy(sourceId = 99), owner, 5_000))
        assertNull(snapshot.forChannel(channel, null, 5_000)) // Profile/source handover has no matching metadata yet.
        assertNull(snapshot.forChannel(channel, owner.copy(profileId = 2), 5_000))
        assertNull(snapshot.forChannel(channel, owner.copy(offset = 1), 5_000))
        assertEquals("Next", snapshot.forChannel(channel, owner, 10_000)!!.now!!.title)
        assertNull(snapshot.forChannel(channel, owner, 20_000)!!.now)
    }

    @Test fun profileChangeClearsCachedGuidesForOtherChannelsRevisitedLater() = runTest {
        val context = MutableStateFlow<SwedishRowContext?>(owner)
        val selected = MutableStateFlow<ChannelEntity?>(channel)
        val cachedById = mutableMapOf<Long, EpgNowNext>() // Core reader's channel-ID cache contract.
        val values = mutableListOf<SwedishGuideSnapshot?>()
        var resets = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            swedishFocusedGuide(context, selected, MutableStateFlow(0), { cachedById.clear(); resets++ }) { ch, c, invalidate ->
                if (invalidate) cachedById.remove(ch.id)
                cachedById.getOrPut(ch.id) { guide.copy(now = guide.now!!.copy(title = "Profile ${c.profileId} channel ${ch.id}")) }
            }.collect { values += it }
        }
        suspend fun settle() { runCurrent(); advanceTimeBy(350); runCurrent() }
        settle()
        selected.value = channel.copy(id = 2); settle()
        assertEquals(setOf(1L, 2L), cachedById.keys); assertEquals(1, resets)
        context.value = owner.copy(profileId = 2); settle()
        assertEquals(setOf(2L), cachedById.keys); assertEquals(2, resets)
        selected.value = channel; settle()
        assertEquals("Profile 2 channel 1", values.last()!!.guide!!.now!!.title)
        assertEquals(2, resets) // Ordinary channel navigation retains the current-context cache.
    }

    @Test fun reopeningAfterBackgroundClearsCacheChangedByAnotherProfileReader() = runTest {
        var cached: EpgNowNext? = null
        var resets = 0
        val source = swedishFocusedGuide(flowOf(owner), flowOf(channel), flowOf(0), { cached = null; resets++ }) { _, _, _ ->
            cached ?: guide.also { cached = it }
        }
        assertEquals(guide, source.filterNotNull().single().guide)
        cached = guide.copy(now = guide.now!!.copy(title = "Another profile's manual mapping"))
        assertEquals(guide, source.filterNotNull().single().guide)
        assertEquals(2, resets)
    }
}
