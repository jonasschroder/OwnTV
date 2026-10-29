package tv.own.owntv.features.live.source

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.features.live.*

class ChannelSourceResolverTest {
    @Test fun regionalSavedQualityAndMissingVariantCannotCrossIntoNationalService() {
        val national = row(10, "SVT1 HD")
        val hd = row(11, "SVT1 Värmland HD")
        val fhd = row(12, "SVT1 Värmland FHD", 102)
        val groups = SwedishChannels.group(listOf(national, hd, fhd)).associateBy { it.identity }
        val choices = SwedishChoices(preferred = mapOf("svt1" to national.stableKey, "svt1varmland" to fhd.stableKey))
        assertEquals(national, ChannelSourceResolver.resolve(groups.getValue("svt1"), choices).selected)
        assertEquals(fhd, ChannelSourceResolver.resolve(groups.getValue("svt1varmland"), choices).selected)
        val missing = groups.getValue("svt1varmland").copy(variants = listOf(hd))
        assertEquals(hd, ChannelSourceResolver.resolve(missing, choices).selected)
        assertEquals(ChannelSourceStatus.SAVED_MISSING, ChannelSourceResolver.resolve(missing, choices).status)
        assertNull(ChannelSourceResolver.resolve(missing.copy(variants = emptyList()), choices).selected)
        assertEquals(fhd.stableKey, choices.preferred["svt1varmland"])
        // An explicit programme/provider row remains exact even if a different regional quality is saved.
        for (intent in listOf(ChannelSourceIntent.GUIDE_PROGRAMME, ChannelSourceIntent.PROVIDER_EXACT)) {
            assertEquals(hd, ChannelSourceResolver.resolve(groups.getValue("svt1varmland"), choices, intent, hd.stableKey).selected)
            assertNull(ChannelSourceResolver.resolve(groups.getValue("svt1varmland"), choices, intent, national.stableKey).selected)
        }
    }
    private fun row(id: Long, raw: String, source: Long = 101, remote: String = "$id") =
        SwedishVariant(id, source, "$source:$remote", raw, SwedishChannels.recognize(raw)!!, null)
    private val hd = row(1, "SE: TV4 HD")
    private val fhd = row(2, "TV4 FHD")
    private val uhd = row(3, "TV4 4K", 102)
    private val group = SwedishGroup("tv4", "TV4", listOf(hd, fhd, uhd))
    @Test fun saveOnlyOrOpenPickerCannotAutotuneAnUpdatedPreferenceButOtherChannelBrowsingCanPreview() {
        assertFalse(sourceChoiceAllowsPreview("tv4", "tv4", false))
        assertFalse(sourceChoiceAllowsPreview("svt1", "tv4", true))
        assertFalse(sourceChoiceAllowsPreview(null, null, false))
        assertTrue(sourceChoiceAllowsPreview("svt1", "tv4", false))
        assertTrue(sourceChoiceAllowsPreview("tv4", null, false)) // new profile/visit has no suppression
    }
    @Test fun automaticPolicyAndExplicitAdvertisedQualityRemainUnchanged() {
        val auto = ChannelSourceResolver.resolve(group, SwedishChoices())
        assertEquals(hd, auto.selected)
        assertEquals(listOf(ChannelQuality.HD, ChannelQuality.FHD, ChannelQuality.UHD), auto.alternatives.map { it.name.quality })
        val selected = ChannelSourceResolver.resolve(group, SwedishChoices(preferred = mapOf("tv4" to uhd.stableKey)))
        assertEquals(uhd, selected.selected); assertEquals(ChannelSourceStatus.SAVED, selected.status)
    }
    @Test fun removedPreferenceFallsBackWithoutDeletingTheSavedKey() {
        val choices = SwedishChoices(preferred = mapOf("tv4" to "deleted:source"))
        val result = ChannelSourceResolver.resolve(group, choices)
        assertEquals(hd, result.selected); assertEquals(ChannelSourceStatus.SAVED_MISSING, result.status)
        assertEquals("deleted:source", choices.preferred["tv4"])
    }
    @Test fun stableKeySurvivesRefreshedRoomIds() {
        val refreshed = fhd.copy(id = 200)
        assertEquals(refreshed, ChannelSourceResolver.resolve(group.copy(variants = listOf(hd, refreshed)),
            SwedishChoices(preferred = mapOf("tv4" to fhd.stableKey))).selected)
    }
    @Test fun providerProgrammeRecordingAndUnknownIntentsAlwaysStayExact() {
        val choices = SwedishChoices(preferred = mapOf("tv4" to uhd.stableKey))
        for (intent in listOf(ChannelSourceIntent.PROVIDER_EXACT, ChannelSourceIntent.GUIDE_PROGRAMME,
            ChannelSourceIntent.RECORDING, ChannelSourceIntent.UNKNOWN)) {
            val result = ChannelSourceResolver.resolve(group, choices, intent, hd.stableKey)
            assertEquals(hd, result.selected); assertTrue(result.alternatives.isEmpty())
            assertNull(ChannelSourceResolver.resolve(group, choices, intent, "removed").selected)
        }
    }
    @Test fun regionsPlusOneAudioOttAndUncertainNamesNeverBecomeTv4Alternatives() {
        for (name in listOf("SE: TV4 Stockholm HD", "SE: TV4 +1 HD", "SE: TV4 English HD", "SE: TV4 Catch-up HD", "SE: TV4 alternate match HD")) {
            val other = row(10, name)
            assertTrue(ChannelSourceResolver.resolve(SwedishGroup(other.name.identity, other.name.label, listOf(other)), SwedishChoices()).alternatives.isEmpty())
            assertEquals(listOf(hd), ChannelSourceResolver.alternatives(group.copy(variants = listOf(hd, other))))
        }
        assertNull(SwedishChannels.recognize("SE: TV4 Play Hockey"))
    }
    @Test fun duplicateOrAmbiguousKeysAreNotArbitrarilyChosen() {
        val ambiguous = fhd.copy(id = 200)
        val result = ChannelSourceResolver.resolve(group.copy(variants = listOf(hd, fhd, ambiguous)),
            SwedishChoices(preferred = mapOf("tv4" to fhd.stableKey)))
        assertEquals(listOf(hd), result.alternatives); assertEquals(hd, result.selected)
        assertEquals(ChannelSourceStatus.SAVED_MISSING, result.status)
        assertEquals(listOf(hd), SwedishChannels.group(listOf(hd, fhd, ambiguous)).single().variants)
        assertTrue(SwedishChannels.group(listOf(fhd, ambiguous)).isEmpty())
    }
    @Test fun hiddenGroupsAndUnavailableUhdHaveNoAutomaticPlay() {
        assertTrue(SwedishChannels.group(listOf(hd, fhd), hidden = setOf("tv4")).isEmpty())
        val onlyUhd = group.copy(variants = listOf(uhd))
        assertNull(ChannelSourceResolver.resolve(onlyUhd, SwedishChoices()).selected)
        assertEquals(uhd, ChannelSourceResolver.resolve(onlyUhd, SwedishChoices(showUhd = true)).selected)
        assertNull(ChannelSourceResolver.resolve(group.copy(variants = emptyList()), SwedishChoices()).selected)
    }
    @Test fun verifiedBroadcastPreferenceCannotEscapeItsEvidenceCandidates() {
        val choices = SwedishChoices(preferred = mapOf("tv4" to uhd.stableKey))
        val result = ChannelSourceResolver.resolve(group.copy(variants = listOf(hd, fhd)), choices, ChannelSourceIntent.VERIFIED_BROADCAST)
        assertEquals(hd, result.selected); assertEquals(ChannelSourceStatus.SAVED_MISSING, result.status)
    }
    @Test fun explicitEpgServicePartitionsAreNotRejoined() {
        val regional = hd.copy(name = hd.name.copy(identity = "tv4:stockholm"))
        assertTrue(ChannelSourceResolver.alternatives(group.copy(variants = listOf(regional))).isEmpty())
        assertEquals(regional, ChannelSourceResolver.resolve(SwedishGroup("tv4:stockholm", "TV4", listOf(regional)),
            SwedishChoices(preferred = mapOf("tv4" to uhd.stableKey))).selected)
    }
}
