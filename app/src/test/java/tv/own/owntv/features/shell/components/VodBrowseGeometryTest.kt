package tv.own.owntv.features.shell.components

import org.junit.Assert.*
import org.junit.Test

class VodBrowseGeometryTest {
    @Test fun tallSavedPanelCannotStarveGridAndZeroSettingStaysHidden() {
        assertEquals(240f, vodBrowseHeroHeight(1080f, 850f), 0f)
        assertEquals(110f, vodBrowseHeroHeight(600f, 500f), 0f)
        assertEquals(0f, vodBrowseHeroHeight(1080f, 0f), 0f)
        assertEquals(0f, vodBrowseHeroHeight(400f, 464f), 0f)
    }
    @Test fun densityZoomAndNarrowWindowsRemainUsableWithoutMoreThanSevenColumns() {
        assertEquals(7, vodBrowseColumns(4000f))
        assertEquals(6, vodBrowseColumns(1400f))
        assertEquals(5, vodBrowseColumns(1200f))
        assertEquals(1, vodBrowseColumns(100f))
    }
}
