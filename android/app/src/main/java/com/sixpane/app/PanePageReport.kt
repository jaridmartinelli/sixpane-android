package com.sixpane.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Date

/**
 * Copies a user-requested snapshot of ONE currently loaded page.
 * Read-only: no navigation, cookie/storage reads or writes, reloads, or profile changes.
 * Page text can contain identifiers displayed by the website; it is never sent anywhere.
 */
class PanePageReport(private val activity: MainActivity) {
    fun copy(card: PaneCard) {
        if (card.interrupted) {
            activity.notice("This pane is recovering. Try copying after its page loads.")
            return
        }
        val view = card.webView
        val originalUrl = view.url.orEmpty()
        if (originalUrl.isBlank() || originalUrl == "about:blank") {
            activity.notice("Open CreepJS, BrowserLeaks, or another page in this pane first.")
            return
        }
        var complete = false
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (!complete) {
                complete = true
                activity.notice("Page report timed out; no clipboard change.")
            }
        }
        handler.postDelayed(timeout, 12_000L)
        try {
            view.evaluateJavascript(SNAPSHOT_SCRIPT) { encoded ->
                if (complete) return@evaluateJavascript
                complete = true
                handler.removeCallbacks(timeout)
                if (card.interrupted || card.webView !== view || view.url != originalUrl) {
                    activity.notice("Page changed while collecting the report; please retry.")
                    return@evaluateJavascript
                }
                try {
                    val decoded = JSONTokener(encoded).nextValue() as? String
                        ?: throw IllegalArgumentException("No page result")
                    val snapshot = JSONObject(decoded)
                    if (snapshot.has("error")) {
                        throw IllegalStateException(snapshot.getString("error"))
                    }
                    val report = formatReport(card, view, snapshot)
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                        "SIXPANE pane " + activity.paneIndex(card.state.id) + " page report", report
                    ))
                    activity.notice("Pane " + activity.paneIndex(card.state.id) +
                        " report copied. Review it for private IDs before sharing.")
                } catch (e: Exception) {
                    activity.notice("Could not copy page report: " +
                        (e.message ?: e.javaClass.simpleName))
                }
            }
        } catch (e: RuntimeException) {
            complete = true
            handler.removeCallbacks(timeout)
            activity.notice("Page report unavailable: " + e.javaClass.simpleName)
        }
    }

    private fun formatReport(card: PaneCard, view: WebView, page: JSONObject): String {
        val state = card.state
        val version = try {
            WebView.getCurrentWebViewPackage()?.versionName ?: "Unavailable"
        } catch (_: Exception) {
            "Unavailable"
        }
        return buildString {
            appendLine("SIXPANE — PER-PANE PAGE & BROWSER REPORT")
            appendLine("Captured: " + Date().toString())
            appendLine("NOTE: This clipboard report may include queue identifiers or other private values displayed on the page. Review before sending.")
            appendLine("Collection: Read-only. No navigation, reload, cookies, storage, or session IDs were modified.")
            appendLine()
            appendLine("=== PANE / APP CONTEXT ===")
            appendLine("Pane number: " + activity.paneIndex(state.id))
            appendLine("Pane ID: " + state.id)
            appendLine("WebView profile ID: " + state.profile)
            appendLine("Page URL: " + page.optString("url", view.url.orEmpty()))
            appendLine("Document title: " + page.optString("title", ""))
            appendLine("Document ready state: " + page.optString("readyState", "unknown"))
            appendLine("Android device: " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("Android API level: " + Build.VERSION.SDK_INT)
            appendLine("WebView package version: " + version)
            appendLine("Actual WebView user agent: " + view.settings.userAgentString)
            appendLine("Shared desktop UA setting: " + activity.store.desktopUserAgent)
            appendLine("Pane desktop mode: " + state.desktop)
            appendLine("Pane zoom setting: " + state.zoom + "%")
            appendLine("Pane locked: " + state.locked)
            appendLine("Viewport width setting: " + state.widthPercent + "%")
            appendLine("Viewport height setting: " + state.heightPercent + "%")
            appendLine("Measured WebView bounds: " + view.width + " × " + view.height + " px")
            appendLine()
            appendLine("=== JAVASCRIPT-REPORTED BROWSER / DISPLAY VALUES ===")
            appendLine(page.optJSONObject("environment")?.toString(2) ?: "Unavailable")
            appendLine()
            appendLine("=== PAGE CONTENT AS EXPOSED TO THIS WEBVIEW ===")
            appendLine("Characters detected: " + page.optLong("pageTextTotalLength", 0L))
            appendLine("Content truncated at 300000 characters: " + page.optBoolean("pageTextTruncated", false))
            appendLine(page.optString("pageText", "(No readable page text returned)"))
            appendLine()
            appendLine("=== SAME-ORIGIN FRAME CONTENT (WHEN ACCESSIBLE) ===")
            appendLine("Frame count: " + page.optInt("iframeCount", 0))
            val frames = page.optJSONArray("frames")
            if (frames == null || frames.length() == 0) {
                appendLine("No frames detected.")
            } else {
                for (i in 0 until frames.length()) {
                    val frame = frames.optJSONObject(i) ?: continue
                    appendLine("Frame " + (i + 1) + ": " + frame.optString("status", "unknown"))
                    appendLine("URL: " + frame.optString("url", "unavailable"))
                    appendLine("Characters detected: " + frame.optLong("totalLength", 0))
                    appendLine("Truncated: " + frame.optBoolean("truncated", false))
                    appendLine(frame.optString("text", ""))
                }
            }
            appendLine()
            appendLine("=== LIMITATIONS ===")
            appendLine("This report copies text exposed in the page DOM plus browser/display diagnostics. It is not the site's internal fingerprint database or a network trace.")
            appendLine("Images, canvas-only results, closed shadow DOM, and cross-origin iframe contents cannot be fully extracted. Very long page or frame text is capped.")
            appendLine("No hidden HttpOnly cookies, private localStorage values, or real Queue-it tokens are read separately. Matching fingerprints alone do not establish shared sessions.")
        }
    }

    companion object {
        private val SNAPSHOT_SCRIPT = """
            (() => {
              try {
                const pageText = String(document.body?.innerText ||
                    document.documentElement?.innerText || '');
                const textCap = 300000;
                const v = window.visualViewport;
                const info = {
                  userAgent: navigator.userAgent,
                  platform: navigator.platform,
                  language: navigator.language,
                  languages: Array.from(navigator.languages || []),
                  hardwareConcurrency: navigator.hardwareConcurrency ?? null,
                  deviceMemory: navigator.deviceMemory ?? null,
                  maxTouchPoints: navigator.maxTouchPoints ?? null,
                  webdriver: navigator.webdriver ?? null,
                  cookieEnabled: navigator.cookieEnabled,
                  doNotTrack: navigator.doNotTrack,
                  vendor: navigator.vendor,
                  userAgentBrands: navigator.userAgentData
                      ? Array.from(navigator.userAgentData.brands || []) : null,
                  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                  innerWidth: innerWidth,
                  innerHeight: innerHeight,
                  outerWidth: outerWidth,
                  outerHeight: outerHeight,
                  devicePixelRatio: devicePixelRatio,
                  visualViewportWidth: v ? v.width : null,
                  visualViewportHeight: v ? v.height : null,
                  screenWidth: screen.width,
                  screenHeight: screen.height,
                  screenAvailWidth: screen.availWidth,
                  screenAvailHeight: screen.availHeight,
                  screenColorDepth: screen.colorDepth,
                  screenPixelDepth: screen.pixelDepth,
                  documentVisibility: document.visibilityState,
                  preferredColorSchemeDark: matchMedia('(prefers-color-scheme: dark)').matches,
                  plugins: Array.from(navigator.plugins || []).map(p => p.name)
                };
                const allFrames = Array.from(document.querySelectorAll('iframe'));
                const frames = [];
                for (const frame of allFrames.slice(0, 24)) {
                  try {
                    const doc = frame.contentDocument;
                    if (!doc) {
                      frames.push({status:'Unavailable (possibly cross-origin)'});
                      continue;
                    }
                    const text = String(doc.body?.innerText ||
                        doc.documentElement?.innerText || '');
                    frames.push({
                      status: 'Accessible',
                      url: String(doc.location.href),
                      text: text.slice(0, 30000),
                      totalLength: text.length,
                      truncated: text.length > 30000
                    });
                  } catch (e) {
                    frames.push({status:'Inaccessible: '+ e.name});
                  }
                }
                return JSON.stringify({
                  title: document.title,
                  url: location.href,
                  readyState: document.readyState,
                  environment: info,
                  pageText: pageText.slice(0, textCap),
                  pageTextTotalLength: pageText.length,
                  pageTextTruncated: pageText.length > textCap,
                  frames: frames,
                  iframeCount: allFrames.length
                });
              } catch(e) {
                return JSON.stringify({error: e.name + ': ' + e.message});
              }
            })()
        """.trimIndent()
    }
}
