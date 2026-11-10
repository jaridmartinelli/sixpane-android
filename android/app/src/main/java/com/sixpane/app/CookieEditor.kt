package com.sixpane.app

import android.app.*
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONTokener
import org.json.JSONObject
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.webkit.WebViewCompat
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Full-height cookie viewer: CURRENT URL only; Android CookieManager has no complete DevTools metadata API. */
class CookieEditor(private val activity:MainActivity,private val card:PaneCard){
    private val dialog=Dialog(activity,android.R.style.Theme_Material_NoActionBar)
    // The bound profile survives renderer replacement; dialog callbacks must not touch a dead view.
    private lateinit var manager:android.webkit.CookieManager
    private val url get()=card.state.url
    private var selected:String?=null
    private lateinit var list:LinearLayout
    private lateinit var search:EditText
    private lateinit var editButton:TextView
    private lateinit var deleteButton:TextView
    private lateinit var selectedActions:LinearLayout
    private lateinit var siteActions:LinearLayout
    private var filtered=linkedMapOf<String,String>()
    private val bg=0xff0b0e13.toInt()
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
        manager=WebViewCompat.getProfile(card.webView).cookieManager
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        val bar=row().apply{setBackgroundColor(0xff141920.toInt());setPadding(activity.dp(10),0,activity.dp(12),0)}
        bar.addView(action("‹"){dialog.dismiss()},LinearLayout.LayoutParams(activity.dp(44),activity.dp(52)))
        val workspace=activity.store.spaces.indexOfFirst{space->
            space.panes.any{it.id==card.state.id}
        }+1
        bar.addView(label("Pane "+activity.paneIndex(card.state.id)+
            " · Workspace "+workspace+" · "+currentHost(),17f).apply{
                setTypeface(null,Typeface.BOLD)
        },LinearLayout.LayoutParams(0,activity.dp(52),1f))
        root.addView(bar)
        val identity=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(activity.dp(17),activity.dp(10),activity.dp(12),activity.dp(10))}
        identity.addView(label("These belong only to this pane's session (profile …"+
            card.state.profile.takeLast(6)+"). No other pane can see or change them.",
            12f,0xb3ffffff.toInt()))
        if(card.state.locked){
            identity.addView(label("VIEW ONLY — unlock this pane to change cookies",
                13f,0xffff4d57.toInt()).apply{
                setPadding(activity.dp(8),activity.dp(8),activity.dp(8),activity.dp(8))
                setBackgroundColor(0xff252c37.toInt())
            })
        }
        root.addView(identity)
        search=EditText(activity).apply {
            hint="Search cookies";setSingleLine(true);textSize=14f
            setPadding(activity.dp(16),0,activity.dp(12),0)
            background=GradientDrawable().apply{
                setColor(0xff1c222b.toInt());cornerRadius=activity.dp(12).toFloat()
                setStroke(activity.dp(1),0xff2b3340.toInt())
            }
            setTextColor(0xffeef2f7.toInt());setHintTextColor(0xb3ffffff.toInt())
        }
        root.addView(search,LinearLayout.LayoutParams(-1,activity.dp(53)))
        val scroll=ScrollView(activity).apply{isFillViewport=true}
        list=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff141920.toInt())
        }
        selectedActions=row()
        editButton=action("✎ Edit"){editSelected()}
        deleteButton=action("⌫ Delete"){deleteSelected()}
        selectedActions.addView(editButton,LinearLayout.LayoutParams(0,activity.dp(44),1f))
        selectedActions.addView(deleteButton,LinearLayout.LayoutParams(0,activity.dp(44),1f))
        footer.addView(selectedActions)
        siteActions=row()
        siteActions.addView(action("Add cookie"){addCookie()},
            LinearLayout.LayoutParams(0,activity.dp(48),1f))
        siteActions.addView(action("Delete all for this site"){deleteAllForSite()},
            LinearLayout.LayoutParams(0,activity.dp(48),1f))
        footer.addView(siteActions)
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
            val itemName=label(name,14f).apply{setTypeface(null,Typeface.BOLD)}
            itemName.setPadding(activity.dp(17),activity.dp(6),activity.dp(10),activity.dp(6))
            item.addView(itemName,LinearLayout.LayoutParams(-1,activity.dp(27)))
            val valueLabel=label(value,12f,0xb3ffffff.toInt()).apply{
                typeface=Typeface.MONOSPACE;maxLines=2
                ellipsize=android.text.TextUtils.TruncateAt.END
                setPadding(activity.dp(17),0,activity.dp(10),activity.dp(6))
            }
            item.addView(valueLabel)
            item.setOnClickListener {
                selected=name
                if(!card.state.locked)editSelected() else render()
            }
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
        val storage=label("Storage on this page: loading…",12f,Color.LTGRAY)
        storage.setPadding(activity.dp(18),activity.dp(18),activity.dp(18),activity.dp(10))
        list.addView(storage)
        if(!card.interrupted){
            val script="(function(){try{return JSON.stringify({origin:location.origin,"+
                "local:localStorage.length,session:sessionStorage.length});}"+
                "catch(e){return JSON.stringify({error:'unavailable'});}})()"
            card.webView.evaluateJavascript(script){raw->
                val decoded=runCatching{JSONTokener(raw).nextValue() as? String}.getOrNull()
                val j=runCatching{JSONObject(decoded?:"{}")}.getOrNull()
                storage.text="Storage on this page\nOrigin: "+(j?.optString("origin")?:"unavailable")+
                    "\nLocal storage keys: "+(j?.optString("local")?:"unknown")+
                    "\nSession storage keys: "+(j?.optString("session")?:"unknown")+
                    "\nIndexedDB names: not exposed synchronously"
            }
        }
        val limitations=label("Android WebView does not reveal the complete cookie path, expiry or HttpOnly attributes. Unknown scope is never overwritten.",11f,Color.LTGRAY)
        limitations.setPadding(activity.dp(18),activity.dp(19),activity.dp(18),activity.dp(14))
        list.addView(limitations)
        // The handoff's default cookie view shows only site actions. Reveal
        // selected-cookie editing controls only after a live cookie selection;
        // locked panes are strictly view-only and expose no mutation controls.
        selectedActions.visibility=if(selected!=null&&!card.state.locked)
            View.VISIBLE else View.GONE
        siteActions.visibility=if(card.state.locked)View.GONE else View.VISIBLE
        editButton.alpha=if(selected==null||card.state.locked).35f else 1f
        deleteButton.alpha=if(selected==null||card.state.locked).35f else 1f
    }

    private fun addCookie(){
        if(blocked())return
        if(!url.startsWith("https://")&&!url.startsWith("http://")){
            activity.notice("Open a website in this pane to add a cookie");return
        }
        val form=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(activity.dp(15),activity.dp(8),activity.dp(15),activity.dp(8))
        }
        val name=EditText(activity).apply{hint="Name";setSingleLine(true)}
        val value=EditText(activity).apply{hint="Value";setSingleLine(true)}
        val path=EditText(activity).apply{hint="Path";setText("/");setSingleLine(true)}
        form.addView(name);form.addView(value);form.addView(path)
        AlertDialog.Builder(activity).setTitle("Add cookie")
            .setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Save"){_,_->
                val key=name.text.toString().trim()
                val valText=value.text.toString()
                val scope=path.text.toString()
                if(!key.matches(Regex("[A-Za-z0-9!#$%&'*+.^_`|~-]+")) ||
                    valText.any{it==';'||it=='\r'||it=='\n'} ||
                    !scope.startsWith("/")||scope.any{it==';'||it=='\r'||it=='\n'}){
                    activity.notice("Invalid cookie name, value or path");return@setPositiveButton
                }
                val secure=url.startsWith("https://")
                if((key.startsWith("__Host-")&&(!secure||scope!="/"))||
                    (key.startsWith("__Secure-")&&!secure)){
                    activity.notice("Cookie prefix requires a secure HTTPS scope");return@setPositiveButton
                }
                val attrs="; Path="+scope+(if(secure)"; Secure" else "")+"; SameSite=Lax"
                manager.setCookie(url,key+"="+valText+attrs){success->
                    manager.flush()
                    activity.runOnUiThread{
                        if(success && cookies()[key]==valText)
                            activity.notice("Cookie saved in this pane")
                        else activity.notice("Cookie save not verified")
                        render()
                    }
                }
            }.show()
    }
    private fun deleteAllForSite(){
        if(blocked())return
        val current=cookies()
        if(current.isEmpty())return
        val scopes=current.keys.map{key->key to observedScope(key)}
        if(scopes.any{it.second==null||observedAttributes(it.second!!)==null}){
            activity.notice("Some cookie scopes are unknown; deleting all is refused to protect other paths")
            return
        }
        AlertDialog.Builder(activity).setTitle("Delete all cookies for this site?")
            .setMessage("Delete "+scopes.size+" cookies with verified domain/path scopes?")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
                var remaining=scopes.size
                var allAccepted=true
                scopes.forEach{(name,scope)->
                    val attr=observedAttributes(scope!!)!!
                    manager.setCookie(url,name+"=; Max-Age=0"+attr){ok->
                        if(!ok)allAccepted=false
                        remaining--
                        if(remaining==0){
                            manager.flush()
                            activity.runOnUiThread{
                                val gone=current.keys.none{it in cookies()}
                                activity.notice(if(allAccepted&&gone)
                                    "Deletion verified for this site" else
                                    "Deletion NOT verified; original scope may remain")
                                selected=null;render()
                            }
                        }
                    }
                }
            }.show()
    }

    private fun blocked():Boolean{
        if(!card.state.locked)return false
        card.blocked("Unlock browser to modify cookies")
        return true
    }
    private fun observedScope(name:String):CookieScopeEvidence?{
        val host=runCatching{URI(url).host?.lowercase()}.getOrNull()?:return null
        val path=runCatching{URI(url).rawPath.orEmpty().ifBlank{"/"}}.getOrDefault("/")
        val scopes=card.state.cookieScopes.filter {
            it.name==name && (it.domain?.removePrefix(".")?.let{domain->
                if(it.hostOnly)host==domain else host==domain||host.endsWith("."+domain)
            }?:it.hostOnly) &&
                path.startsWith(it.path) && (!it.secure||url.startsWith("https://"))
        }
        // CookieManager.getCookie supplies no path/domain/flags; a name alone
        // cannot distinguish several same-name cookies with different scopes.
        return scopes.singleOrNull()
    }
    private fun observedAttributes(scope:CookieScopeEvidence):String?{
        if(scope.deadlineMs!=null && scope.deadlineMs<=System.currentTimeMillis())return null
        val attributes=StringBuilder("; Path="+scope.path)
        if(!scope.hostOnly){
            val domain=scope.domain?:return null
            attributes.append("; Domain=").append(domain)
        }
        if(scope.secure)attributes.append("; Secure")
        if(scope.httpOnly)attributes.append("; HttpOnly")
        scope.sameSite?.let{attributes.append("; SameSite=").append(it)}
        if(scope.deadlineMs!=null){
            val formatter=SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US)
            formatter.timeZone=TimeZone.getTimeZone("GMT")
            attributes.append("; Expires=").append(formatter.format(Date(scope.deadlineMs)))
        }
        return attributes.toString()
    }
    private fun editSelected(){
        val name=selected?:return
        if(blocked())return
        val old=cookies()[name]?:return
        val scope=observedScope(name)
        val attributes=scope?.let{observedAttributes(it)}
        if(attributes==null){
            activity.notice("Cookie scope unverified; edit refused to protect the original.")
            return
        }
        val input=EditText(activity).apply{setText(old);setSingleLine(true);setSelection(text.length)}
        AlertDialog.Builder(activity).setTitle("Edit $name").setMessage("Observed scope will be preserved: domain, path, Secure, HttpOnly, SameSite and expiry.")
            .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
                val value=input.text.toString()
                if(value.any{it==';'||it=='\n'||it=='\r'}){activity.notice("Cookie value contains unsupported characters");return@setPositiveButton}
                manager.setCookie(url,"$name=$value$attributes"){success->
                    manager.flush()
                    activity.runOnUiThread{
                        if(success){activity.notice("Cookie value edit accepted with observed scope");render()}
                        else activity.notice("Cookie edit rejected; original cookie preserved")
                    }
                }
            }.show()
    }
    private fun deleteSelected(){
        val name=selected?:return
        if(blocked())return
        val scope=observedScope(name)
        val attributes=scope?.let{observedAttributes(it)}
        if(attributes==null){
            activity.notice("Cookie scope unverified; deletion refused.")
            return
        }
        AlertDialog.Builder(activity).setTitle("Are you sure?")
            .setMessage("Delete observed cookie '$name' at its recorded domain and path? This cannot be undone.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
                manager.setCookie(url,"$name=; Max-Age=0$attributes"){success->
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
