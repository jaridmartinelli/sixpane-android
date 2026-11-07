package com.sixpane.app

import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * R17 physical-device QA persistence test. Run seed and readAfterRestart in
 * SEPARATE am instrument invocations, with the mandatory command between them:
 *
 *   adb shell am force-stop com.sixpane.app.qa.wvab
 *
 * A process-unique nonce rejects accidental in-process comparisons. This test
 * uses an app-created SYNTHETIC cookie on the demo origin; it never pretends
 * the cookie was issued or accepted by Queue-it's origin connector.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalAcceptedCookieTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun seed() {
        requirePhysicalQa()
        val marker = UUID.randomUUID().toString()
        withNamedWebView { web ->
            val callback = CountDownLatch(1)
            val accepted = AtomicReference<Boolean?>(null)
            instrumentation.runOnMainSync {
                val manager = WebViewCompat.getProfile(web).cookieManager
                manager.setCookie(
                    DEMO_ORIGIN,
                    COOKIE_NAME + "=" + marker + "; Path=/; Secure; SameSite=Lax; Max-Age=3600"
                ) { wasAccepted ->
                    accepted.set(wasAccepted)
                    callback.countDown()
                }
            }
            assertTrue("QA fixture CookieManager callback timed out", callback.await(10, TimeUnit.SECONDS))
            assertEquals("QA fixture cookie write rejected", true, accepted.get())
            assertCookieValue(web, marker, "Immediately after seeding")
            instrumentation.runOnMainSync {
                WebViewCompat.getProfile(web).cookieManager.flush()
            }
            val expiry = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
            val prefs = context.getSharedPreferences(PREF_NAME, 0)
            assertTrue(
                "Could not persist restart test state",
                prefs.edit()
                    .putString(KEY_MARKER, marker)
                    .putString(KEY_PROCESS, PROCESS_NONCE)
                    .putLong(KEY_EXPIRY, expiry)
                    .commit()
            )
            println("R17 synthetic cookie seeded and flushed. REQUIRED NEXT: " +
                "adb shell am force-stop " + QA_PACKAGE)
        }
    }

    @Test
    fun readAfterRestart() {
        requirePhysicalQa()
        val prefs = context.getSharedPreferences(PREF_NAME, 0)
        val marker = prefs.getString(KEY_MARKER, null)
        val seededProcess = prefs.getString(KEY_PROCESS, null)
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        assertFalse("Run seed in a separate am instrument invocation first", marker.isNullOrBlank())
        assertFalse("Seed process nonce is missing", seededProcess.isNullOrBlank())
        assertNotEquals(
            "No new process was observed. Run the required adb shell am force-stop " + QA_PACKAGE,
            seededProcess, PROCESS_NONCE
        )
        assertTrue("The 1-hour synthetic cookie fixture window expired; rerun seed",
            System.currentTimeMillis() < expiry)

        withNamedWebView { web ->
            assertCookieValue(web, marker!!, "After required process restart")
            // Ordinary real-page navigation and reload must not erase the
            // profile-managed synthetic cookie.
            navigate(web, reload = false)
            assertCookieValue(web, marker, "After HTTPS navigation")
            navigate(web, reload = true)
            assertCookieValue(web, marker, "After page reload")
            instrumentation.runOnMainSync {
                WebViewCompat.getProfile(web).cookieManager.flush()
            }
        }
        println("R17 QA cookie persisted across process restart and reload. " +
            "Real provider-issued post-clearance acceptance remains UNKNOWN.")
    }

    private fun requirePhysicalQa() {
        assertEquals("QA application required", QA_PACKAGE, context.packageName)
        assertFalse("Physical Android device required", isEmulator())
        assertTrue("MULTI_PROFILE required", WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
    }

    private fun withNamedWebView(block: (WebView) -> Unit) {
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(context)
            // First operation: bind the persistent QA profile before settings.
            WebViewCompat.setProfile(web, PROFILE_NAME)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            assertEquals(PROFILE_NAME, WebViewCompat.getProfile(web).name)
        }
        try {
            block(web)
        } finally {
            instrumentation.runOnMainSync {
                runCatching { WebViewCompat.getProfile(web).cookieManager.flush() }
                web.stopLoading()
                web.destroy()
            }
        }
    }

    private fun assertCookieValue(web: WebView, marker: String, phase: String) {
        val cookies = AtomicReference<String>("")
        instrumentation.runOnMainSync {
            cookies.set(WebViewCompat.getProfile(web).cookieManager.getCookie(DEMO_ORIGIN).orEmpty())
        }
        assertTrue(
            phase + ": synthetic QA cookie missing/changed in named profile",
            cookies.get().split(";").any { it.trim() == COOKIE_NAME + "=" + marker }
        )
    }

    private fun navigate(web: WebView, reload: Boolean) {
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view === web) completed.countDown()
                }
            }
            if (reload) web.reload() else web.loadUrl(DEMO_URL)
        }
        assertTrue("Live demo page did not finish navigation within 25s",
            completed.await(25, TimeUnit.SECONDS))
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", true) ||
            Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.MODEL.contains("sdk_gphone", true)

    companion object {
        private const val QA_PACKAGE = "com.sixpane.app.qa.wvab"
        private const val PROFILE_NAME = "qa-r17-physical-accepted-cookie"
        private const val DEMO_ORIGIN = "https://queueitcom.queue-it.net/"
        private const val DEMO_URL = "https://queueitcom.queue-it.net/?c=queueitcom&e=wrdemoproduct"
        private const val COOKIE_NAME = "sixpane_r17_qa_accepted_fixture"
        private const val PREF_NAME = "sixpane_r17_qa_process_check"
        private const val KEY_MARKER = "marker"
        private const val KEY_PROCESS = "seed_process_nonce"
        private const val KEY_EXPIRY = "fixture_expiry"
        private val PROCESS_NONCE = UUID.randomUUID().toString()
    }
}
