package com.sixpane.app

import android.app.*
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.webkit.WebViewCompat
import java.net.URI

/** Full-height cookie viewer: CURRENT URL only; Android CookieManager has no complete DevTools metadata API. */
class CookieEditor(private val activity:MainActivity,private val card:PaneCard){
    private val dialog=Dialog(activity,android.R.style.Theme_Material_NoActionBar)
    private val manager get()=WebViewCompat.getProfile(card.webView).cookieManager
    private val url get()=card.webView.url.orEmpty()
    private var selected:String?=null
    private lateinit var list:LinearLayout
    private lateinit var search:EditText
    private lateinit var editButton:TextView
    private lateinit var deleteButton:TextView
    private var filtered=linkedMapOf<String,String>()
    private val bg=Color.rgb(44,46,51)
    private fun label(text:String,size:Float,color:Int=Color.WHITE)=TextView(activity).apply {
        this.text=text;textSize=size;setTextColor(color);gravity=Gravity.CENTER_VERTICAL
    }
    private fun row():LinearLayout=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun action(text:String,func:()->Unit)=label(text,14f).apply{gravity=Gravity.CENTER;setOnClickListener{func()}}
    private fun currentHost():String=try{URI(url).host?:"No active website"}catch(_:Exception){"No active website"}
    private fun cookies():LinkedHashMap<String,String>{
        val map=linkedMapOf<String,String>()
        if(!url.startsWith("https://")&&!url.startsWith("http://"))return map
        manager.getCookie(url).orEmpty().split(';').map{it.trim()}.filter{it.contains('=')}.forEach{
            val key=it.substringBefore('=').trim();if(key.isNotEmpty())map[key]=it.substringAfter('=')
        }
        return map
    }
    fun open(){
        if(card.interrupted){activity.notice("Browser renderer interrupted. Cookies cannot be inspected until recovery.");return}
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        val bar=row().apply{setBackgroundColor(Color.rgb(28,27,33));setPadding(activity.dp(10),0,activity.dp(12),0)}
        bar.addView(action("‹"){dialog.dismiss()},LinearLayout.LayoutParams(activity.dp(44),activity.dp(52)))
        bar.addView(label("Cookie-Editor",17f),LinearLayout.LayoutParams(0,activity.dp(52),1f))
        root.addView(bar)
        val identity=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(activity.dp(17),activity.dp(10),activity.dp(12),activity.dp(10))}
        identity.addView(label(currentHost(),14f),LinearLayout.LayoutParams(-1,activity.dp(24)))
        identity.addView(label("Browser profile · ${card.state.id.takeLast(7)}",11f,Color.LTGRAY))
        root.addView(identity)
        search=EditText(activity).apply {
            hint="Search cookies";setSingleLine(true);textSize=14f
            setPadding(activity.dp(16),0,activity.dp(12),0)
            setBackgroundColor(Color.rgb(56,59,66));setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
        }
        root.addView(search,LinearLayout.LayoutParams(-1,activity.dp(53)))
        val scroll=ScrollView(activity).apply{fillViewport=true}
        list=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=row().apply{setBackgroundColor(Color.rgb(36,38,44))}
        editButton=action("✎   Edit"){editSelected()}
        deleteButton=action("⌫   Delete"){deleteSelected()}
        footer.addView(editButton,LinearLayout.LayoutParams(0,activity.dp(67),1f))
        footer.addView(deleteButton,LinearLayout.LayoutParams(0,activity.dp(67),1f))
        root.addView(footer)
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render()}
            override fun afterTextChanged(s:Editable?){}
        })
        dialog.setContentView(root)
        dialog.window?.apply{setLayout(-1,-1);setBackgroundDrawableResource(android.R.color.transparent)}
        render()
        dialog.show()
        dialog.window?.setLayout(-1,-1)
    }
    private fun render(){
        list.removeAllViews()
        val found=cookies();filtered=linkedMapOf<String,String>().apply{
            for((k,v) in found)if(k.contains(search.text.toString(),true))put(k,v)
        }
        if(selected !in filtered.keys)selected=null
        for((name,value)in filtered){
            val item=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
            val itemName=label("${if(selected==name)"⌄" else "›"}   $name",12f)
            itemName.setPadding(activity.dp(17),activity.dp(6),activity.dp(10),activity.dp(6))
            item.addView(itemName,LinearLayout.LayoutParams(-1,activity.dp(55)))
            if(selected==name){
                val details=label("Value: ${"•".repeat(value.length.coerceIn(6,18))}\nDomain: ${currentHost()}\nPath / HttpOnly / expiration: unavailable",11f,Color.LTGRAY)
                details.setPadding(activity.dp(35),0,activity.dp(12),activity.dp(10))
                item.addView(details)
            }
            item.setOnClickListener {selected=if(selected==name)null else name;render()}
            val line=View(activity).apply{setBackgroundColor(Color.rgb(63,66,72))}
            list.addView(item);list.addView(line,LinearLayout.LayoutParams(-1,activity.dp(1)))
        }
        if(filtered.isEmpty()){
            val description=if(currentHost()=="No active website")"Open a website in this browser to view its cookies."
              else "No cookies exposed by WebView for this website."
            val empty=label(description,12f,Color.LTGRAY).apply{setPadding(activity.dp(18),activity.dp(24),activity.dp(18),activity.dp(8))}
            list.addView(empty)
        }
        // Honest limitation: getCookie() may not expose every cookie attribute or every origin.
        val limitations=label("Current site only. WebView does not expose full cookie attributes or all stored domains; editing or deletion may fail for cookies using another path.",11f,Color.LTGRAY)
        limitations.setPadding(activity.dp(18),activity.dp(19),activity.dp(18),activity.dp(14))
        list.addView(limitations)
        editButton.alpha=if(selected==null).35f else 1f;deleteButton.alpha=if(selected==null).35f else 1f
    }
    private fun blocked():Boolean{
        if(!card.state.locked)return false
        card.blocked("Unlock browser to modify cookies")
        return true
    }
    private fun editSelected(){
        val name=selected?:return
        if(blocked())return
        val old=cookies()[name]?:return
        val input=EditText(activity).apply{setText(old);setSingleLine(true);setSelection(text.length)}
        AlertDialog.Builder(activity).setTitle("Edit $name").setMessage("Current site · cookie value only. Original path and security attributes may not be available; this cannot guarantee replacement of every matching cookie.")
            .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
                val value=input.text.toString()
                if(value.any{it==';'||it=='\n'||it=='\r'}){activity.notice("Cookie value contains unsupported characters");return@setPositiveButton}
                val secure=if(url.startsWith("https://"))"; Secure" else ""
                manager.setCookie(url,"$name=$value; Path=/$secure"){success->
                    manager.flush()
                    activity.runOnUiThread{
                        if(success){activity.notice("Root-path cookie write accepted; check for duplicate names");render()}
                        else activity.notice("Cookie edit rejected; original cookie preserved")
                    }
                }
            }.show()
    }
    private fun deleteSelected(){
        val name=selected?:return
        if(blocked())return
        AlertDialog.Builder(activity).setTitle("Are you sure?")
            .setMessage("Delete cookie '$name' from this browser's current website? Only root-path deletion can be attempted because WebView does not reveal the cookie's original path. This cannot be undone if successful.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
                val secure=if(url.startsWith("https://"))"; Secure" else ""
                manager.setCookie(url,"$name=; Max-Age=0; Path=/$secure"){success->
                    manager.flush()
                    activity.runOnUiThread{
                        val stillThere=cookies().containsKey(name)
                        if(success&&!stillThere){selected=null;activity.notice("Deletion verified for current URL")}
                        else activity.notice("Cookie deletion NOT verified; another path or domain may require more capable APIs")
                        render()
                    }
                }
            }.show()
    }
}
