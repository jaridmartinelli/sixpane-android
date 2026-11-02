package com.sixpane.app

import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI

/** An in-memory snapshot only: no navigation, cookie writes, storage writes, logs or export. */
class SessionAudit(private val activity:MainActivity){
    private val dialog=Dialog(activity,android.R.style.Theme_Material_NoActionBar)
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var results:LinearLayout
    private lateinit var summary:TextView
    private var generation=0
    private val candidates=mutableListOf<SessionCandidate>()
    private val notes=linkedMapOf<Int,MutableList<String>>()
    private var pending=0
    private fun text(value:String,size:Float=13f,color:Int=Color.WHITE)=TextView(activity).apply{
        text=value;textSize=size;setTextColor(color);setPadding(activity.dp(12),activity.dp(6),activity.dp(12),activity.dp(6))
        setTextIsSelectable(true)
    }
    fun open(){
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(30,39,52))}
        val close=Button(activity).apply{text="‹  Session audit";setOnClickListener{dialog.dismiss()}}
        root.addView(close)
        root.addView(text("Read-only. CookieManager, document.cookie and Queue-it-prefixed localStorage are sampled independently. JavaScript sources require the pane to be on the supplied origin. No pane is navigated. Shared candidates can include ordinary non-session cookies; red does not prove shared queue identity.",12f))
        val input=EditText(activity).apply{
            hint="Host or full URL (include path for path-scoped cookies)";setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
        }
        root.addView(input)
        root.addView(Button(activity).apply{
            text="Read all panes"
            setOnClickListener{
                val url=BrowserPolicy.normalizeAddress(input.text.toString())
                if(url==null||url=="about:blank")input.error="Enter an HTTP or HTTPS host or URL" else scan(url)
            }
        })
        summary=text("No audit run yet.");root.addView(summary)
        results=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(activity).apply{addView(results)},LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root)
        dialog.setOnDismissListener{generation++;handler.removeCallbacksAndMessages(null);candidates.clear();notes.clear()}
        dialog.show();dialog.window?.setLayout(-1,-1)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun cookies(pane:Int,source:String,raw:String){
        raw.split(';').forEach{entry->
            val name=entry.substringBefore('=').trim()
            if('=' in entry&&name.isNotEmpty())candidates.add(SessionCandidate(pane,source,name,entry.substringAfter('=').trim()))
        }
    }
    private fun scan(url:String){
        generation++;val run=generation
        handler.removeCallbacksAndMessages(null);candidates.clear();notes.clear()
        val panes=activity.auditPanes()
        pending=panes.size
        val target=URI(url)
        // Match an exact origin, including non-default ports. A URL's path affects getCookie only.
        val port=target.port.takeIf{it!=-1&&!(it==443&&target.scheme=="https")&&!(it==80&&target.scheme=="http")}
        val origin="${target.scheme}://${target.host.lowercase()}${port?.let{":$it"}?:""}"
        val script="""
            (() => {
              if (location.origin !== ${JSONObject.quote(origin)}) return JSON.stringify({unavailable:'Page is on a different origin; no navigation performed'});
              const result={cookies:'',storage:[],errors:[]};
              try { result.cookies=document.cookie; } catch(e) { result.errors.push('document.cookie unavailable: '+e.name); }
              try {
                for(let i=0;i<localStorage.length;i++) {
                  const key=localStorage.key(i);
                  if(/^queue[-_]?it/i.test(key)) result.storage.push([key,localStorage.getItem(key)]);
                }
              } catch(e) { result.errors.push('localStorage unavailable: '+e.name); }
              return JSON.stringify(result);
            })()
        """.trimIndent()
        panes.forEachIndexed{offset,(_,card)->
            val index=offset+1
            val status=mutableListOf<String>();notes[index]=status
            if(card==null||card.interrupted){status.add("All sources unavailable: pane starting or recovering");pending--;return@forEachIndexed}
            val view=card.webView
            try{
                val raw=WebViewCompat.getProfile(view).cookieManager.getCookie(url).orEmpty()
                cookies(index,"Profile cookies",raw)
                status.add("Profile cookies: read (${raw.split(';').count{'=' in it}} returned). HttpOnly coverage is not guaranteed.")
            }catch(e:RuntimeException){status.add("Profile cookies unavailable: ${e.javaClass.simpleName}")}
            var finished=false
            fun complete(message:String?=null){
                if(finished||run!=generation)return
                finished=true;if(message!=null)status.add(message)
                pending--;render()
            }
            val timeout=Runnable{complete("document.cookie + localStorage unavailable: timed out")}
            handler.postDelayed(timeout,5_000L)
            try{
                view.evaluateJavascript(script){encoded->
                    if(finished||run!=generation)return@evaluateJavascript
                    handler.removeCallbacks(timeout)
                    if(card.interrupted||card.webView!==view){complete("JavaScript snapshot unavailable: renderer changed");return@evaluateJavascript}
                    try{
                        val decoded=JSONTokener(encoded).nextValue() as? String
                        val data=decoded?.let{JSONObject(it)}?:throw IllegalArgumentException("No script result")
                        if(data.has("unavailable"))status.add("document.cookie + localStorage unavailable: ${data.getString("unavailable")}")
                        else {
                            cookies(index,"document.cookie",data.optString("cookies"))
                            val storage=data.optJSONArray("storage")
                            for(i in 0 until (storage?.length()?:0)){
                                val item=storage!!.getJSONArray(i)
                                candidates.add(SessionCandidate(index,"localStorage",item.getString(0),item.optString(1)))
                            }
                            status.add("document.cookie + Queue-it localStorage: read")
                            val errors=data.optJSONArray("errors")
                            for(i in 0 until (errors?.length()?:0))status.add(errors!!.getString(i))
                        }
                        complete()
                    }catch(e:Exception){complete("JavaScript snapshot unavailable: ${e.javaClass.simpleName}")}
                }
            }catch(e:RuntimeException){handler.removeCallbacks(timeout);complete("JavaScript snapshot unavailable: ${e.javaClass.simpleName}")}
        }
        render()
    }
    private fun render(){
        val duplicates=SessionAuditPolicy.duplicates(candidates)
        summary.text="Distinct candidate values: ${SessionAuditPolicy.distinctTotal(candidates)} · Duplicate values: ${duplicates.size} · Pending panes: $pending\n${if(pending==0)"Scan complete; review source availability below." else "Reading…"}"
        results.removeAllViews()
        notes.forEach{(index,status)->
            results.addView(text("Pane $index",16f))
            status.forEach{results.addView(text(it,11f,Color.LTGRAY))}
            val found=candidates.filter{it.pane==index}
            if(found.isEmpty())results.addView(text("No readable candidates. This does not establish a unique queue session.",12f,Color.LTGRAY))
            found.forEach{candidate->
                val duplicate=candidate.value in duplicates
                results.addView(text("${if(duplicate)"DUPLICATE · " else ""}${candidate.source} · ${candidate.key}\n${candidate.value}",13f,if(duplicate)Color.rgb(255,100,100) else Color.WHITE))
            }
        }
    }
}
