package app.tessera.browser

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service that keeps the process at foreground priority while
 * sessions exist, and (by default) holds a partial CPU wake lock and a Wi-Fi
 * lock so pages keep running and stay connected with the screen off.
 * It does not own the WebViews; it keeps the process that owns them alive.
 */
class KeepAliveService : Service() {
    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Session keep-alive", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Tessera keeps your browser sessions running"
                setShowBadge(false)
            }
        )
        goForeground(0, restarted = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as TesseraApp
        val live = app.hub.liveCount()
        // A null intent means Android restarted us after killing the process:
        // the WebViews are gone, so tell the user instead of pretending.
        goForeground(live, restarted = intent == null && live == 0)
        applyLocks(app.store.state.keepCpuAwake, app.store.state.keepWifiAwake)
        return START_STICKY
    }

    private fun goForeground(sessions: Int, restarted: Boolean) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (restarted) "Sessions were stopped by Android" else "Keeping $sessions sessions running"
        val text = if (restarted) "Tap to restore them. Logins are kept; open pages reload."
            else "Pages stay connected while the screen is off. Tap to open."
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, n)
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun applyLocks(cpu: Boolean, wifi: Boolean) {
        if (cpu && cpuLock?.isHeld != true) {
            cpuLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:sessions").apply {
                    setReferenceCounted(false); acquire()
                }
        } else if (!cpu) { cpuLock?.takeIf { it.isHeld }?.release(); cpuLock = null }

        if (wifi && wifiLock?.isHeld != true) {
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tessera:sessions").apply {
                    setReferenceCounted(false); acquire()
                }
        } else if (!wifi) { wifiLock?.takeIf { it.isHeld }?.release(); wifiLock = null }
        cpuHeld = cpuLock?.isHeld == true
        wifiHeld = wifiLock?.isHeld == true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running: the user swiping the task away should not end sessions.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        cpuLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        cpuHeld = false; wifiHeld = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "keepalive"
        private const val NOTIFICATION_ID = 42
        @Volatile var running = false; private set
        @Volatile var cpuHeld = false; private set
        @Volatile var wifiHeld = false; private set

        /** Start or refresh the service (call from the foreground Activity). */
        fun sync(context: Context) {
            running = true
            context.startForegroundService(Intent(context, KeepAliveService::class.java).putExtra("sync", true))
        }
    }
}
