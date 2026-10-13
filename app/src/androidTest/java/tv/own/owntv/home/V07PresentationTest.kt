package tv.own.owntv.home

import android.content.ComponentName
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.home.*

/** Production presentation with wholly synthetic records, no owner accounts, artwork HTTP or playback. */
class V07PresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun screenshot(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "v04-screenshots").apply { mkdirs() }
        File(dir, name).outputStream().use { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }
    @Test fun featuredMoviesAndSeriesExposePlayAndInformationWithRemoteWithoutAutoplay() {
        var plays = 0; var information = 0
        compose.setContent { MaterialTheme {
            val first = remember { FocusRequester() }
            val card = DiscoveryCard(77, 101, "101:synthetic", MetadataType.MOVIE, "Syntetisk svensk film", null, 2026, 1, 8.2, setOf("Drama"), 11)
            LazyColumn(Modifier.fillMaxSize().background(MinTvNavy).padding(24.dp)) {
                item { DiscoveryFeaturedHero(DiscoveryHero(card, overview = "En syntetisk berättelse för granskning av den riktiga TV-layouten."),
                    compose.activity.getString(R.string.mintv_featured_play), { plays++ }, { information++ }, primaryModifier = Modifier.focusRequester(first)) }
                item { DiscoveryFeaturedHero(DiscoveryHero(card.copy(type = MetadataType.TV, name = "Syntetisk svensk serie")),
                    compose.activity.getString(R.string.mintv_featured_episodes), { information++ }, { information++ }) }
            }
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
        } }
        compose.onAllNodesWithTag("discovery-featured-primary").onFirst().assertIsFocused()
        assertEquals(0, plays); screenshot("v07-featured-movies-and-series-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, plays)
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, information)
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_featured_episodes)).performScrollTo().assertIsDisplayed()
        screenshot("v07-featured-series-synthetic.png")
    }
    @Test fun liveTwitchShowsCategoryLaunchesChosenPlayerWithoutAuthorization() {
        val prefs = compose.activity.getSharedPreferences("v07-twitch-" + java.util.UUID.randomUUID(), 0)
        var launches = 0; var authorizations = 0
        val player = TwitchPlayer(ComponentName("synthetic.player", "SyntheticPlayer"), "Synthetic TV player", true)
        compose.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().background(MinTvNavy).padding(24.dp)) {
                TwitchCompanionUi(true, true, prefs, "synthetic-public", { true }, { TwitchState.Live("Synthetic live stream", 12000, "Counter-Strike 2") },
                    { _, _ -> authorizations++ }, {}, listPlayers = { listOf(player) }, openPlayer = { assertEquals(player, it); launches++; true })
            }
        } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("twitch-watch").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Counter-Strike 2").assertIsDisplayed(); assertEquals(0, launches)
        screenshot("v07-twitch-live-synthetic.png")
        compose.onNodeWithTag("twitch-watch").performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        compose.onNodeWithTag("twitch-watch").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        val playerLabel = compose.activity.getString(R.string.mintv_twitch_open_direct, player.name)
        // Player discovery runs on IO; wait for the actual dialog window and remote focus.
        try {
            compose.waitUntil(5000) { runCatching {
                compose.onNodeWithText(playerLabel).assertIsDisplayed().assertIsFocused(); true
            }.getOrDefault(false) }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            screenshot("v07-twitch-picker-failure-synthetic.png")
            compose.onAllNodes(isRoot()).printToLog("V07_SYNTHETIC_TWITCH_PICKER")
            throw error
        }
        compose.onNodeWithText(playerLabel).assertIsDisplayed().assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, launches); assertEquals(0, authorizations)
    }
    @Test fun missingTwitchPlayerShowsGracefulFallbackAndRemoteClose() {
        var closed = false
        compose.setContent { MaterialTheme { TwitchPlayerPicker(emptyList(), false, { error("Missing player must not launch") }, { closed = true }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_twitch_no_player)).assertIsDisplayed()
        screenshot("v07-twitch-no-player-synthetic.png")
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_close)).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertTrue(closed)
    }
    @Test fun hockeySummaryShowsSpecificEpgChannelAndRemoteWatchWithoutExternalRequests() {
        val now = java.time.Instant.parse("2026-10-13T15:00:00Z").toEpochMilli()
        val epg = tv.own.owntv.core.database.entity.EpgProgrammeEntity(sourceId = 101, epgChannelId = "synthetic",
            startMs = now + 60_000, stopMs = now + 3 * 60 * 60_000, title = "SHL LIVE: FBK – LHF")
        val parser = HockeyEpgParser(SportPreferences(listOf(SportsCatalog.farjestadId)), now)
        val game = parser.parse(epg)!!.fixture
        var watched = 0; var browsing = 0
        compose.setContent { MaterialTheme {
            val first = remember { FocusRequester() }
            Row(Modifier.fillMaxWidth().background(MinTvNavy).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeSportsCard(true, { watched++ }, Modifier.weight(1f).focusRequester(first), watch = true) {
                    HockeyHomeSummary(game, now, "Synthetic TV4 Sport Live 2 FHD", HockeyChannelEvidence.SPECIFIC_EPG, true, false, false)
                }
                HomeButton(compose.activity.getString(R.string.mintv_hockey_other_channels), { browsing++ })
            }
            LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; first.requestSafely() }
        } }
        compose.onNodeWithText("Synthetic TV4 Sport Live 2 FHD").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.mintv_hockey_epg_evidence)).assertIsDisplayed()
        assertEquals(0, watched); screenshot("v07-min-hockey-epg-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, watched)
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1, browsing)
    }
    @Test fun featuredContinuationChecksStableSourceAndSeriesIdentityInsteadOfTitleGuessing() {
        val card = DiscoveryCard(71, 101, "101:hero", MetadataType.TV, "Synthetic series", null, 2026, 1, null, emptySet(), 11)
        val link = tv.own.owntv.core.launcher.LauncherDeepLink.Episode(seriesSourceId = 101, seriesRemoteId = "hero", seriesItemId = 71, episodeItemId = 90)
        val item = tv.own.owntv.core.launcher.LauncherContinuationItem(tv.own.owntv.core.launcher.LauncherContinuationKind.EPISODE,
            90, 90, "synthetic-episode", "Synthetic episode", playbackUri = link.toUri(), lastEngagementAt = 1, durationMs = 1000,
            positionMs = 400, watchNextType = tv.own.owntv.core.launcher.LauncherWatchNextType.CONTINUE)
        assertEquals(item, DiscoveryHeroSelection.continuation(card, listOf(item)))
        assertNull(DiscoveryHeroSelection.continuation(card.copy(sourceId = 102), listOf(item)))
        assertNull(DiscoveryHeroSelection.continuation(card.copy(stableKey = "101:another"), listOf(item)))
        assertNull(DiscoveryHeroSelection.continuation(card.copy(id = 72), listOf(item)))
    }
    @Test fun offlineTwitchOffersSettingsWithoutStreamActionOrAuthorization() {
        val prefs = compose.activity.getSharedPreferences("v07-twitch-offline-" + java.util.UUID.randomUUID(), 0)
        var authorized = false
        compose.setContent { MaterialTheme { TwitchCompanionUi(true, true, prefs, "synthetic-public", { true }, { TwitchState.Offline },
            { _, _ -> authorized = true }, {}) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText(compose.activity.getString(R.string.mintv_twitch_offline)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("twitch-watch").assertDoesNotExist(); assertFalse(authorized)
        screenshot("v07-twitch-offline-synthetic.png")
    }
}
