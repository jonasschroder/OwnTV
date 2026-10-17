package tv.own.owntv.home

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.own.owntv.R
import tv.own.owntv.features.home.*

/** Real Android PackageManager + exported handlers in our TEST APK. No third-party APK or account. */
class TwitchPlaybackIntentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixturePackage get() = instrumentation.context.packageName
    @Before fun disposableOnly() {
        val qemu = shell("getprop ro.kernel.qemu").trim()
        assertEquals("External app fixture tests refuse physical devices", "1", qemu)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("disposableEmulator"))
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun reader(uri: String? = "s0undtv://stream/ohnepixel", pkg: String = fixturePackage) =
        TwitchPlayback(context, listOf(TwitchClient(pkg, R.string.mintv_setup_soundtv, uri)))

    @Test fun officialAppLaunchUsesInstalledTvEntryWithoutUriOrSecretsAndFailsClosedWhenDisabled() {
        val playback = TwitchPlayback(context, officialPackage = fixturePackage)
        val intent = playback.officialAppIntent()!!
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.categories.contains(Intent.CATEGORY_LEANBACK_LAUNCHER))
        assertEquals(fixturePackage, intent.component!!.packageName)
        assertNull(intent.data); assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        assertNull(TwitchPlayback(context, officialPackage = "tv.own.mintv.fixture.absent").officialAppIntent())
        assertTrue(playback.openOfficialApp())
        try {
            instrumentation.waitForIdleSync()
            assertTrue(shell("dumpsys activity activities").contains("TwitchFixtureLauncher"))
        } finally { instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK) }
        val component = intent.component!!
        try {
            assertTrue(shell("pm disable --user 0 ${component.flattenToString()}").contains("new state: disabled"))
            assertNull(playback.officialAppIntent()); assertFalse(playback.openOfficialApp())
        } finally {
            shell("pm default-state --user 0 ${component.flattenToString()}")
        }
    }
    @Test fun installedExportedPublicStreamContractOpensExactChannelWithoutSecrets() {
        val playback = reader()
        val player = playback.players().single { it.component.packageName == fixturePackage }
        assertTrue(player.direct); assertEquals("s0undtv://stream/ohnepixel", player.publicUri)
        assertTrue(player.component.className.endsWith("TwitchFixtureStream"))
        assertFalse(playback.open(player.copy(publicUri = "s0undtv://stream/another-channel")))
        assertTrue(playback.open(player))
        try {
            instrumentation.waitForIdleSync()
            val dump = shell("dumpsys activity activities")
            assertTrue("Public channel URI must reach the real Android activity", dump.contains("dat=s0undtv://stream/ohnepixel"))
            assertTrue(dump.contains("TwitchFixtureStream"))
        } finally { instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK) }
    }
    @Test fun absentPrivateDisabledAndPermissionProtectedHandlersNeverBecomeDirectPlayers() {
        assertTrue(reader(pkg = "tv.own.mintv.fixture.absent").players().none { it.component.packageName == fixturePackage })
        for (path in listOf("fixture-private", "fixture-disabled", "fixture-protected", "unhandled")) {
            val player = reader("s0undtv://stream/$path").players().single { it.component.packageName == fixturePackage }
            assertFalse(path, player.direct); assertNull(player.publicUri)
            assertTrue(player.component.className.endsWith("TwitchFixtureLauncher"))
        }
        val manual = reader(null).players().single { it.component.packageName == fixturePackage }
        assertFalse(manual.direct)
        assertTrue(reader(null).open(manual))
        try {
            instrumentation.waitForIdleSync()
            val dump = shell("dumpsys activity activities")
            assertTrue("Manual fallback must use the actual exported TV launcher", dump.contains("TwitchFixtureLauncher"))
        } finally { instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK) }
        assertFalse(reader().install("tv.own.mintv.fixture.absent"))
    }
}
