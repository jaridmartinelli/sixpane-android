package com.sixpane.app

import android.util.Log
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Passive only: WebView exposes response headers for HTTP errors, not all successful loads.
 * CookieManager remains the only cookie authority. Never persist cookie values to the manifest.
 */
object CookieExpiryObserver {
    fun observe(pane:PaneState,requestUrl:String,headers:Map<String,String>?):Boolean {
        val header=headers?.entries?.firstOrNull{it.key.equals("Set-Cookie",true)}?.value?:return false
        val origin=runCatching{URI(requestUrl)}.getOrNull()?:return false
        val host=origin.host?.lowercase()?:return false
        val segments=header.split(';').map{it.trim()}
        val first=segments.firstOrNull()?:return false
        val name=first.substringBefore('=',"").trim()
        if(name.isBlank() || !first.contains('='))return false
        fun attribute(key:String):String?=segments.drop(1).firstOrNull{
            it.substringBefore('=').trim().equals(key,true)
        }?.substringAfter('=',"")?.trim()?.takeIf{it.isNotEmpty()}
        fun flag(key:String):Boolean=segments.drop(1).any{it.trim().equals(key,true)}
        val domain=attribute("Domain")?.lowercase()?.removePrefix(".")
        val path=attribute("Path")?:origin.rawPath.orEmpty().let{
            if(!it.startsWith("/")||it.indexOf('/',1)<0)"/"
            else it.substringBeforeLast('/')
        }
        if(!path.startsWith("/")||domain!=null&&host!=domain&&!host.endsWith("."+domain))return false
        val maxAge=attribute("Max-Age")?.toLongOrNull()
        val expires=attribute("Expires")
        val expiry=expires?.let{
            runCatching{
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US).apply{
                    timeZone=TimeZone.getTimeZone("GMT");isLenient=false
                }.parse(it)?.time
            }.getOrNull()
        }
        val now=System.currentTimeMillis()
        val deadline=if(maxAge!=null)now+(maxAge.coerceIn(-1_000_000_000L,1_000_000_000L)*1000L)
            else expiry
        val classification=when{
            maxAge!=null->CookieLifetime.EXTENDABLE
            expiry!=null->CookieLifetime.FIXED
            else->CookieLifetime.UNKNOWN
        }
        val sameSite=attribute("SameSite")?.takeIf{
            it.equals("Strict",true)||it.equals("Lax",true)||it.equals("None",true)
        }
        // Redact the cookie VALUE: retaining the raw Set-Cookie would copy live
        // authentication material into shared preferences and potentially logs.
        val evidence="Set-Cookie: "+name+"=<redacted>; sourceHost="+host+
            "; Max-Age="+(attribute("Max-Age")?:"absent")+
            "; Expires="+(expires?:"absent")
        val scope=CookieScopeEvidence(name,domain?:host,domain==null,path,
            flag("Secure"),flag("HttpOnly"),sameSite,deadline,evidence,
            classification,maxAge!=null||expires!=null)
        pane.cookieScopes.removeAll{it.name==name&&it.domain==scope.domain&&it.path==path}
        pane.cookieScopes.add(scope)
        Log.i("SIXPANE_R16","profile="+pane.profile+" cookie="+name+
            " classification="+classification.name+" deadline="+deadline+" evidence="+evidence)
        return true
    }
}
