package app.tessera.browser

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/** Read-only look at the current page's storage, run inside that pane's own session. */
data class StorageInfo(val origin: String, val localKeys: Int, val sessionKeys: Int, val indexedDb: String, val visibility: String)

object StorageProbe {
    private const val PHASE1 = """(function(){
      try{window.__tsIdb='pending';
        if(window.indexedDB&&indexedDB.databases){indexedDB.databases().then(function(d){window.__tsIdb=d.map(function(x){return x.name}).join(', ')||'(none)'}).catch(function(){window.__tsIdb='blocked'})}
        else{window.__tsIdb='not reported by this page'}}catch(e){window.__tsIdb='blocked'}
      function n(s){try{return s.length}catch(e){return -1}}
      return JSON.stringify({o:location.origin,l:n(window.localStorage),s:n(window.sessionStorage),v:document.visibilityState});
    })()"""

    fun probe(wv: WebView?, done: (StorageInfo?) -> Unit) {
        if (wv == null) { done(null); return }
        wv.evaluateJavascript(PHASE1) { r ->
            val o = runCatching { JSONObject(JSONTokener(r).nextValue() as String) }.getOrNull()
            if (o == null) { done(null); return@evaluateJavascript }
            Handler(Looper.getMainLooper()).postDelayed({
                wv.evaluateJavascript("String(window.__tsIdb)") { idb ->
                    val names = runCatching { JSONTokener(idb).nextValue() as String }.getOrDefault("?")
                    done(StorageInfo(o.optString("o"), o.optInt("l"), o.optInt("s"), names, o.optString("v")))
                }
            }, 400)
        }
    }
}
