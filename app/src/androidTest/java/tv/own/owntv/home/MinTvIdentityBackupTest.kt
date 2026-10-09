package tv.own.owntv.home

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.backup.BackupContainer
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.OwnTVDatabase

/** Device-only checks. Never imports into, opens, or clears the other app's database. */
@RunWith(AndroidJUnit4::class)
class MinTvIdentityBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun providersAndPermissionsBelongToThisInstallation() {
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS,
        )
        assertEquals(BuildConfig.APPLICATION_ID, info.packageName)
        assertTrue(info.providers!!.all { it.authority.startsWith(context.packageName + ".") })
        assertTrue(info.permissions!!.all { it.name.startsWith(context.packageName + ".") })
        assertFalse(info.sharedUserId != null)
    }

    @Test fun privateDatabaseAndFilesUseThisPackagesSandbox() {
        val dataDir = File(context.applicationInfo.dataDir).canonicalFile
        assertEquals(context.packageName, dataDir.name)
        assertTrue(context.filesDir.canonicalPath.startsWith(dataDir.path + File.separator))
        assertTrue(context.getDatabasePath(OwnTVDatabase.NAME).canonicalPath.startsWith(dataDir.path + File.separator))
    }

    @Test fun existingEncryptedBackupFormatCanBeInspectedAndPreviewedWithoutImporting() = runBlocking {
        // Same package-independent .own format exported by v0.1; empty synthetic content, no secrets.
        val payload = BackupContainer.Payload("""{"version":25,"profiles":[],"sources":[],"userData":[]}""", null)
        val file = File.createTempFile("mintv-backup-test-", ".own", context.cacheDir)
        try {
            file.writeBytes(BackupContainer.pack(payload, "test-only-passphrase"))
            val backup = GlobalContext.get().get<BackupManager>()
            val inspection = backup.sectionsIn(file, "test-only-passphrase").getOrThrow()
            assertTrue(inspection.sealed)
            assertTrue(BackupManager.Section.SOURCES in inspection.sections)
            assertTrue(backup.previewImport(file, backupPassword = "test-only-passphrase").getOrThrow().isEmpty)
        } finally {
            file.delete()
        }
    }
}
