package tv.own.owntv.home

import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.MaterialTheme
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.settings.BackupScreen

/** Real backup screen, synthetic last-backup metadata only. Never exports or imports user data. */
class BackupScreenDateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun disposableEmulatorOnly() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
    }

    @Test fun swedish12HourWithAndWithoutBackup() = checkScreen("sv-SE", false)
    @Test fun swedish24HourWithAndWithoutBackup() = checkScreen("sv-SE", true)
    @Test fun english12HourWithAndWithoutBackup() = checkScreen("en-US", false)
    @Test fun english24HourWithAndWithoutBackup() = checkScreen("en-US", true)

    private fun checkScreen(language: String, is24Hour: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val settings = GlobalContext.get().get<SettingsRepository>()
        val previousBackup = runBlocking { settings.lastBackup.first() }
        val previousCycle = Settings.System.getString(app.contentResolver, Settings.System.TIME_12_24)
        fun clock(value: String?) {
            val command = if (value == null) "settings delete system time_12_24"
                else "settings put system time_12_24 $value"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        try {
            clock(if (is24Hour) "24" else "12")
            val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = app.createConfigurationContext(configuration)
            assertEquals(is24Hour, DateFormat.is24HourFormat(context))
            runBlocking { settings.recordBackup(0, 0, false, "") }
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                    MaterialTheme { BackupScreen(modifier = Modifier.fillMaxSize()) }
                }
            }
            val never = context.getString(R.string.more_backup_never_title)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(never).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(context.getString(R.string.settings_backup_title)).assertIsDisplayed()
            compose.onNodeWithText(never).assertIsDisplayed()

            // This transition used to throw in Android ICU after the first successful export.
            runBlocking { settings.recordBackup(1_704_110_400_000L, 4096, true, "synthetic-backup.own") }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("synthetic-backup.own", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("synthetic-backup.own", substring = true).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.more_pane_encrypted), substring = true).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.settings_backup_title)).assertIsDisplayed()
        } finally {
            clock(previousCycle)
            runBlocking {
                settings.recordBackup(previousBackup?.at ?: 0, previousBackup?.bytes ?: 0,
                    previousBackup?.encrypted ?: false, previousBackup?.path.orEmpty())
            }
        }
    }
}
