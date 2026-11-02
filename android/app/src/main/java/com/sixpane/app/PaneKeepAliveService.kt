package com.sixpane.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/** Process-priority aid only. WebViews remain owned by MainActivity. */
class PaneKeepAliveService:Service(){
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Live browser panes",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("SIXPANE panes are open")
            .setContentText("Keep SIXPANE open for live sessions. Activity closure ends live pages.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).build()
        try {
            // Notification denial does not prohibit a foreground service; Android shows it in Task Manager.
            startForeground(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }catch(e:RuntimeException){
            Log.w("SIXPANE","Foreground priority unavailable",e);stopSelf()
        }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_NOT_STICKY
    override fun onTimeout(startId:Int,fgsType:Int){
        // API 35: background dataSync budget is six hours. Never restart to evade that budget.
        Log.w("SIXPANE","Foreground service reached Android's background time limit")
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy(){stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
    companion object {private const val CHANNEL="sixpane-live-panes"}
}
