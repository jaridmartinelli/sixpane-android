package com.sixpane.app

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Disposable emulator only: never run this test on a user's installed app.
 *
 * Exercises REAL WebViews with 42 distinct QA-only profiles. It never navigates
 * an actual Queue-it event, accesses existing SIXPANE pane profiles, spoofs
 * fingerprints or reads real queue tokens.
 *
 * This verifies sequential profile isolation and data continuity on WebView
 * reconstruction, NOT 42 concurrent background renderers or server-side
 * visitor classification.
 */
@RunWith(AndroidJUnit4::class)
class SessionIntegrityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val origin = "https://appassets.androidplatform.net/"
    private val page = origin + "assets/session_integrity_probe.html"
    private val loader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()

    private data class ProfileCase(val name: String, val marker: String)

    private fun newCase(): ProfileCase =
        ProfileCase("qa-integrity-" + UUID.randomUUID(), UUID.randomUUID().toString())

    private fun isDisposableEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", true) ||
            Build.MODEL.contains("sdk_gphone", true) ||
            Build.PRODUCT.contains("sdk", true)

    private fun inspect(profile: ProfileCase, returning: Boolean) {
        lateinit var view: WebView
        val finished = CountDownLatch(1)
        val url = page + "?marker=" + profile.marker
        instrumentation.runOnMainSync {
            view = WebView(context)
            // First operation on the WebView: assign the named, test-only profile.
            WebViewCompat.setProfile(view, profile.name)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.databaseEnabled = true
            assertEquals(profile.name, WebViewCompat.getProfile(view).name)
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    webView: WebView, request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                override fun onPageFinished(webView: WebView?, loadedUrl: String?) {
                    if (webView === view && loadedUrl?.startsWith(page) == true) finished.countDown()
                }
            }
            view.loadUrl(url)
        }

        try {
            assertTrue("Profile page did not load: " + profile.name.takeLast(8),
                finished.await(20, TimeUnit.SECONDS))
            val data = pollProbe(view)
            assertFalse("Probe error: " + data.optString("error"), data.has("error"))
            assertEquals(profile.marker, data.getString("marker"))
            assertEquals(profile.marker, data.getString("cookieAfter"))
            assertEquals(profile.marker, data.getString("localAfter"))
            assertEquals(profile.marker, data.getString("idbAfter"))

            if (returning) {
                assertEquals("Cookie lost when WebView was reconstructed",
                    profile.marker, data.getString("cookieBefore"))
                assertEquals("localStorage lost when WebView was reconstructed",
                    profile.marker, data.getString("localBefore"))
                assertEquals("IndexedDB lost when WebView was reconstructed",
                    profile.marker, data.getString("idbBefore"))
            } else {
                assertTrue("Cookie already present in a fresh QA profile", data.isNull("cookieBefore"))
                assertTrue("localStorage already present in a fresh QA profile", data.isNull("localBefore"))
                assertTrue("IndexedDB already present in a fresh QA profile", data.isNull("idbBefore"))
            }

            instrumentation.runOnMainSync {
                val manager = WebViewCompat.getProfile(view).cookieManager
                val cookies = manager.getCookie(url).orEmpty()
                assertTrue("Profile cookie differs from document.cookie",
                    cookies.split(';').any { it.trim() == "sixpane_integrity_probe=" + profile.marker })
                manager.flush()
            }
        } finally {
            instrumentation.runOnMainSync { view.stopLoading(); view.destroy() }
        }
    }

    private fun pollProbe(view: WebView): JSONObject {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            val done = CountDownLatch(1)
            val result = AtomicReference<JSONObject?>(null)
            val failure = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                view.evaluateJavascript("JSON.stringify(window.sixpaneIntegrityProbe || null)") { raw ->
                    try {
                        val decoded = JSONTokener(raw).nextValue() as? String
                        if (!decoded.isNullOrBlank() && decoded != "null")
                            result.set(JSONObject(decoded))
                    } catch (e: Exception) {
                        failure.set(e.message ?: e.javaClass.simpleName)
                    } finally {
                        done.countDown()
                    }
                }
            }
            assertTrue("JavaScript callback timed out", done.await(5, TimeUnit.SECONDS))
            if (failure.get() != null) fail("Probe JS decoding failed: " + failure.get())
            result.get()?.let { return it }
            Thread.sleep(100)
        }
        fail("Cookie/localStorage/IndexedDB probe never completed")
        throw AssertionError("unreachable")
    }

    @Test fun fortyTwoQaProfilesKeepCookiesLocalStorageAndIndexedDbAcrossRecreation() {
        // These profiles are written to a throwaway emulator installation ONLY.
        // Never run automatically on the user's OnePlus Open or installed package.
        assumeTrue("Disposable emulator required", isDisposableEmulator())
        assertTrue("MULTI_PROFILE is mandatory; shared-cookie fallback is forbidden",
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val cases = List(42) { newCase() }
        assertEquals(42, cases.map { it.name }.toSet().size)
        assertEquals(42, cases.map { it.marker }.toSet().size)

        // A single attached or detached WebView is retained per step, to avoid
        // misleading claims that a 42-concurrent stress test has been performed.
        cases.forEach { inspect(it, returning = false) }
        cases.forEach { inspect(it, returning = true) }
    }
}
