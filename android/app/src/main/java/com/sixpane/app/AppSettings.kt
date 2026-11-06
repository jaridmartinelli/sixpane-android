package com.sixpane.app

import android.app.AlertDialog

/**
 * Infrequent app-wide administration lives here, never in the pane menu or
 * the top-level workspace actions. None of these settings mutate named
 * WebView profiles, cookies, queue identities or their stored bookmarks.
 */
internal class AppSettings(private val host:MainActivity){
    fun open(){
        val items=arrayOf("Appearance","Battery optimization","Notifications","Developer tools")
        AlertDialog.Builder(host)
            .setTitle("Settings")
            .setItems(items){_,position->when(position){
                0->appearance()
                1->host.requestBatteryExemption(true)
                2->host.requestNotificationPermissionFromSettings()
                3->developerTools()
            }}.show()
    }

    private fun appearance(){
        val options=arrayOf("Dark","Light")
        AlertDialog.Builder(host)
            .setTitle("Appearance")
            .setSingleChoiceItems(options,if(host.store.lightMode)1 else 0){dialog,index->
                host.store.lightMode=index==1
                host.applyChromeTheme()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel",null)
            .show()
    }

    private fun developerTools(){
        val items=arrayOf("Session audit","Session integrity report (read-only)","Pending profile cleanup","Controlled test page")
        AlertDialog.Builder(host)
            .setTitle("Developer tools")
            .setItems(items){_,position->when(position){
                0->SessionAudit(host).open()
                1->SessionIntegrityReport(host).open()
                2->host.notice(
                    host.store.pendingDeletion.size.toString()+
                    " retired profiles pending a clean app restart. No profile ID will be reused."
                )
                3->host.openControlledTestPage()
            }}.show()
    }
}
