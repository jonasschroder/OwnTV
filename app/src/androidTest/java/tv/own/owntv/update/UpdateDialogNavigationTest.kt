package tv.own.owntv.update

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.features.update.UpdateDialog

class UpdateDialogNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun disposableOnly() { assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator")) }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun mount(): MutableState<Boolean> {
        val open = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (open.value) UpdateDialog(onDismiss = { open.value = false }) } }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A platform dialog attaches asynchronously; require actual focus without forcing it in test.
        compose.waitUntil(5_000) {
            compose.onNodeWithText(context.getString(R.string.settings_close)).fetchSemanticsNode()
                .config.getOrElse(SemanticsProperties.Focused) { false }
        }
        return open
    }
    @Test fun dpadOkClosesAboutWithoutStartingAnInstallation() {
        val open = mount()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.mintv_update_about)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_close)).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText(context.getString(R.string.settings_check_updates)).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_LEFT); key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(open.value)
    }
    @Test fun backReturnsFromAbout() {
        val open = mount(); key(KeyEvent.KEYCODE_BACK); assertFalse(open.value)
    }
}
