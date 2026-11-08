package com.sixpane.app

import java.net.URI

/** Navigation and bounded recovery; WebView provides its own genuine system user agent. */
object BrowserPolicy {
    const val MAX_RECOVERY_ATTEMPTS = 10
    const val RECOVERY_LOAD_TIMEOUT_MS = 60_000L
    /** Asset hostname region strings are informational and do not alter routing. */
    fun assetRegion(url:String):Pair<String,String>? = try {
        val host=URI(url).host?.lowercase()
        val region=host?.let {
            Regex("""(?:^|[.-])(us|eu|ap|sa|ca|me|af)[-_.]?(east|west|central|north|south)[-_.]?[0-9]?""")
                .find(it)?.value?.trim('.','-','_')
        }
        region?.let{it to "hostname=$host; segment="+it}
    }catch(_:Exception){null}
    /** Same-origin candidate prefixes, shallowest first; preserves raw query and fragment. */
    fun entryCandidates(original:String):List<String> = try {
        val uri=URI(original)
        if(uri.scheme !in listOf("http","https")||uri.rawAuthority.isNullOrBlank())listOf(original)
        else {
            val raw=uri.rawPath.orEmpty()
            if(raw.isBlank()||raw=="/")listOf(original)
            else {
                val pieces=raw.trim('/').split('/').filter{it.isNotBlank()}
                val prefixes=mutableListOf("/")
                var prefix=""
                for(part in pieces.dropLast(1)){
                    prefix+="/"+part
                    prefixes.add(prefix+"/")
                }
                val requested=if(raw.startsWith('/'))raw else "/"+raw
                val candidates=(prefixes+requested).distinct().take(3).toMutableList()
                if(requested !in candidates){candidates.removeAt(candidates.lastIndex);candidates.add(requested)}
                val suffix=(uri.rawQuery?.let{"?"+it}?:"")+(uri.rawFragment?.let{"#"+it}?:"")
                candidates.map{uri.scheme+"://"+uri.rawAuthority+it+suffix}
            }
        }
    }catch(_:Exception){listOf(original)}
    fun isWaitingRoomRedirect(from:String,to:String):Boolean = try{
        val source=URI(from).host?.lowercase()
        val destination=URI(to).host?.lowercase()
        source!=null&&destination!=null&&source!=destination &&
            (destination.endsWith(".queue-it.net") || destination=="queue-it.net" ||
             destination.startsWith("queue.") || destination.startsWith("q."))
    }catch(_:Exception){false}
    fun recoveryDelayMs(attempt:Int):Long {
        require(attempt in 1..MAX_RECOVERY_ATTEMPTS)
        return (2_000L shl (attempt-1)).coerceAtMost(30_000L)
    }
    /**
     * Chrome-style one-handed address entry. URLs go directly to the site; other
     * text is a Google search. Keep normalizeAddress strict for bulk portal URLs.
     * Never interpret a pasted javascript: or other unsupported scheme as a page.
     */
    fun resolveAddressOrSearch(raw:String):String? {
        val text=raw.trim()
        if(text.isEmpty())return null
        if(text=="about:blank")return text
        val explicitHttp=text.startsWith("http://",ignoreCase=true) ||
            text.startsWith("https://",ignoreCase=true)
        val hostWithDomain=Regex("""^(?:[A-Za-z0-9_-]+\.)+[A-Za-z0-9_-]+(?::[0-9]{1,5})?(?:[/#?].*)?$""")
            .matches(text)
        val localhost=Regex("""^localhost(?::[0-9]{1,5})?(?:[/#?].*)?$""",RegexOption.IGNORE_CASE)
            .matches(text)
        val ipAddress=Regex("""^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?:[/#?].*)?$""")
            .matches(text)
        if(explicitHttp||hostWithDomain||localhost||ipAddress){
            val candidate=when {
                explicitHttp->text.replace(" ","%20")
                localhost||ipAddress->"http://"+text
                else->text
            }
            normalizeAddress(candidate)?.let{return it}
        }
        return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text,"UTF-8")
    }

    fun normalizeAddress(raw:String):String? {
        val text=raw.trim()
        if(text=="about:blank")return text
        val candidate=if("://" in text)text else "https://$text"
        return try {
            val uri=URI(candidate)
            candidate.takeIf{uri.scheme in listOf("http","https")&&!uri.host.isNullOrBlank()}
        }catch(_:Exception){null}
    }
}

data class SessionCandidate(val pane:Int,val source:String,val key:String,val value:String)

object SessionAuditPolicy {
    fun duplicates(candidates:List<SessionCandidate>):Set<String> = candidates
        .filter{it.value.isNotEmpty()}.groupBy{it.value}
        .filterValues{rows->rows.map{it.pane}.distinct().size>1}.keys
    fun distinctTotal(candidates:List<SessionCandidate>):Int = candidates
        .map{it.value}.filter{it.isNotEmpty()}.distinct().size
}
