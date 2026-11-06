package app.tessera.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a phone restart the browsers can't run until the app opens, so post a
 * one-tap "restore your sessions" notification. Logins are kept on disk.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Restore after restart", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        nm.notify(43, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (updated) "Tessera was updated" else "Phone restarted")
            .setContentText("Tap to restore your sessions. Logins are kept; pages reload.")
            .setContentIntent(open).setAutoCancel(true).build())
    }

    private companion object { const val CHANNEL = "restore" }
}
