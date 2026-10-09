package tv.own.owntv.home

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Always package-scoped. No implicit YouTube/browser playback fallback. */
object MinTvExternalApps {
    val smartTubePackages = listOf(
        "org.smarttube.stable", "org.smarttube.beta", "app.smarttube.fdroid",
        "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta",
    )
    const val SVT_PLAY = "se.svt.android.svtplay"

    fun launch(context: Context, packages: List<String>): Intent? = packages.firstNotNullOfOrNull { pkg ->
        context.packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: context.packageManager.getLaunchIntentForPackage(pkg)
    }

    /** SmartTube's exported SplashActivity accepts HTTPS youtube.com/watch links. */
    fun video(context: Context, videoId: String): Intent? {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
        return smartTubePackages.firstNotNullOfOrNull { pkg ->
            val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$videoId".toUri())
                .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.takeIf { context.packageManager.resolveActivity(it, 0)?.activityInfo?.exported == true }
        }
    }

    fun store(context: Context, pkg: String): Intent? {
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
            .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}
