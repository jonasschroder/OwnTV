package tv.own.owntv.home

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.features.epg.GuideTimeState
import tv.own.owntv.features.epg.rememberGuideSelection

class GuideSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun disposableEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
    }
    private val boundary = 1_792_620_000_000L
    private val channel = ChannelEntity(id = 1, sourceId = 1, name = "Synthetic", streamUrl = "")
    private val old = EpgProgrammeEntity(1, 1, "synthetic", boundary - 60_000, boundary, "Yesterday")
    private val current = EpgProgrammeEntity(2, 1, "synthetic", boundary, boundary + 60_000, "Today")

    private fun shown(text: String) {
        // IO-style delay is deliberately outside Compose's frame-idle guarantee.
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun midnightResumeAndNowRefreshDetailsWithoutRowFocus() {
        var clock by mutableStateOf(GuideTimeState(boundary - 1, lastInteractionElapsed = 0))
        val toolbar = FocusRequester()
        compose.setContent {
            val selected = rememberGuideSelection(channel, clock.cursor, boundary - 60_000, boundary + 60_000, 0,
                { listOf(old, current) }, { id -> listOf(old, current).first { it.id == id }.copy(description = "Full") })
            Column {
                Box(Modifier.testTag("toolbar").focusRequester(toolbar).focusable()) { Text("Toolbar") }
                Text(selected?.title.orEmpty())
            }
            LaunchedEffect(Unit) { toolbar.requestFocus() }
        }
        shown("Yesterday")
        compose.runOnIdle { clock = clock.refreshed(boundary, 1) }
        shown("Today")
        compose.onNodeWithTag("toolbar").assertIsFocused()
        compose.runOnIdle { clock = clock.browse(boundary - 1, 2).refreshed(boundary + 1, 3) }
        shown("Yesterday") // short resume preserves manual selection
        compose.runOnIdle { clock = clock.now(boundary + 1, 4) }
        shown("Today") // Now works while toolbar still owns focus
        compose.runOnIdle { clock = clock.browse(boundary - 1, 5).refreshed(boundary + 1, GuideTimeState.IDLE_RESET_MS + 5) }
        shown("Today")
        compose.onNodeWithTag("toolbar").assertIsFocused()
    }

    @Test fun refreshedProgrammeAndChannelNeverRetainOldDetails() {
        var revision by mutableStateOf(0)
        var row by mutableStateOf<ChannelEntity?>(channel)
        compose.setContent {
            val selected = rememberGuideSelection(row, boundary, boundary - 60_000, boundary + 60_000, revision,
                { delay(100); listOf(current.copy(title = if (revision == 0) "Original" else "Refreshed")) },
                { delay(100); current.copy(title = if (revision == 0) "Original" else "Refreshed") })
            Text(selected?.title ?: "No selection")
        }
        shown("Original")
        compose.runOnIdle { revision++ }
        shown("Refreshed")
        compose.runOnIdle { row = null }
        shown("No selection")
    }
}
