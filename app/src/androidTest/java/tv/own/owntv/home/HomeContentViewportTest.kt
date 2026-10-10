package tv.own.owntv.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.home.*

/** Production viewport and EPG text on CI's 1080p TV hardware profile, including lazy scrolling. */
class HomeContentViewportTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun programmeDescriptionIsBoundedAndCannotScrollAcrossClockArea() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().homeContentViewport(100.dp).testTag("viewport")) {
                        item { Spacer(Modifier.height(120.dp)) }
                        item { Box(Modifier.widthIn(max = 560.dp)) {
                            HomeProgrammeDescription("Long synthetic television programme description. ".repeat(200), Modifier.testTag("description"))
                        } }
                        item { Spacer(Modifier.height(1500.dp)) }
                    }
                    Box(Modifier.align(Alignment.TopEnd).width(300.dp).height(80.dp).testTag("clock"))
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("description").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(3, layouts.single().lineCount)
        assertTrue(layouts.single().hasVisualOverflow)
        compose.onNodeWithTag("viewport").performScrollToIndex(1)
        val clock = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val description = compose.onNodeWithTag("description").fetchSemanticsNode().boundsInRoot
        assertTrue(viewport.top >= clock.bottom)
        assertTrue(description.top >= viewport.top)
        assertTrue(description.right <= viewport.right)
    }
}
