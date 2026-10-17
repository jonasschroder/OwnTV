package tv.own.owntv.features.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import tv.own.owntv.R
import tv.own.owntv.home.MinTvExternalApps

internal fun twitchDirectPlayerEligible(enabled: Boolean, exported: Boolean, targetSdk: Int,
    hasTvEntry: Boolean, authorities: Set<String>): Boolean = enabled && exported && targetSdk >= 23 && hasTvEntry &&
        authorities.any { it == "www.twitch.tv" || it == "twitch.tv" }

internal data class TwitchPlayer(val component: ComponentName, val name: String, val direct: Boolean,
    val publicUri: String? = null)
internal data class TwitchClient(val packageName: String, val nameRes: Int, val streamUri: String? = null)
internal val supportedTwitchClients = listOf(
    TwitchClient("com.s0und.s0undtv", R.string.mintv_setup_soundtv, "s0undtv://stream/ohnepixel"),
    TwitchClient("com.fgl27.twitch", R.string.mintv_setup_smarttv),
)

/** Only public channel contracts, resolved against the installed exported activity. Never OAuth data. */
internal class TwitchPlayback(private val context: Context, private val clients: List<TwitchClient> = supportedTwitchClients,
    private val officialPackage: String = MinTvExternalApps.TWITCH,
    private val findOfficialApp: (Context, List<String>) -> Intent? = MinTvExternalApps::launch) {
    private val pm get() = context.packageManager
    private fun channel(uri: String = "https://www.twitch.tv/ohnepixel") = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    private fun tvActivity(packageName: String) = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName)
    private fun accessible(info: ResolveInfo): Boolean = info.activityInfo.let {
        it.enabled && it.exported && it.applicationInfo.enabled && it.applicationInfo.targetSdkVersion >= 23 &&
            (it.permission == null || pm.checkPermission(it.permission, context.packageName) == PackageManager.PERMISSION_GRANTED)
    }
    private fun launchers(packageName: String) = pm.queryIntentActivities(tvActivity(packageName), 0).filter(::accessible)

    /** Same app entry as SVT Play; no channel URI, account, OAuth extras or implicit fallback. */
    fun officialAppIntent(): Intent? = runCatching {
        val intent = findOfficialApp(context, listOf(officialPackage)) ?: return null
        val resolved = pm.resolveActivity(intent, 0) ?: return null
        intent.takeIf { resolved.activityInfo.packageName == officialPackage && accessible(resolved) }
    }.getOrNull()

    fun openOfficialApp(): Boolean = runCatching {
        val intent = officialAppIntent() ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun installOfficialApp(): Boolean = openStore(MinTvExternalApps.TWITCH)

    fun players(): List<TwitchPlayer> {
        // A browser with a generic VIEW filter is not a compatible TV client.
        val https = pm.queryIntentActivities(channel(), PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
            .filter { resolved ->
                val filter = resolved.filter
                accessible(resolved) && filter != null && twitchDirectPlayerEligible(true, true,
                    resolved.activityInfo.applicationInfo.targetSdkVersion, launchers(resolved.activityInfo.packageName).isNotEmpty(),
                    (0 until filter.countDataAuthorities()).map { filter.getDataAuthority(it).host }.toSet())
            }.take(8).map { TwitchPlayer(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString().take(80), true) }
        val known = clients.mapNotNull { client ->
            val launcher = launchers(client.packageName).firstOrNull() ?: return@mapNotNull null
            val direct = client.streamUri?.let { uri ->
                pm.queryIntentActivities(channel(uri).setPackage(client.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                    .firstOrNull { it.activityInfo.packageName == client.packageName && accessible(it) }
            }
            val info = (direct ?: launcher).activityInfo
            TwitchPlayer(ComponentName(info.packageName, info.name), context.getString(client.nameRes), direct != null,
                client.streamUri.takeIf { direct != null })
        }
        return (known + https).distinctBy { it.component.packageName }.take(8)
    }

    fun open(player: TwitchPlayer): Boolean = runCatching {
        // Re-resolve: uninstall/disable/permission/filter changes after selection fail closed.
        val actual = players().firstOrNull { it == player } ?: return false
        val intent = if (actual.direct) channel(actual.publicUri ?: "https://www.twitch.tv/ohnepixel").setComponent(actual.component)
            else tvActivity(actual.component.packageName).setComponent(actual.component)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    fun install(packageName: String): Boolean = runCatching {
        if (supportedTwitchClients.none { it.packageName == packageName }) return false
        openStore(packageName)
    }.getOrDefault(false)

    private fun openStore(packageName: String): Boolean = runCatching {
        // User explicitly chooses a Google Play listing. No APK download or silent installation.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage("com.android.vending")
        if (pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).none(::accessible)) return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
