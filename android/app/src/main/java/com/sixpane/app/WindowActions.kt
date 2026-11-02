package com.sixpane.app

import android.app.AlertDialog
import android.graphics.Color
import android.view.Gravity
import android.widget.EditText

/** Transient pane controls; no persistent browser toolbar. */
class WindowActions(private val host:MainActivity,private val origin:PaneCard,private val targets:List<PaneState>){
    private val ids=targets.map{it.id}.toSet()
    private fun bottom(dialog:AlertDialog){
        dialog.show()
        dialog.window?.setGravity(Gravity.BOTTOM)
    }
    fun open(){
        val labels=arrayOf("Lock","Desktop mode","Enter URL","Edit cookies","Close window","Select multiple","Frame color")
        bottom(AlertDialog.Builder(host,android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(if(targets.size==1)"Window ${host.paneIndex(origin.state.id)}" else "${targets.size} selected windows")
            .setItems(labels){_,which->when(which){
                0->{val locked=!targets.all{it.locked};targets.forEach{it.locked=locked};host.updateWindowLocks()}
                1->if(unlocked())host.confirm("Change display mode?","This reloads the selected pages. Cookies stay in their own browsers."){
                    val next=!targets.all{it.desktop};targets.forEach{host.windowCard(it.id)?.setDesktopMode(next)}
                }
                2->address()
                3->if(targets.size!=1)host.notice("Select one window to edit cookies") else if(unlocked())host.cookieEditor(origin)
                4->host.closeWindows(ids)
                5->host.toggleSelectionFromMenu(origin)
                6->colors()
            }}.create())
    }
    private fun unlocked():Boolean{
        if(targets.any{it.locked}){host.notice("Unlock the selected windows first");return false}
        return true
    }
    private fun address(){
        val field=EditText(host).apply{
            setSingleLine(true);setTextColor(Color.WHITE);hint="Search Google or type URL"
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_GO
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            if(targets.size==1)setText(origin.state.url)
        }
        val dialog=AlertDialog.Builder(host,android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Enter URL").setView(field).setPositiveButton("Open",null).setNegativeButton("Cancel",null).create()
        bottom(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            if(targets.size==1){
                if(origin.submitAddress(field.text.toString()))dialog.dismiss() else field.error="Enter an address or search term"
            }else{
                val url=BrowserPolicy.normalizeAddress(field.text.toString())
                if(url==null)field.error="Enter a valid web address" else{
                    targets.forEach{pane->host.windowCard(pane.id)?.loadAddress(url)?:run{pane.url=url}}
                    host.persist();dialog.dismiss()
                }
            }
        }
        field.setOnEditorActionListener { _,actionId,_ ->
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_GO){
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            }else false
        }
    }
    private fun colors(){
        val names=arrayOf("Graphite","Blue","Teal","Amber","Purple","Rose","Silver","Green")
        val values=arrayOf("#51606e","#82b6ff","#72dbcf","#e8bd76","#b5a0ef","#e89eae","#c8d1da","#9acb91")
        bottom(AlertDialog.Builder(host,android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Frame color")
            .setItems(names){_,i->host.recolorWindows(ids,Color.parseColor(values[i]))}.create())
    }
}
