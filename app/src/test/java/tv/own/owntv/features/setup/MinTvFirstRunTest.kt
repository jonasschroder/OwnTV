package tv.own.owntv.features.setup

import org.junit.Assert.*
import org.junit.Test

class MinTvFirstRunTest {
    @Test fun freshInstallOnlyAfterBothStreamsAreLoaded() {
        assertTrue(minTvFirstRunRequired(-1, true, 0))
        assertFalse(minTvFirstRunRequired(null, true, 0))
        assertFalse(minTvFirstRunRequired(-1, false, 0))
    }
    @Test fun restoredProfilesAndStaleActiveIdsNeverCreateAReplacement() {
        assertFalse(minTvFirstRunRequired(-1, true, 1))
        assertFalse(minTvFirstRunRequired(42, true, 0))
        assertFalse(minTvFirstRunRequired(42, true, 2))
    }
}
