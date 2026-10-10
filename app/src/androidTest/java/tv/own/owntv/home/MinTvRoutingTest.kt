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
import tv.own.owntv.BuildConfig

/** Requires Android: JVM android.jar stubs cannot validate Uri or PackageManager routing. */
@RunWith(AndroidJUnit4::class)
class MinTvRoutingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun normalAppNeverRegistersAsAndroidHome() {
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        assertTrue(matches.isEmpty())
        val app = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName),
            0,
        )
        assertEquals(1, app.size)
        assertEquals(AppIconSwitcher.launchComponent(context).className, app.single().activityInfo.name)
        assertTrue(app.single().activityInfo.exported)
    }

    @Test fun liveEntryTargetsThisAppAndKeepsCoreOnboardingRouting() {
        val intent = MinTvIntents.liveTv(context)
        assertEquals(context.packageName, intent.component!!.packageName)
        assertEquals(BuildConfig.APP_LINK_SCHEME, intent.data!!.scheme)
        assertEquals(AppIconSwitcher.launchComponent(context), intent.component)
        assertEquals(LauncherDeepLink.OpenLiveSection, MinTvIntents.parseDeepLink(intent.data))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(Settings.ACTION_SETTINGS, MinTvIntents.settings().action)
    }

    @Test fun externalSchemePreservesPlaybackParametersWithoutClaimingUpstreamLinks() {
        assertEquals(
            LauncherDeepLink.Live(sourceId = 7, itemId = 42),
            MinTvIntents.parseDeepLink(Uri.Builder().scheme(BuildConfig.APP_LINK_SCHEME)
                .authority("play").appendPath("live").appendQueryParameter("sourceId", "7")
                .appendQueryParameter("itemId", "42").build()),
        )
        val otherScheme = if (BuildConfig.APP_LINK_SCHEME == "mintv") "mintv-qa" else "mintv"
        assertNull(MinTvIntents.parseDeepLink(Uri.Builder().scheme(otherScheme).authority("open").appendPath("live").build()))
        assertNull(MinTvIntents.parseDeepLink(Uri.parse("https://example.com/open/live")))
    }

    @Test fun externalPlaybackCannotFallBackToOrdinaryYoutube() {
        assertNull(MinTvExternalApps.video(context, "invalid"))
        MinTvExternalApps.video(context, "dQw4w9WgXcQ")?.let { intent ->
            assertTrue(intent.`package` in MinTvExternalApps.smartTubePackages)
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", intent.data.toString())
        }
        assertEquals("org.smarttube.stable", MinTvExternalApps.smartTubePackages.first())
    }
}
