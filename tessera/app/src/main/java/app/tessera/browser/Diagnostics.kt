package app.tessera.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Diagnostics {

    private fun ago(ms: Long): String {
        if (ms <= 0L) return "never"
        val s = (SystemClock.elapsedRealtime() - ms) / 1000
        return if (s < 120) "${s}s ago" else "${s / 60} min ago"
    }

    private fun keepAliveLines(a: MainActivity): List<String> {
        val pm = a.getSystemService(Context.POWER_SERVICE) as PowerManager
        val st = a.stateRef()
        return listOf(
            "Keep-alive service: " + if (KeepAliveService.running) "running" else "NOT running",
            "CPU wake lock: " + if (KeepAliveService.cpuHeld) "held" else if (st.keepCpuAwake) "NOT held" else "off in settings",
            "Wi-Fi lock: " + if (KeepAliveService.wifiHeld) (if (Build.VERSION.SDK_INT >= 34) "requested (Android 14+ may ignore it)" else "held")
                else if (st.keepWifiAwake) "NOT held" else "off in settings",
            "Battery optimisation: " + if (pm.isIgnoringBatteryOptimizations(a.packageName)) "unrestricted" else "RESTRICTED",
            "Closing from Recents: " + if (a.hubRef().canPark) "full speed (holding window allowed)" else "SLOWED (allow Display over other apps)",
        )
    }

    private fun header(a: MainActivity): List<String> {
        val wv = WebViewCompat.getCurrentWebViewPackage(a)
        val ver = runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull()
        return listOf(
            "Tessera diagnostics — " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()),
            "App: ${a.packageName} $ver",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "WebView: ${wv?.packageName} ${wv?.versionName}",
        )
    }

    /** Copies one pane's status. Cookie VALUES are never included. */
    fun copy(a: MainActivity, p: Pane) {
        val hub = a.hubRef()
        val rt = hub.runtime(p.id)
        val view = hub.view(p.id)
        val bound = view?.let { runCatching { WebViewCompat.getProfile(it).name }.getOrNull() }
        val cookieNames = if (AddressPolicy.isWeb(p.url)) (hub.cookieManager(p).getCookie(p.url) ?: "")
            .split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() } else emptyList()
        StorageProbe.probe(view) { info ->
            val lines = header(a) + listOf(
                "",
                "Workspace ${a.workspaceNumber(p)} · Pane ${a.slotNumber(p)}",
                "Pane id: ${p.id}",
                "Profile: ${p.profile}",
                "WebView bound to: ${bound ?: "(not running)"}" + if (bound != null && bound != p.profile) "  <-- MISMATCH" else "",
                "URL: ${p.url.ifBlank { "(new session)" }}",
                "Title: ${p.title.ifBlank { "-" }}",
                "Locked: ${p.locked} · Desktop: ${p.desktop} · Zoom: ${p.zoom}% · Colour: ${T.colorName(p.color)}",
                "Loading: ${rt.loading} (${rt.progress}%) · Error: ${rt.error ?: "none"} · Crashes recovered: ${rt.crashCount}",
                "JS heartbeat: ${ago(rt.lastHeartbeat)} · Page visibility: ${rt.visibilityState}",
                "Session age: ${(SystemClock.elapsedRealtime() - rt.createdAt) / 60000} min",
                "Cookies for this URL: ${cookieNames.size}" + if (cookieNames.isNotEmpty()) " (${cookieNames.joinToString(", ")})" else "",
                if (info == null) "Storage: unavailable" else
                    "Storage at ${info.origin}: localStorage ${info.localKeys}, sessionStorage ${info.sessionKeys}, IndexedDB: ${info.indexedDb}",
                "User agent: ${view?.settings?.userAgentString ?: "-"}",
                "",
            ) + keepAliveLines(a) + listOf("Live sessions: ${hub.liveCount()} · responding in last 90s: ${hub.responsiveCount()}")
            clip(a, "Tessera diagnostics", lines.joinToString("\n"))
            a.toast("Diagnostics for pane ${a.slotNumber(p)} copied")
        }
    }

    private fun clip(a: MainActivity, label: String, text: String) {
        (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * On-device proof of isolation: writes a different secret cookie into every
     * pane's cookie jar, then checks each jar sees ONLY its own value. Also checks
     * every live WebView is bound to its own unique profile, and that pages are
     * still answering (not slept). Test cookies are removed afterwards.
     */
    fun isolationCheck(a: MainActivity) {
        val hub = a.hubRef()
        val panes = a.stateRef().allPanes()
        val store = ProfileStore.getInstance()
        val url = "https://isolation-check.tessera.test/"
        val tokens = panes.associate { it.id to UUID.randomUUID().toString() }
        val names = panes.map { it.profile }
        val uniqueNames = names.toSet().size == names.size && "Default" !in names
        val mismatched = panes.filter { p ->
            hub.view(p.id)?.let { runCatching { WebViewCompat.getProfile(it).name }.getOrNull() != p.profile } ?: false
        }
        a.toast("Checking ${panes.size} sessions…")
        var pending = panes.size
        panes.forEach { p ->
            val cm = store.getOrCreateProfile(p.profile).cookieManager
            cm.setCookie(url, "tessera_iso=${tokens[p.id]}; Path=/") {
                if (--pending == 0) hub.pingAll(4000) { answered, visible, total ->
                    verify(a, panes, tokens, url, uniqueNames, mismatched, answered, visible, total)
                }
            }
        }
    }

    private fun verify(a: MainActivity, panes: List<Pane>, tokens: Map<String, String>, url: String,
                       uniqueNames: Boolean, mismatched: List<Pane>, awake: Int, visible: Int, live: Int) {
        val hub = a.hubRef()
        val store = ProfileStore.getInstance()
        val leaks = mutableListOf<String>()
        panes.forEach { p ->
            val cm = store.getOrCreateProfile(p.profile).cookieManager
            val seen = (cm.getCookie(url) ?: "").split(';').map { it.trim() }
                .filter { it.startsWith("tessera_iso=") }.map { it.removePrefix("tessera_iso=") }
            if (seen != listOf(tokens[p.id])) leaks += "W${a.workspaceNumber(p)}·P${a.slotNumber(p)} saw ${seen.size} value(s)"
            cm.setCookie(url, "tessera_iso=; Path=/; Max-Age=0", null)
            cm.flush()
        }
        val isolated = leaks.isEmpty() && uniqueNames && mismatched.isEmpty()
        val n = panes.size

        val s = Sheet(a, if (isolated) "$n of $n sessions isolated" else "Isolation problem found",
            if (isolated) "Every pane has its own cookie jar and storage profile." else "Some sessions are not separated. Details below.")
        fun row(ok: Boolean, title: String, detail: String) = s.item(if (ok) Icon.CHECK else Icon.ERROR, title, detail = detail, keepOpen = true) {}
        row(leaks.isEmpty(), "Cookie jars are separate", if (leaks.isEmpty()) "A secret written into each of the $n jars was seen only by its own pane" else leaks.joinToString("; "))
        row(uniqueNames, "Unique profiles", if (uniqueNames) "$n different profile names, none shared, none the default" else "Two panes share a profile")
        row(mismatched.isEmpty(), "Each browser uses its own profile", if (mismatched.isEmpty()) "$live running browsers checked" else "Mismatch in ${mismatched.size} panes")
        row(awake >= live, "Sessions awake", "$awake of $live answered a live ping just now · $visible report themselves as visible")
        keepAliveLines(a).forEach { line ->
            val bad = line.contains("NOT") || line.contains("RESTRICTED")
            row(!bad, line.substringBefore(':'), line.substringAfter(": "))
        }
        val report = (header(a) + listOf("") + listOf(
            "Isolation: ${if (isolated) "PASS" else "FAIL"} ($n sessions)",
            "Cookie leaks: ${if (leaks.isEmpty()) "none" else leaks.joinToString("; ")}",
            "Unique profiles: $uniqueNames · Binding mismatches: ${mismatched.size}",
            "Awake: $awake/$live answered a live ping · visible: $visible",
        ) + keepAliveLines(a)).joinToString("\n")
        s.buttons(a.pillButton("Copy report", filled = false) { clip(a, "Tessera isolation report", report); a.toast("Report copied") },
            a.pillButton("Done", filled = true) { s.dismiss() })
        s.show()
    }
}
