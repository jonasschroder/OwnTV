package tv.own.owntv.features.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

internal fun twitchDirectPlayerEligible(enabled: Boolean, exported: Boolean, targetSdk: Int,
    hasTvEntry: Boolean, authorities: Set<String>): Boolean = enabled && exported && targetSdk >= 23 && hasTvEntry &&
        authorities.any { it == "www.twitch.tv" || it == "twitch.tv" }

internal data class TwitchPlayer(val component: ComponentName, val name: String, val direct: Boolean)

/** Public channel URL only. No account tokens, stream extraction, browser or guessed private extras. */
internal class TwitchPlayback(private val context: Context) {
    private val pm get() = context.packageManager
    private fun channel() = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.twitch.tv/ohnepixel"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    private fun tvActivity(packageName: String) = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName)
    private fun compatible(packageName: String): Boolean =
        pm.getApplicationInfo(packageName, 0).let { it.enabled && it.targetSdkVersion >= 23 } &&
            pm.queryIntentActivities(tvActivity(packageName), PackageManager.GET_RESOLVED_FILTER)
                .any { it.activityInfo.enabled && it.activityInfo.exported }

    fun players(): List<TwitchPlayer> {
        val direct = pm.queryIntentActivities(channel(), PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
            .filter { resolved ->
                val info = resolved.activityInfo
                val filter = resolved.filter
                filter != null && runCatching {
                    twitchDirectPlayerEligible(info.enabled, info.exported, info.applicationInfo.targetSdkVersion,
                        compatible(info.packageName), (0 until filter.countDataAuthorities()).map { filter.getDataAuthority(it).host }.toSet())
                }.getOrDefault(false)
            }.take(8).map { TwitchPlayer(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString().take(80), true) }
        // The current public beta targets SDK 37. Its custom stream parameters are undocumented:
        // discover its exported public filter, but use only its normal TV launcher as a manual fallback.
        val manual = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("s0undtv://stream")), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == "com.s0und.s0undtv" && runCatching { compatible(it.activityInfo.packageName) }.getOrDefault(false) }
            .mapNotNull { pm.getLeanbackLaunchIntentForPackage(it.activityInfo.packageName)?.component }
            .distinct().take(1).map { TwitchPlayer(it, pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString().take(80), false) }
        return (direct + manual).distinctBy { it.component.packageName }.take(8)
    }

    fun open(player: TwitchPlayer): Boolean = runCatching {
        // Re-resolve after the picker: uninstall, disable and SDK changes must fail safely.
        val actual = players().firstOrNull { it.component == player.component && it.direct == player.direct } ?: return false
        val intent = if (actual.direct) channel().setComponent(actual.component)
            else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(actual.component)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
