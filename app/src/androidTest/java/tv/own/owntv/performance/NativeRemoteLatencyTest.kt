package tv.own.owntv.performance

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.features.home.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** No Compose test rule: withFrameNanos uses the REAL display clock, not auto-advanced test frames.
 * Bounded synthetic cards using the production Sport/VOD navigation owner. Key-injection-to-focus
 * callback latency includes Android dispatch; it does not measure photon/presentation latency.
 */
class NativeRemoteLatencyTest {
    @Test fun visibleRowsUseRealFrameClock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        assertEquals("true", InstrumentationRegistry.getArguments().getString("performanceProbe"))
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu"))
            .bufferedReader().use { assertEquals("1", it.readText().trim()) }
        assertEquals("se.jonasschroder.mintv.qa", app.packageName)
        instrumentation.setInTouchMode(false)
        val focused = AtomicReference<String?>()
        val changed = AtomicReference(CountDownLatch(1))
        val focusAt = AtomicLong()
        val keys = (0..3).map { "synthetic-$it" }
        val samples = mutableListOf<Double>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme {
                    val first = remember { FocusRequester() }
                    val rows = remember { listOf(NativeRow("visible", keys)) }
                    val nav = rememberNativeRowsNavigation(rows, 99000, first, {}, {})
                    LazyColumn(state = nav.list, modifier = Modifier.fillMaxSize().then(nav.modifier).focusGroup(),
                        contentPadding = PaddingValues(8.dp)) {
                        item(key = "visible") {
                            LazyRow(state = nav.rails.getValue("visible")) {
                                items(keys, key = { it }) { key ->
                                    HomeButton(key, {}, nav.item(key).width(128.dp).height(64.dp)
                                        .onFocusChanged { if (it.isFocused && focused.getAndSet(key) != key) {
                                            focusAt.set(SystemClock.elapsedRealtimeNanos())
                                            changed.get().countDown()
                                        } })
                                }
                            }
                        }
                    }
                    LaunchedEffect(Unit) { repeat(3) { withFrameNanos { } }; first.requestFocus() }
                }
            } }
            assertTrue("Initial native focus", changed.get().await(5, TimeUnit.SECONDS))
            assertEquals(keys[0], focused.get())
            repeat(42) { iteration ->
                for ((code, expected) in listOf(KeyEvent.KEYCODE_DPAD_RIGHT to keys[1], KeyEvent.KEYCODE_DPAD_LEFT to keys[0])) {
                    val latch = CountDownLatch(1); changed.set(latch)
                    val sentAt = SystemClock.elapsedRealtimeNanos()
                    instrumentation.sendKeyDownUpSync(code)
                    assertTrue("Native focus arrives", latch.await(5, TimeUnit.SECONDS))
                    assertEquals(expected, focused.get())
                    if (iteration >= 2) samples += (focusAt.get() - sentAt) / 1e6
                }
            }
        }
        val label = InstrumentationRegistry.getArguments().getString("perfLabel", "real-focus")!!
        require(label.matches(Regex("[a-z0-9-]{1,60}")))
        val directory = File(app.getExternalFilesDir(null), "performance").apply { mkdirs() }
        File(directory, "$label.json").writeText(JSONObject().put("label", label)
            .put("measurement", "native_real_clock_injected_key_to_focus_callback_ms")
            .put("fixture", "four-visible-cards-production-native-rows-owner")
            .put("samples_ms", JSONArray(samples)).toString(2))
    }
}
