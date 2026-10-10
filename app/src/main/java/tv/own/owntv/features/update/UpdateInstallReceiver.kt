package tv.own.owntv.features.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Private PendingIntent target survives process death. Never launches an activity in background. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != context.packageName + ".UPDATE_STATUS") return
        val prefs = context.getSharedPreferences(MinTvUpdater.STORE, Context.MODE_PRIVATE)
        val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (session < 0 || session != prefs.getInt("session", -2)) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            prefs.edit().putString("confirmation", confirmation.toUri(Intent.URI_INTENT_SCHEME)).commit()
        } else prefs.edit().putInt("result", status).remove("session").remove("confirmation").commit()
    }
}
