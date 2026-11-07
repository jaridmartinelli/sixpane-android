package com.sixpane.app

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * R8 observational QA ONLY, never a Queue-it acceptance or challenge-solving test.
 *
 * Arm 1: two six-pane workspaces follow the EXACT QA treatment crossover in
 * PaneCard.createWebView(): workspace 1 first three native, last three stripped;
 * workspace 2 reverses it. Six viewport ratios come from PaneState.distinctSix()
 * (LocalStore.kt:25-32), repeated as matched physical-size controls in workspace 2.
 * These are test-only viewport configurations, NOT the user's persisted panes.
 *
 * Arm 2: after Arm 1, run when USER_AGENT_METADATA is supported regardless
 * of whether UA-only looked different. Uses the exact shared treatment called
 * by real PaneCard, logs setter result/exception and metadata readback.
 * The second label STRIPPED_METADATA is NEVER included in Arm 1 comparisons.
 *
 * Observe HTTP request headers from httpbin.org/headers in-page, plus browser JS
 * values and a read-only demo waiting-room/challenge indication. Demo redirects
 * (including to queue-it.com/product) are allowed. No credentials or tokens logged.
 * No interception, header injection, challenge response or session migration.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalUaSurfaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private enum class Treatment { NATIVE, STRIPPED, STRIPPED_METADATA }
    private enum class Challenge { YES, NO, UNKNOWN }

    private data class Sample(
        val pane: Int,
        val workspace: Int,
        val viewportGroup: Int,
        val widthPercent: Int,
        val heightPercent: Int,
        val treatment: Treatment
    )

    private data class Observation(
        val sample: Sample,
        val diagnosis: QaUaConfiguration.Result,
        val headerUa: String,
        val sentMobileHint: String,
        val sentBrandsHint: String,
        val clientHints: String,
        val brands: String,
        val jsUa: String,
        val actualViewport: String,
        val challenge: Challenge,
        val finalPage: String,
        val notes: String
    )

    @Test
    fun observeUaSurfacesAndChallenges() {
        assertEquals("Separate QA package required", QA_PACKAGE, context.packageName)
        assertTrue("QA treatment must be enabled", BuildConfig.QA_WV_AB)
        assertFalse("Run on a physical Android device", isEmulator())
        assertTrue("Named WebView profiles required",
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))

        val plan = samples()
        // Run matched crossover pairs adjacent in time; do not use a later
        // workspace's results as a separate uncontrolled batch.
        val ordered = (0 until WorkspacePolicy.PANE_COUNT).flatMap { index ->
            listOf(plan[index], plan[index + WorkspacePolicy.PANE_COUNT])
        }
        val arm1 = ordered.map { measure(it) }

        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
        Log.i("SIXPANE_R8", "R8_FEATURE USER_AGENT_METADATA supported=" + supported)
        val arm2 = if (supported) {
            // Only stripped-UA samples are eligible; matched viewport settings
            // are unchanged. Metadata is NEVER enabled for Arm 1.
            plan.filter { it.treatment == Treatment.STRIPPED }
                .map { measure(it.copy(treatment = Treatment.STRIPPED_METADATA)) }
        } else emptyList()

        val metadataStatus = when {
            !supported -> "NOT_RUN: USER_AGENT_METADATA unsupported by installed WebView"
            else -> "SECOND_ARM: shared PaneCard stripped+mobile UA and metadata (excluded from UA-only comparison)"
        }
        val report = buildReport(arm1, arm2, metadataStatus)
        // One table and one-line read are sent directly to am instrument output.
        // No assertion is ever made from inside a WebView JS callback.
        val status = Bundle()
        status.putString(Instrumentation.REPORT_KEY_STREAMRESULT, report)
        instrumentation.sendStatus(0, status)
    }

    private fun samples(): List<Sample> {
        val six = PaneState.distinctSix()
        return (0 until 2 * WorkspacePolicy.PANE_COUNT).map { ordinal ->
            val withinWorkspace = ordinal % WorkspacePolicy.PANE_COUNT
            val workspaceZero = ordinal / WorkspacePolicy.PANE_COUNT
            val laterHalf = withinWorkspace >= WorkspacePolicy.PANE_COUNT / 2
            val reverse = workspaceZero % 2 == 1
            val treatment = if (laterHalf != reverse) Treatment.STRIPPED else Treatment.NATIVE
            val layout = six[withinWorkspace]
            Sample(ordinal + 1, workspaceZero + 1, withinWorkspace + 1,
                layout.widthPercent, layout.heightPercent, treatment)
        }
    }

    private fun measure(sample: Sample): Observation {
        val profile = "qa-r8-" + UUID.randomUUID()
        lateinit var view: WebView
        val note = mutableListOf<String>()
        val baseW = (context.resources.displayMetrics.widthPixels / 2).coerceAtLeast(120)
        val baseH = (context.resources.displayMetrics.heightPixels / 3).coerceAtLeast(120)
        val width = (baseW / WorkspacePolicy.normalViewScale(sample.widthPercent))
            .roundToInt().coerceAtLeast(1)
        val height = (baseH / WorkspacePolicy.normalViewScale(sample.heightPercent))
            .roundToInt().coerceAtLeast(1)
        lateinit var diagnosis: QaUaConfiguration.Result

        instrumentation.runOnMainSync {
            view = WebView(context)
            // Profile assignment MUST be the first operation on the WebView.
            WebViewCompat.setProfile(view, profile)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.useWideViewPort = true
            view.settings.loadWithOverviewMode = true
            // EXACT same function as the real PaneCard override, not a test-local
            // recreation of its UA or metadata builder. Arm 1 deliberately asks
            // that same function for UA_ONLY to preserve experiment separation.
            val variant = when (sample.treatment) {
                Treatment.NATIVE -> QaUaConfiguration.Treatment.NATIVE
                Treatment.STRIPPED -> QaUaConfiguration.Treatment.UA_ONLY
                Treatment.STRIPPED_METADATA ->
                    QaUaConfiguration.Treatment.UA_WITH_METADATA
            }
            diagnosis = QaUaConfiguration.apply(view.settings, variant)
            note.add("SHARED_PANECARD_CONFIG=" + variant.name)
            note.add("SETTER_RESULT=" + diagnosis.metadataResult)
            note.add("READBACK=" + diagnosis.metadataReadback)
            // Mirror PaneCard.applyViewportDimensions(): real logical viewport
            // dimensions change inversely to the persisted percentage scale.
            // Detached test WebView is laid out explicitly; JS reports actual
            // dimensions so an unmeasured/zero viewport cannot pass as measured.
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width,
                    android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height,
                    android.view.View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }

        try {
            navigate(view, HEADERS_URL)
            val headersSnapshot = snapshot(view, HEADERS_SCRIPT)
            val headers = parseHeaders(headersSnapshot)
            val headerUa = headers?.optString("user-agent", "")?.ifBlank { "NOT_RETURNED" }
                ?: "UNAVAILABLE"
            val sentMobile = headers?.optString("sec-ch-ua-mobile", "ABSENT") ?: "UNAVAILABLE"
            val sentBrands = headers?.optString("sec-ch-ua", "ABSENT") ?: "UNAVAILABLE"
            val ch = if (headers == null) "UNAVAILABLE" else {
                val entries = mutableListOf<String>()
                val iterator = headers.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    if (key.lowercase().startsWith("sec-ch-")) {
                        entries.add(key.lowercase() + "=" + headers.optString(key))
                    }
                }
                if (entries.isEmpty()) "NONE_ECHOED" else entries.sorted().joinToString("; ")
            }
            if (headers == null)
                note.add("HTTPBIN_UNPARSEABLE:" + headersSnapshot.optString("error", "no headers"))
            val jsUa = headersSnapshot.optString("ua", "UNAVAILABLE")
            val brands = if (headersSnapshot.isNull("brands")) "UNAVAILABLE"
                else headersSnapshot.optString("brands", "UNAVAILABLE")
            val viewport = listOf(
                headersSnapshot.optInt("innerW", -1),
                headersSnapshot.optInt("innerH", -1),
                headersSnapshot.optInt("screenW", -1),
                headersSnapshot.optInt("screenH", -1)
            ).joinToString("x", limit = 2, truncated = "") +
                " /screen=" +
                headersSnapshot.optInt("screenW", -1) + "x" +
                headersSnapshot.optInt("screenH", -1) +
                " /layout=" + width + "x" + height
            if (headersSnapshot.optInt("innerW", -1) <= 0)
                note.add("VIEWPORT_NOT_MEASURED")

            navigate(view, CHALLENGE_URL)
            val challengeSnapshot = snapshot(view, CHALLENGE_SCRIPT)
            val challenge = when (challengeSnapshot.optString("challenge")) {
                "YES" -> Challenge.YES
                "NO" -> Challenge.NO
                else -> Challenge.UNKNOWN
            }
            val path = challengeSnapshot.optString("path", "UNAVAILABLE")
            val observation = challengeSnapshot.optString("observation", "")
            if (observation.isNotEmpty()) note.add("CHALLENGE:" + observation)

            Log.i("SIXPANE_R8",
                "R8_RUNTIME pane=" + sample.pane + " treatment=" + sample.treatment.name +
                    " mobilePresent=" + diagnosis.mobilePresent +
                    " metaSupported=" + diagnosis.metadataSupported +
                    " setter=" + diagnosis.metadataResult +
                    " sentSecChUaMobile=" + sentMobile +
                    " sentSecChUa=" + sentBrands +
                    " jsBrands=" + brands +
                    " sentUa=" + headerUa)
            return Observation(sample, diagnosis, headerUa, sentMobile, sentBrands,
                ch, brands, jsUa, viewport, challenge, path, note.joinToString("; "))
        } finally {
            instrumentation.runOnMainSync {
                view.stopLoading()
                view.destroy()
            }
        }
    }

    private fun navigate(view: WebView, url: String) {
        val finished = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(webView: WebView?, loadedUrl: String?) {
                    if (webView === view) finished.countDown()
                }
            }
            view.loadUrl(url)
        }
        assertTrue("WebView onPageFinished timed out after 20s; origin=" +
            android.net.Uri.parse(url).host, finished.await(20, TimeUnit.SECONDS))
    }

    private fun snapshot(view: WebView, script: String): JSONObject {
        val done = CountDownLatch(1)
        val raw = AtomicReference<String>("null")
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                raw.set(value ?: "null")
                done.countDown()
            }
        }
        assertTrue("WebView JavaScript snapshot timed out", done.await(20, TimeUnit.SECONDS))
        return try {
            val text = JSONTokener(raw.get()).nextValue() as? String
            if (text.isNullOrEmpty()) JSONObject().put("error", "empty JS result")
            else JSONObject(text)
        } catch (e: Exception) {
            JSONObject().put("error", e.javaClass.simpleName + ": " + e.message)
        }
    }

    private fun parseHeaders(data: JSONObject): JSONObject? {
        val source = data.optString("headersJson")
        if (source.isBlank()) return null
        return try {
            val response = JSONObject(source)
            val obj = response.optJSONObject("headers") ?: return null
            JSONObject().also { normalized ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    normalized.put(k.lowercase(), obj.optString(k))
                }
            }
        } catch (_: Exception) { null }
    }

    private fun buildReport(
        primary: List<Observation>,
        metadata: List<Observation>,
        metadataStatus: String
    ): String {
        val report = StringBuilder()
        report.appendLine("R8 OBSERVATION: physical QA | 12 matched pane settings, synthetic LocalStore.distinctSix | source WebKit 1.13.0")
        report.appendLine("PRIMARY: native vs UA stripped + Mobile (metadata OFF) | SECONDARY: " + metadataStatus)
        report.appendLine("R8_FEATURE USER_AGENT_METADATA supported=" +
            WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA) +
            " | PaneCard treatment uses the exact QaUaConfiguration.apply implementation")
        report.appendLine("pane | ws | treatment | viewport LocalStore W%xH% (group) | measured innerWxH / screen / layout px | configured UA | mobilePresent | metadataSupported | setterResult | metadataReadback | sent User-Agent (httpbin) | sent sec-ch-ua-mobile | sent sec-ch-ua | all echoed sec-ch-* | navigator.userAgent | navigator.userAgentData.brands | challenge | final host/path | notes")
        for (o in primary + metadata) {
            report.appendLine(listOf(
                o.sample.pane.toString(),
                o.sample.workspace.toString(),
                o.sample.treatment.name,
                o.sample.widthPercent.toString() + "%x" +
                    o.sample.heightPercent + "% (g" + o.sample.viewportGroup + ")",
                o.actualViewport,
                cell(o.diagnosis.configuredUa),
                o.diagnosis.mobilePresent.toString(),
                o.diagnosis.metadataSupported.toString(),
                cell(o.diagnosis.metadataResult),
                cell(o.diagnosis.metadataReadback),
                cell(o.headerUa),
                cell(o.sentMobileHint),
                cell(o.sentBrandsHint),
                cell(o.clientHints),
                cell(o.jsUa),
                cell(o.brands),
                o.challenge.name,
                cell(o.finalPage),
                cell(o.notes)
            ).joinToString(" | "))
        }
        // Counts derive ONLY from echoed HTTP headers and JS in the physical
        // device run, never from intended settings or the setter's return value.
        // This line is NOT a success claim unless every measured condition holds.
        val n = metadata.size
        val mobileInUa = metadata.count {
            it.diagnosis.mobilePresent &&
                it.headerUa.contains(" Mobile Safari/537.36")
        }
        val mobileHint = metadata.count { it.sentMobileHint.trim() == "?1" }
        val chromeHeader = metadata.count {
            it.sentBrandsHint.contains("Google Chrome", ignoreCase = true)
        }
        val chromeJs = metadata.count {
            it.brands.contains("Google Chrome", ignoreCase = true)
        }
        val returned = metadata.count { it.diagnosis.metadataResult == "SET_RETURNED" }
        val proven = n > 0 && mobileInUa == n && mobileHint == n &&
            chromeHeader == n && chromeJs == n && returned == n
        report.appendLine(
            "R8_FIELDS_RUNTIME: treatment=STRIPPED_METADATA samples=" + n +
                " configured+sentMobileUA=" + mobileInUa + "/" + n +
                " sentSecChUaMobile1=" + mobileHint + "/" + n +
                " sentGoogleChromeBrand=" + chromeHeader + "/" + n +
                " jsGoogleChromeBrand=" + chromeJs + "/" + n +
                " setterReturned=" + returned + "/" + n +
                " verdict=" + if (proven) "ALL_THREE_OBSERVED_ON_DEVICE"
                    else "NOT_PROVEN_ON_DEVICE"
        )
        report.appendLine(readPrimary(primary))
        return report.toString()
    }

    private fun readPrimary(rows: List<Observation>): String {
        // Only matched arm-1 outcomes count. The metadata rows are excluded.
        val pairs = (1..WorkspacePolicy.PANE_COUNT).mapNotNull { group ->
            val variants = rows.filter { it.sample.viewportGroup == group }
            val native = variants.singleOrNull { it.sample.treatment == Treatment.NATIVE }
            val stripped = variants.singleOrNull { it.sample.treatment == Treatment.STRIPPED }
            if (native == null || stripped == null ||
                native.challenge == Challenge.UNKNOWN || stripped.challenge == Challenge.UNKNOWN
            ) null else native.challenge to stripped.challenge
        }
        if (pairs.isEmpty()) return "READ: Challenge association indeterminate: no complete known-outcome viewport-matched pairs."
        val differing = pairs.count { it.first != it.second }
        val yes = pairs.count { it.first == Challenge.YES || it.second == Challenge.YES }
        return when {
            differing > 0 ->
                "READ: Challenges differ by UA treatment in " + differing + "/" +
                    pairs.size + " matched viewport pairs; association only, not causation (order/server state may confound)."
            yes in 1 until pairs.size ->
                "READ: Challenges track viewport groups, not UA treatment: matched treatment pairs agree while viewport groups differ. A/B is confounded and wv is not the lever."
            else ->
                "READ: No measurable challenge variation across known matched pairs (" +
                    yes + " challenged of " + pairs.size +
                    "); cannot attribute challenge to UA treatment or viewport."
        }
    }

    private fun cell(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ').replace('|', '/').take(360)

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", true) ||
            Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.MODEL.contains("sdk_gphone", true)

    companion object {
        private const val QA_PACKAGE = "com.sixpane.app.qa.wvab"
        private const val HEADERS_URL = "https://httpbin.org/headers"
        private const val CHALLENGE_URL =
            "https://queueitcom.queue-it.net/?c=queueitcom&e=wrdemoproduct"

        // Read HTTPBIN's response JSON from the document; shouldInterceptRequest
        // is not an outbound request-header observer and must not be used here.
        private val HEADERS_SCRIPT = """
            (() => {
              try {
                const raw = (document.body && document.body.innerText) || '';
                const uaData = navigator.userAgentData;
                return JSON.stringify({
                  headersJson: raw, ua: navigator.userAgent,
                  brands: uaData && uaData.brands
                    ? Array.from(uaData.brands, b => b.brand + '/' + b.version).join(';')
                    : null,
                  innerW: innerWidth, innerH: innerHeight,
                  screenW: screen.width, screenH: screen.height
                });
              } catch (e) { return JSON.stringify({error:String(e)}); }
            })();
        """.trimIndent()

        private val CHALLENGE_SCRIPT = """
            (() => {
              try {
                const text = (document.body && document.body.innerText || '').slice(0,12000);
                const title = document.title || '';
                const normalLanding = location.hostname === 'queue-it.com' &&
                  location.pathname.startsWith('/product');
                const queueHost = location.hostname.endsWith('.queue-it.net');
                const explicit = /captcha|verify you are human|proof.of.work|security challenge|security check/i;
                const waiting = /you are now in line|estimated wait|queue paused|waiting room/i;
                const challenge = !normalLanding && (
                  explicit.test(title) || explicit.test(text) ||
                  (queueHost && (waiting.test(title) || waiting.test(text))) ||
                  !!document.querySelector('iframe[src*="captcha"], [class*="captcha"]')
                );
                return JSON.stringify({
                  challenge: normalLanding ? 'NO' : challenge ? 'YES' : 'UNKNOWN',
                  path: location.hostname + location.pathname,
                  observation: normalLanding ? 'demo released to product landing'
                    : challenge ? 'visible challenge indicator'
                    : 'no definitive challenge indicator'
                });
              } catch (e) {
                return JSON.stringify({
                  challenge:'UNKNOWN', path:'UNAVAILABLE', observation:String(e)
                });
              }
            })();
        """.trimIndent()
    }
}
