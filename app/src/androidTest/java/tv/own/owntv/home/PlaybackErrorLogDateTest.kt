package tv.own.owntv.home

import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.own.owntv.features.more.ErrorLogPage
import tv.own.owntv.player.PlaybackErrorLog

/** Exercise the actual playback-log row with an isolated synthetic log, never the app's log. */
class PlaybackErrorLogDateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun disposableEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
    }

    @Test fun swedish12HourPlaybackTimestamp() = checkScreen("sv-SE", false)
    @Test fun swedish24HourPlaybackTimestamp() = checkScreen("sv-SE", true)
    @Test fun english12HourPlaybackTimestamp() = checkScreen("en-US", false)
    @Test fun english24HourPlaybackTimestamp() = checkScreen("en-US", true)

    private fun checkScreen(language: String, is24Hour: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val previousCycle = Settings.System.getString(app.contentResolver, Settings.System.TIME_12_24)
        val directory = File(app.cacheDir, "playback-date-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        fun clock(value: String?) {
            val command = if (value == null) "settings delete system time_12_24"
                else "settings put system time_12_24 $value"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        try {
            clock(if (is24Hour) "24" else "12")
            val locale = Locale.forLanguageTag(language)
            val configuration = Configuration(app.resources.configuration).apply { setLocale(locale) }
            val context = object : ContextWrapper(app.createConfigurationContext(configuration)) {
                override fun getFilesDir(): File = directory
            }
            assertEquals(is24Hour, DateFormat.is24HourFormat(context))
            val at = 1_704_114_420_000L
            File(directory, "playback_errors.json").writeText(
                """[{"atMs":$at,"engine":"mpv","live":true,"reason":"Synthetic playback failure","model":"Emulator","android":"14"}]""",
            )
            assertEquals(1, PlaybackErrorLog.read(context).size)
            // Independent platform reference: a date-only skeleton plus exactly one hour cycle.
            val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "dMMMHm" else "dMMMhm")
            val expected = SimpleDateFormat(pattern, locale).format(Date(at))
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                    MaterialTheme { ErrorLogPage(setEntry = {}, onCountChanged = {}) }
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Synthetic playback failure").assertIsDisplayed()
            compose.onNodeWithText(expected).assertIsDisplayed()
        } finally {
            clock(previousCycle)
            directory.deleteRecursively()
        }
    }
}
