package com.sixpane.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Date

/**
 * Read-only inspection of *existing* panes. Never creates/navigates a WebView, writes
 * cookies or storage, reloads pages, deletes profiles, or changes live session state.
 *
 * The report does not claim that Queue-it recognises separate visitors: that is
 * server-side classification and cannot be established from local profile names.
 */
internal class SessionIntegrityReport(private val host: MainActivity) {
    fun open() {
        val report = capture()
        val textView = TextView(host).apply {
            text = report
            textSize = 12f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(18))
        }
        val scroll = ScrollView(host).apply {
            setBackgroundColor(Color.rgb(30, 39, 52))
            addView(textView)
        }
        AlertDialog.Builder(host)
            .setTitle("Session integrity (read-only)")
            .setView(scroll)
            .setPositiveButton("Copy report") { _, _ ->
                val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SIXPANE integrity report", report))
                host.notice("Report copied. No cookie values or URL tokens were included.")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    internal fun capture(): String {
        val panes = host.auditPanes()
        val expectedNames = panes.map { it.first.profile }
        val uniqueNames = expectedNames.toSet().size
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        val online = panes.count { it.second != null && !it.second!!.interrupted }
        val recovering = panes.count { it.second?.interrupted == true }
        var boundCorrect = 0
        var boundIncorrect = 0
        var boundUnavailable = 0
        var jsReady = 0
        var storageReady = 0
        val details = mutableListOf<String>()
        val provider = WebView.getCurrentWebViewPackage()?.versionName ?: "Unknown"

        panes.forEachIndexed { index, (state, card) ->
            if (card == null) {
                boundUnavailable++
                details.add("Pane ${index + 1}: NOT ALLOCATED | expected profile ${state.profile.takeLast(8)}")
                return@forEachIndexed
            }
            if (card.interrupted) {
                boundUnavailable++
                details.add("Pane ${index + 1}: RECOVERING | expected profile ${state.profile.takeLast(8)}")
                return@forEachIndexed
            }
            val actual = if (supported) runCatching {
                WebViewCompat.getProfile(card.webView).name
            }.getOrNull() else null
            val status = when {
                actual == null -> { boundUnavailable++; "UNAVAILABLE" }
                actual == state.profile -> { boundCorrect++; "MATCH" }
                else -> { boundIncorrect++; "MISMATCH" }
            }
            if (card.webView.settings.javaScriptEnabled) jsReady++
            if (card.webView.settings.domStorageEnabled) storageReady++

            // Hostname only: never copy query strings, queueittoken, or cookie values.
            val url = card.webView.url.orEmpty()
            val hostName = runCatching { Uri.parse(url).host }.getOrNull()
            details.add(
                "Pane ${index + 1}: profile=$status ${state.profile.takeLast(8)}" +
                    " | JavaScript=${card.webView.settings.javaScriptEnabled}" +
                    " | DOM storage=${card.webView.settings.domStorageEnabled}" +
                    " | host=${hostName ?: "(blank or internal)"}"
            )
        }

        return buildString {
            appendLine("SIXPANE — SESSION INTEGRITY / OBSERVATION ONLY")
            appendLine("Captured: ${Date()}")
            appendLine("Installed WebView: $provider")
            appendLine("MULTI_PROFILE supported: $supported")
            appendLine("Current workspaces: ${host.store.spaces.size} / 7")
            appendLine("Declared pane profiles: ${panes.size} / 42")
            appendLine("Distinct declared profile names: $uniqueNames / ${panes.size}")
            appendLine("Manifest profile uniqueness: ${if (uniqueNames == panes.size) "PASS" else "FAIL"}")
            appendLine("Allocated non-recovering WebViews: $online | recovering: $recovering | not allocated: ${panes.size - online - recovering}")
            appendLine("Runtime profile binding: $boundCorrect matched, $boundIncorrect mismatched, $boundUnavailable unavailable")
            appendLine("Runtime profile-binding result: ${if (boundIncorrect > 0) "FAIL" else if (boundUnavailable > 0) "INCOMPLETE" else "PASS"}")
            appendLine("JavaScript enabled in live panes: $jsReady / $online")
            appendLine("DOM storage enabled in live panes: $storageReady / $online")
            appendLine()
            appendLine("NOT VERIFIED BY THIS REPORT:")
            appendLine("- Cross-profile cookie, localStorage or IndexedDB contents (read-only report).")
            appendLine("- HttpOnly/SameSite cookie attributes or service worker state.")
            appendLine("- Real Queue-it visitor-session cookies, Queue IDs, or accepted-session tokens.")
            appendLine("- Redirect/token/challenge success on a protected site.")
            appendLine("- 42 continuously executing Android WebViews through background/renderer death.")
            appendLine("- Distinct device fingerprints or Queue-it server-side visitor classifications.")
            appendLine()
            appendLine("LIMITATIONS: This is a read-only inventory, not a Queue-it acceptance test.")
            appendLine("A distinct local profile is not proof of a distinct Queue-it visitor.")
            appendLine("No page navigated or reloaded. No cookies, bookmarks or profile data changed.")
            appendLine("No full URLs, cookie values or Queue-it tokens included.")
            appendLine()
            appendLine("PER-PANE INVENTORY")
            details.forEach { appendLine(it) }
        }
    }
}
