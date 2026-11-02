package com.sixpane.app

import java.net.URI

/** Shared request identity and bounded recovery; no per-pane UA variation. */
object BrowserPolicy {
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val MAX_RECOVERY_ATTEMPTS = 10
    const val RECOVERY_LOAD_TIMEOUT_MS = 60_000L
    fun recoveryDelayMs(attempt:Int):Long {
        require(attempt in 1..MAX_RECOVERY_ATTEMPTS)
        return (2_000L shl (attempt-1)).coerceAtMost(30_000L)
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
