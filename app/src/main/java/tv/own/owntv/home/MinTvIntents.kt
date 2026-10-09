package tv.own.owntv.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import tv.own.owntv.BuildConfig
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.launcher.LauncherDeepLink

/** Keep external Min TV links separate; reuse Core's parser only inside our process. */
object MinTvIntents {
    fun parseDeepLink(uri: Uri?): LauncherDeepLink? = when (uri?.scheme) {
        BuildConfig.APP_LINK_SCHEME -> LauncherDeepLink.parse(uri.buildUpon().scheme("owntv").build())
        // Package-scoped notifications/internal Core links still use this protocol.
        "owntv" -> LauncherDeepLink.parse(uri)
        else -> null
    }

    fun liveTv(context: Context): Intent = Intent(Intent.ACTION_VIEW, Uri.Builder()
        .scheme(BuildConfig.APP_LINK_SCHEME).authority("open").appendPath("live").build())
        // MainActivity may be disabled by the existing icon picker; resolve its enabled variant.
        .setComponent(AppIconSwitcher.launchComponent(context))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun settings(): Intent = Intent(Settings.ACTION_SETTINGS)
}
