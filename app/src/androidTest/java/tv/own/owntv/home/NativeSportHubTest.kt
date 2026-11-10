package tv.own.owntv.home

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.Test
import java.io.File
import java.util.Locale
import tv.own.owntv.features.home.*
import tv.own.owntv.features.sporthub.*
import tv.own.owntv.features.shell.*
import java.time.Instant

/** Production native hub presenter; explicitly synthetic data, no network or owner profiles. */
class NativeSportHubTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true" && shell("getprop ro.kernel.qemu").trim() == "1")
                val small = description.methodName.contains("720")
                prepareDisplay(small)
                try { base.evaluate() } finally { shell("wm size reset") }
            }
        }
    }).around(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var entry: (() -> Boolean)? = null
    private var profile by mutableStateOf(1L)
    private var path by mutableStateOf("root")
    private var present by mutableStateOf(true)
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Before fun disposableOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("disposableEmulator") == "true" && shell("getprop ro.kernel.qemu").trim() == "1")
        instrumentation.setInTouchMode(false)
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun press(code: Int) { instrumentation.sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun prepareDisplay(small: Boolean) {
        shell(if (small) "wm size 1280x720" else "wm size 1920x1080")
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        do {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val ready = bitmap.width == (if (small) 1280 else 1920) && bitmap.height == (if (small) 720 else 1080)
            bitmap.recycle()
            if (ready) break
            check(android.os.SystemClock.uptimeMillis() < deadline)
            Thread.sleep(50)
        } while (true)
    }
    private fun mount(small: Boolean = false) {
        compose.setContent {
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("sv")) }
            val swedish = compose.activity.createConfigurationContext(config)
            CompositionLocalProvider(LocalDensity provides Density(if (small) 1.2f else 1.8f, if (small) 1.4f else 1f),
                LocalContext provides swedish, LocalConfiguration provides config) {
                MaterialTheme {
                    val header = remember { FocusRequester() }
                    Column(Modifier.fillMaxSize().background(MinTvNavy)) {
                        NativeTopNavigation(TopDestination.SPORT, header, {}, { entry?.invoke() }, {})
                        key(profile) {
                            val holder = rememberSaveableStateHolder()
                            val first = remember { FocusRequester() }
                            holder.SaveableStateProvider(path) {
                                NativeSportHubPage(if (path == "root") "Ligor & matcher · syntetiskt navigationsprov" else "Matchdetaljer · syntetiskt prov",
                                    "Ingen livedata. Profil $profile. Fokus och rullning testas i den riktiga Compose-komponenten.",
                                    if (path == "root" && present) listOf(HubSection("fixtures", "Tillgängliga matcher", (0..29).map { i ->
                                        HubTile("hub-fixture-$i", "Testlag $i – Motståndare", "Ofullständig källa · ingen verifierad TV-kanal", "open/$i")
                                    })) else listOf(HubSection("empty", "", listOf(HubTile("empty", "Inga matchuppgifter tillgängliga")))),
                                    profile, first, { entry = it }, { header.requestFocus() }, { action ->
                                        if (action.startsWith("open/")) path = "detail" else if (action == "hub-back") path = "root"
                                    }, Modifier.weight(1f))
                            }
                            LaunchedEffect(path) { repeat(2) { withFrameNanos { } }; entry?.invoke() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun exercise(small: Boolean) {
        run {
            mount(small)
            compose.onNodeWithTag("hub-back").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("hub-fixture-0").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_RIGHT);press(KeyEvent.KEYCODE_DPAD_RIGHT)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("hub-fixture-5").assertIsFocused()
            repeat(4) { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN) }
            compose.waitForIdle()
            compose.onNodeWithTag("hub-fixture-17").assertIsFocused().assertIsDisplayed()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.onNodeWithTag("hub-back").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.onNodeWithTag("hub-fixture-17").assertIsFocused().assertIsDisplayed()
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "sport-hub-screenshots").apply { mkdirs() }
            File(output, if (small) "HUB-SYNTHETIC-1280x720.png" else "HUB-SYNTHETIC-1920x1080.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            compose.runOnIdle { present = false }
            compose.onNodeWithTag("hub-back").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("top-SPORT").assertIsFocused()
        }
    }
    @Test fun hubRemoteRowsBackRestorationAndUnavailableAt1080() = exercise(false)
    @Test fun hubRemoteRowsBackRestorationAndUnavailableAt720() = exercise(true)
    @Test fun profileSwitchAndEmptyArrivalNeverRecallAnotherProfilesFocus() {
        run {
            mount()
            press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("hub-fixture-1").assertIsFocused()
            compose.runOnIdle { profile = 2; present = false }
            compose.onNodeWithTag("hub-fixture-1").assertDoesNotExist()
            compose.onNodeWithTag("hub-back").assertIsFocused()
            compose.runOnIdle { present = true }
            compose.onNodeWithTag("hub-back").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("hub-fixture-0").assertIsFocused()
        }
    }
    @Test fun mainSportShowsFollowedPublicLeagueAndRevocationRemovesOnlyItsMatches() {
        run {
            val at=Instant.parse("2026-11-09T12:00:00Z")
            val a=HubTeam("hockey:events24:"+"0".repeat(32),"Synthetic North A")
            val b=HubTeam("hockey:events24:"+"1".repeat(32),"Synthetic North B")
            val game=HubMatch("synthetic-nhl","us.nhl","2026-27",a,b,at.plusSeconds(3600),HubMatchStatus.SCHEDULED,null)
            val follow=HubFollow(a.id,a.name,game.league,HubSport.ICE_HOCKEY)
            var prefs by mutableStateOf(SportPreferences(listOf(SportsCatalog.farjestadId),hubTeams=listOf(follow),hubProviders=setOf(HubProvider.EVENTS24)))
            val hero=SportsCatalog.fixture(SportsCatalog.shl,ShlGame("hero","Färjestad BK","Skellefteå AIK",at.plusSeconds(10800).toEpochMilli(),null))
            val other=hero.copy(id="later",kickoff=at.plusSeconds(7200))
            var opened:String?=null
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1.8f)) { MaterialTheme {
                    val first=remember { FocusRequester() }
                    NativeSportDashboard(NativeSportSnapshot(1,at.toEpochMilli(),prefs,hero,listOf(hero,other),emptyList(),emptyList()),
                        NativeSportActions({}, {}, {}, {}, {}),first,{entry=it},{},onExplore={},
                        hubReads=mapOf(game.league to HubRead(HubSnapshot(game.league,game.season,HubProvider.EVENTS24,listOf(game),at))),
                        onHubTeam={},onHubMatch={opened=it.id})
                    LaunchedEffect(Unit) { repeat(2) { withFrameNanos { } };entry?.invoke() }
                } }
            }
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("sport-mine-hub:synthetic-nhl").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(game.id,opened)
            compose.runOnIdle { prefs=prefs.copy(hubProviders=emptySet()) }
            compose.onNodeWithTag("sport-mine-hub:synthetic-nhl").assertDoesNotExist()
            compose.onNodeWithTag("sport-hero-action").assertIsFocused()
            assertEquals(listOf(SportsCatalog.farjestadId),prefs.teamIds)
        }
    }
    @Test fun publicOnlyProfileUsesContextualHeroAndCold720EntryRemainsLocal() {
        run {
            val at=Instant.parse("2026-11-09T12:00:00Z")
            val a=HubTeam("hockey:events24:"+"2".repeat(32),"Synthetic North A")
            val b=HubTeam("hockey:events24:"+"3".repeat(32),"Synthetic North B")
            val game=HubMatch("synthetic-nhl-hero","us.nhl","2026-27",a,b,at.plusSeconds(3600),HubMatchStatus.SCHEDULED,null)
            val prefs=SportPreferences(hubTeams=listOf(HubFollow(a.id,a.name,game.league,HubSport.ICE_HOCKEY)),hubProviders=setOf(HubProvider.EVENTS24))
            var reads by mutableStateOf(mapOf(game.league to HubRead(HubSnapshot(game.league,game.season,HubProvider.EVENTS24,listOf(game),at))))
            var opened:String?=null;var mine=0
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1.2f,1.4f)) { MaterialTheme {
                    val first=remember { FocusRequester() }
                    NativeSportDashboard(NativeSportSnapshot(1,at.toEpochMilli(),prefs,null,emptyList(),emptyList(),emptyList()),
                        NativeSportActions({}, {}, {}, {}, {}),first,{entry=it},{},onExplore={},hubReads=reads,onHubTeam={},
                        onHubMatch={opened=it.id},onHubMine={mine++})
                    LaunchedEffect(Unit) { repeat(2) { withFrameNanos { } };entry?.invoke() }
                } }
            }
            compose.onNodeWithTag("sport-public-hero").assertIsDisplayed()
            compose.onNodeWithTag("sport-hero-action").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(game.id,opened)
            compose.runOnIdle { reads=emptyMap() }
            compose.onNodeWithTag("sport-public-hero").assertIsDisplayed()
            compose.onNodeWithTag("sport-hero-action").assertIsFocused()
            press(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(1,mine)
            compose.onNodeWithTag("sport-mine-hub:synthetic-nhl-hero").assertDoesNotExist()
        }
    }
}
