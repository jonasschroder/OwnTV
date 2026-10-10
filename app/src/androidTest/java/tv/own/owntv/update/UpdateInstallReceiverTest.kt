package tv.own.owntv.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import tv.own.owntv.features.update.UpdateInstallReceiver

/** Synthetic private callback state ONLY in the newly populated disposable CI emulator. */
class UpdateInstallReceiverTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("mintv-verified-updates", Context.MODE_PRIVATE)
    @Before fun prepare() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/getprop", "ro.kernel.qemu"))
        assertEquals("1", process.inputStream.bufferedReader().readText().trim()); assertEquals(0, process.waitFor())
        assertFalse(prefs.contains("session")); assertFalse(prefs.contains("confirmation"))
        assertTrue(prefs.edit().putInt("session", 12345).commit())
    }
    @After fun cleanupSyntheticKeys() { prefs.edit().remove("session").remove("confirmation").remove("result").commit() }
    private fun callback(status: Int) = Intent(context.packageName + ".UPDATE_STATUS")
        .putExtra(PackageInstaller.EXTRA_SESSION_ID, 12345).putExtra(PackageInstaller.EXTRA_STATUS, status)
    @Test fun wrongPackageActionAndWrongSessionAreIgnored() {
        val receiver = UpdateInstallReceiver()
        receiver.onReceive(context, callback(PackageInstaller.STATUS_FAILURE).setAction("se.jonasschroder.other.UPDATE_STATUS"))
        receiver.onReceive(context, callback(PackageInstaller.STATUS_FAILURE).putExtra(PackageInstaller.EXTRA_SESSION_ID, 999))
        assertFalse(prefs.contains("result")); assertEquals(12345, prefs.getInt("session", -1))
    }
    @Test fun confirmationIsPersistedAndCanBeReadByAnotherInstance() {
        val confirmation = Intent("android.content.pm.action.CONFIRM_INSTALL").putExtra(PackageInstaller.EXTRA_SESSION_ID, 12345)
        UpdateInstallReceiver().onReceive(context, callback(PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirmation))
        val reopened = context.getSharedPreferences("mintv-verified-updates", Context.MODE_PRIVATE)
        val restored = Intent.parseUri(reopened.getString("confirmation", null), Intent.URI_INTENT_SCHEME)
        assertEquals(confirmation.action, restored.action)
        assertEquals(12345, restored.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1))
        assertFalse(prefs.contains("result"))
    }
    @Test fun cancellationIsDurableAndNeverResetsTheInstalledData() {
        val marker = java.io.File(context.filesDir, "same-signer-synthetic-marker").readText()
        UpdateInstallReceiver().onReceive(context, callback(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, prefs.getInt("result", -1))
        assertFalse(prefs.contains("session")); assertFalse(prefs.contains("confirmation"))
        assertEquals(marker, java.io.File(context.filesDir, "same-signer-synthetic-marker").readText())
    }
}
