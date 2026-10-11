package tv.own.owntv.features.metadata

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.MetadataSearchResult
import tv.own.owntv.core.metadata.MetadataType

class ConservativeMetadataTest {
    private fun hit(id: Int, title: String, year: Int? = null, type: MetadataType = MetadataType.MOVIE,
        original: String? = null) = MetadataSearchResult(id, type, title, original, year, null, null, 0.0)
    @Test fun remakesWithoutYearAreAmbiguousRatherThanPopularityGuesses() {
        assertTrue(ConservativeMetadataProvider.choose("Dune", null, MetadataType.MOVIE,
            listOf(hit(1, "Dune", 1984), hit(2, "Dune", 2021)))!!.isEmpty())
        assertEquals(2, ConservativeMetadataProvider.choose("Dune", 2021, MetadataType.MOVIE,
            listOf(hit(1, "Dune", 1984), hit(2, "Dune", 2021)))!!.single().tmdbId)
    }
    @Test fun movieAndSeriesIdentitiesAndInvalidIdsCannotBeMixed() {
        val found = ConservativeMetadataProvider.choose("Fargo", 2014, MetadataType.TV,
            listOf(hit(9, "Fargo", 2014), hit(0, "Fargo", 2014, MetadataType.TV), hit(3, "Fargo", 2014, MetadataType.TV)))!!
        assertEquals(listOf(3), found.map { it.tmdbId })
    }
    @Test fun originalTitlesCanMatchAndDuplicateHitsAreNotFalseAmbiguity() {
        val localized = hit(7, "Inception på annat språk", 2010, original = "Inception")
        assertEquals(7, ConservativeMetadataProvider.choose("Inception", 2010, MetadataType.MOVIE,
            listOf(localized, localized))!!.single().tmdbId)
    }
    @Test fun transportFailureRetainsNullAndLowConfidenceIsEmpty() {
        assertNull(ConservativeMetadataProvider.choose("Dune", null, MetadataType.MOVIE, null))
        assertTrue(ConservativeMetadataProvider.choose("Dune", null, MetadataType.MOVIE, listOf(hit(1, "Other")))!!.isEmpty())
    }
    @Test fun substringPlusYearIsStillNotAnExactIdentity() {
        assertTrue(ConservativeMetadataProvider.choose("Alien", 1979, MetadataType.MOVIE,
            listOf(hit(1, "Alien expanded", 1979)))!!.isEmpty())
        assertEquals(2, ConservativeMetadataProvider.choose("Alien", 1979, MetadataType.MOVIE,
            listOf(hit(1, "Alien expanded", 1979), hit(2, "Alien", 1979)))!!.single().tmdbId)
    }
    @Test fun destructiveNormalizationAbstainsButKnownDecorationsAreAccepted() {
        listOf("1917", "2001: A Space Odyssey (1968)", "Class of 1999", "Mission: Impossible (2023)",
            "Birdman (The Unexpected Virtue of Ignorance)").forEach {
            // Mission: Impossible is safe; colon inside an ordinary title is meaningful.
            if (it.startsWith("Mission")) assertTrue(it, ConservativeTitles.safeForAutoMatch(it))
            else assertFalse(it, ConservativeTitles.safeForAutoMatch(it))
        }
        listOf("SE | Forrest Gump (1994) [HD]", "EN: Dune (2021)", "MAD MAX - Fury Road").forEach {
            assertTrue(it, ConservativeTitles.safeForAutoMatch(it))
        }
    }
}
