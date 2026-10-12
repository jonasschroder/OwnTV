package tv.own.owntv.home

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.home.*

/** Production rows/navigation; synthetic available records, no decoder/provider/account access. */
class DiscoveryPresentationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Before fun remoteMode(){InstrumentationRegistry.getInstrumentation().setInTouchMode(false)}
    private fun key(code:Int){InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code);compose.waitForIdle()}
    private val cards=listOf(DiscoveryCard(77,101,"101:synthetic-a",MetadataType.MOVIE,"Synthetic available film",null,2025,100,8.2,setOf("Drama"),11),
        DiscoveryCard(78,101,"101:synthetic-b",MetadataType.MOVIE,"Synthetic second film",null,2024,200,7.8,setOf("Drama"),22))
    private fun screenshot(name:String){
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(app.getExternalFilesDir(null),"v04-screenshots").apply{mkdirs()}
        File(dir,name).outputStream().use {assertTrue(compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun providerRowsKeepStableIdentityAndOkDoesNotAutoplayOnFocus(){
        var chosen:DiscoveryCard?=null
        compose.setContent {MaterialTheme {
            val first=remember{FocusRequester()}
            LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                item {DiscoveryPosterRow(DiscoveryRow(DiscoveryKind.TOP,cards),MetadataType.MOVIE,System.currentTimeMillis(),{chosen=it},first=Modifier.focusRequester(first).testTag("discovery-first"))}
                item {DiscoveryPosterRow(DiscoveryRow(DiscoveryKind.RECENT,cards.reversed()),MetadataType.MOVIE,null,{chosen=it})}
            }
            LaunchedEffect(Unit){withFrameNanos{};withFrameNanos{};first.requestSafely()}
        }}
        compose.onNodeWithTag("discovery-first").assertIsFocused();assertNull(chosen)
        compose.onAllNodesWithText("Synthetic available film").onFirst().assertIsDisplayed()
        screenshot("v05-discovery-production-rows-synthetic.png")
        key(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals("101:synthetic-a",chosen!!.stableKey)
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(78L,chosen!!.id)
    }
    @Test fun discoveryArrivalKeepsFocusedAppsAndRemoteCanReachInsertedRows(){
        var insert:(()->Unit)?=null;var chosen:DiscoveryCard?=null
        compose.setContent {MaterialTheme {
            var arrival by remember{mutableStateOf(false)}
            insert={arrival=true}
            val navigation=rememberHomeDpadNavigation(remember{FocusRequester()},8,101)
            val list=rememberLazyListState()
            val indices=homeSectionIndices(listOf(true,true,false,true,arrival,false,true,true))
            LazyColumn(state=list,modifier=Modifier.fillMaxSize().then(homeDpadModifier(navigation,list,indices))) {
                item(key="hero"){HomeButton("Synthetic live",{},navigation.section(0))}
                item(key="favorite"){HomeButton("Synthetic favorite",{},navigation.section(1))}
                item(key="sport"){HomeSportsCard(true,{},navigation.section(3))}
                if(arrival)item(key="discovery"){DiscoveryPosterRow(DiscoveryRow(DiscoveryKind.TOP,cards),MetadataType.MOVIE,null,{chosen=it},first=navigation.section(4).testTag("inserted-row"))}
                item(key="twitch"){HomeButton("Synthetic Twitch",{},navigation.section(6))}
                item(key="apps"){HomeButton("Synthetic apps",{},navigation.section(7).testTag("stable-apps"))}
            }
            LaunchedEffect(Unit){list.scrollToItem(indices[7]);withFrameNanos{};withFrameNanos{};navigation.focus[7].requestSafely()}
        }}
        compose.onNodeWithTag("stable-apps").assertIsFocused()
        compose.runOnIdle{insert!!.invoke()}
        compose.onNodeWithTag("stable-apps").assertIsFocused();assertNull(chosen)
        key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("inserted-row").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER);assertEquals(77L,chosen!!.id)
    }
}
