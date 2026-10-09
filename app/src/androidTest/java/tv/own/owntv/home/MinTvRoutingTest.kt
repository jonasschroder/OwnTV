package tv.own.owntv.home

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.launcher.LauncherDeepLink

/** Requires Android: JVM android.jar stubs cannot validate Uri or PackageManager routing. */
@RunWith(AndroidJUnit4::class)
class MinTvRoutingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun homeIsAStableExportedComponentSeparateFromIptv() {
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        assertEquals(1, matches.size)
        val home = matches.single().activityInfo
        assertEquals(MinTvHomeActivity::class.java.name, home.name)
        assertTrue(home.exported)
        assertEquals(context.packageName + ".home", home.taskAffinity)
        assertTrue(home.name != AppIconSwitcher.launchComponent(context).className)
    }

    @Test fun liveEntryTargetsThisAppAndKeepsCoreOnboardingRouting() {
        val intent = MinTvIntents.liveTv(context)
        assertEquals("se.jonasschroder.mintv", intent.component!!.packageName)
        assertEquals(AppIconSwitcher.launchComponent(context), intent.component)
        assertEquals(LauncherDeepLink.OpenLiveSection, MinTvIntents.parseDeepLink(intent.data))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(Settings.ACTION_SETTINGS, MinTvIntents.settings().action)
    }

    @Test fun externalSchemePreservesPlaybackParametersWithoutClaimingUpstreamLinks() {
        assertEquals(
            LauncherDeepLink.Live(sourceId = 7, itemId = 42),
            MinTvIntents.parseDeepLink(Uri.parse("mintv://play/live?sourceId=7&itemId=42")),
        )
        assertNull(MinTvIntents.parseDeepLink(Uri.parse("https://example.com/open/live")))
    }
}
