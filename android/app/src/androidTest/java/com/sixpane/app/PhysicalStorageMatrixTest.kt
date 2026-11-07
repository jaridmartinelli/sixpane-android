package com.sixpane.app

import android.net.Uri
import android.os.Build
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

/**
 * R6 physical-device QA test. Loads https://example.com as an HTTPS document, then
 * evaluates synthetic storage probes in that WebView's real JavaScript context.
 * No file:///android_asset loads and no service-worker registration/assertions.
 * This checks cookies, localStorage, sessionStorage and IndexedDB separately.
 *
 * QA-only profile names and test keys are unique per invocation. No Queue-it
 * token, challenge, or real session cookie is read, copied, or modified.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalStorageMatrixTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private data class Case(val profile: String, val key: String, val marker: String)

    @Test
    fun liveHttpsNamedProfilesKeepStorageSeparateAcrossWebViewRecreation() {
        assertEquals("Run against the separate QA application only", QA_PACKAGE, context.packageName)
        assertFalse("A physical Android device is required", isEmulator())
        assertTrue("MULTI_PROFILE is required", WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))

        val nonce = UUID.randomUUID().toString().replace("-", "")
        val a = Case("qa-r6-a-" + UUID.randomUUID(), "a" + nonce, UUID.randomUUID().toString())
        val b = Case("qa-r6-b-" + UUID.randomUUID(), "b" + nonce, UUID.randomUUID().toString())
        assertNotEquals(a.profile, b.profile)
        assertNotEquals(a.marker, b.marker)

        // First encounter: no test markers may be present in either profile.
        withLivePage(a.profile) { web ->
            val seeded = probe(web, a, b, "seed")
            assertNoOwnValues("A initial", seeded.getJSONObject("before"))
            assertOthersAbsent("A initial", seeded.getJSONObject("before"))
            assertOwned("A seeded", seeded.getJSONObject("after"), a.marker, true)
            assertOthersAbsent("A seeded", seeded.getJSONObject("after"))
            checkNativeCookie(web, a)
            val sameWebView = probe(web, a, b, "read").getJSONObject("after")
            assertOwned("A same WebView", sameWebView, a.marker, true)
            assertOthersAbsent("A same WebView", sameWebView)
        }
        withLivePage(b.profile) { web ->
            val seeded = probe(web, b, a, "seed")
            assertNoOwnValues("B initial", seeded.getJSONObject("before"))
            assertOthersAbsent("B initial (no A leakage)", seeded.getJSONObject("before"))
            assertOwned("B seeded", seeded.getJSONObject("after"), b.marker, true)
            assertOthersAbsent("B seeded", seeded.getJSONObject("after"))
            checkNativeCookie(web, b)
        }

        // Rebuild the WebView with the SAME named profile. sessionStorage belongs
        // to the old browsing context, unlike cookies/localStorage/IndexedDB.
        withLivePage(a.profile) { web ->
            val restored = probe(web, a, b, "read").getJSONObject("after")
            assertOwned("A reconstructed", restored, a.marker, false)
            assertOthersAbsent("A reconstructed", restored)
            checkNativeCookie(web, a)
        }
        withLivePage(b.profile) { web ->
            val restored = probe(web, b, a, "read").getJSONObject("after")
            assertOwned("B reconstructed", restored, b.marker, false)
            assertOthersAbsent("B reconstructed", restored)
            checkNativeCookie(web, b)
        }
    }

    private fun withLivePage(profile: String, block: (WebView) -> Unit) {
        val pageFinished = CountDownLatch(1)
        val lastUrl = AtomicReference<String>("(not loaded)")
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(context)
            // The first operation on each WebView is binding its named profile.
            WebViewCompat.setProfile(web, profile)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            assertEquals(profile, WebViewCompat.getProfile(web).name)
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view === web) {
                        lastUrl.set(url ?: "(unknown)")
                        pageFinished.countDown()
                    }
                }
            }
            web.loadUrl(DEMO_URL)
        }
        try {
            assertTrue("Example.com did not finish loading within 25s; last URL: " + lastUrl.get(),
                pageFinished.await(25, TimeUnit.SECONDS))
            val actual = AtomicReference<String>()
            instrumentation.runOnMainSync { actual.set(web.url) }
            assertEquals("Example.com left its HTTPS origin; do not inject into another site: " + actual.get(),
                DEMO_HOST, Uri.parse(actual.get()).host)
            block(web)
        } finally {
            instrumentation.runOnMainSync {
                runCatching { WebViewCompat.getProfile(web).cookieManager.flush() }
                web.stopLoading()
                web.destroy()
            }
        }
    }

    private fun probe(web: WebView, mine: Case, other: Case, operation: String): JSONObject {
        assertEquals("QA test must remain on example.com origin",
            DEMO_HOST, Uri.parse(web.url).host)
        val injected = PROBE_JS
            .replace("__MINE__", JSONObject.quote(mine.key))
            .replace("__OTHER__", JSONObject.quote(other.key))
            .replace("__MARKER__", JSONObject.quote(mine.marker))
            .replace("__MODE__", JSONObject.quote(operation))
        evaluate(web, injected)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            val returned = evaluate(web, "JSON.stringify(window.__sixpaneR6Result || null)")
            val decoded = JSONTokener(returned).nextValue() as? String
            if (decoded != null && decoded != "null") {
                val result = JSONObject(decoded)
                assertFalse("Live origin blocked QA probe: " + result.optString("error"), result.has("error"))
                assertEquals(DEMO_HOST, result.optString("host"))
                return result
            }
            Thread.sleep(150)
        }
        fail("Injected live-page probe never completed; URL=" + web.url)
        throw AssertionError("unreachable")
    }

    private fun evaluate(web: WebView, script: String): String {
        val callback = CountDownLatch(1)
        val value = AtomicReference<String>("null")
        instrumentation.runOnMainSync {
            web.evaluateJavascript(script) {
                value.set(it ?: "null")
                callback.countDown()
            }
        }
        assertTrue("evaluateJavascript callback timed out", callback.await(5, TimeUnit.SECONDS))
        return value.get()
    }

    private fun checkNativeCookie(web: WebView, case: Case) {
        val cookies = AtomicReference<String>("")
        instrumentation.runOnMainSync {
            val manager = WebViewCompat.getProfile(web).cookieManager
            cookies.set(manager.getCookie(DEMO_ORIGIN).orEmpty())
            manager.flush()
        }
        val expected = "sixpane_r6_" + case.key + "=" + case.marker
        assertTrue("Profile CookieManager missing synthetic QA cookie: " + case.profile,
            cookies.get().split(";").any { it.trim() == expected })
    }

    private fun assertNoOwnValues(label: String, data: JSONObject) {
        for (field in listOf("cookieOwn", "localOwn", "sessionOwn", "idbOwn"))
            assertNull(label + " must start empty: " + field, value(data, field))
    }

    private fun assertOthersAbsent(label: String, data: JSONObject) {
        for (field in listOf("cookieOther", "localOther", "sessionOther", "idbOther"))
            assertNull(label + " crossed profile boundary: " + field, value(data, field))
    }

    private fun assertOwned(label: String, data: JSONObject, marker: String, sameContext: Boolean) {
        assertEquals(label + " cookie", marker, value(data, "cookieOwn"))
        assertEquals(label + " localStorage", marker, value(data, "localOwn"))
        assertEquals(label + " IndexedDB", marker, value(data, "idbOwn"))
        if (sameContext) {
            assertEquals(label + " sessionStorage", marker, value(data, "sessionOwn"))
        } else {
            assertNull(label + " sessionStorage must not survive WebView destruction",
                value(data, "sessionOwn"))
        }
    }

    private fun value(data: JSONObject, key: String): String? =
        if (data.isNull(key)) null else data.getString(key)

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.contains("generic", true) ||
            Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.MODEL.contains("sdk_gphone", true)

    companion object {
        private const val QA_PACKAGE = "com.sixpane.app.qa.wvab"
        private const val DEMO_HOST = "example.com"
        private const val DEMO_ORIGIN = "https://example.com/"
        private const val DEMO_URL = "https://example.com/"

        // Executed by evaluateJavascript AFTER HTTPS navigation. This is not a
        // script resource and does not register a service worker on a third-party
        // origin. Only nonce-prefixed synthetic QA storage is modified.
        private val PROBE_JS = """
            (() => {
              const mine = __MINE__, other = __OTHER__;
              const marker = __MARKER__, mode = __MODE__;
              window.__sixpaneR6Result = null;
              const ck = key => 'sixpane_r6_' + key;
              const lk = key => 'sixpane.r6.' + key;
              const cookie = name => {
                const item = document.cookie.split(';').map(x => x.trim())
                  .find(x => x.startsWith(name + '='));
                return item ? item.slice(name.length + 1) : null;
              };
              (async () => {
                if (location.protocol !== 'https:' || location.hostname !== 'example.com')
                  throw Error('Not on requested live HTTPS origin');
                const db = await new Promise((resolve, reject) => {
                  const request = indexedDB.open('sixpane-physical-r6-qa', 1);
                  request.onupgradeneeded = () => request.result.createObjectStore('values');
                  request.onsuccess = () => resolve(request.result);
                  request.onerror = () => reject(request.error || Error('IndexedDB open failed'));
                  request.onblocked = () => reject(Error('IndexedDB blocked'));
                });
                const readDb = key => new Promise((resolve, reject) => {
                  let found = null;
                  const tx = db.transaction('values', 'readonly');
                  const request = tx.objectStore('values').get(key);
                  request.onsuccess = () => { found = request.result ?? null; };
                  tx.oncomplete = () => resolve(found);
                  tx.onerror = () => reject(tx.error || Error('IndexedDB read failed'));
                  tx.onabort = () => reject(tx.error || Error('IndexedDB aborted'));
                });
                const writeDb = (key, value) => new Promise((resolve, reject) => {
                  const tx = db.transaction('values', 'readwrite');
                  tx.objectStore('values').put(value, key);
                  tx.oncomplete = () => resolve();
                  tx.onerror = () => reject(tx.error || Error('IndexedDB write failed'));
                  tx.onabort = () => reject(tx.error || Error('IndexedDB aborted'));
                });
                const snapshot = async () => ({
                  cookieOwn: cookie(ck(mine)), cookieOther: cookie(ck(other)),
                  localOwn: localStorage.getItem(lk(mine)),
                  localOther: localStorage.getItem(lk(other)),
                  sessionOwn: sessionStorage.getItem(lk(mine)),
                  sessionOther: sessionStorage.getItem(lk(other)),
                  idbOwn: await readDb(mine),
                  idbOther: await readDb(other)
                });
                try {
                  const before = await snapshot();
                  if (mode === 'seed') {
                    document.cookie = ck(mine) + '=' + marker +
                      '; Path=/; Secure; SameSite=Lax; Max-Age=3600';
                    localStorage.setItem(lk(mine), marker);
                    sessionStorage.setItem(lk(mine), marker);
                    await writeDb(mine, marker);
                  }
                  const after = await snapshot();
                  window.__sixpaneR6Result = {
                    host: location.hostname, mode, before, after
                  };
                } finally { db.close(); }
              })().catch(error => {
                window.__sixpaneR6Result = {
                  host: location.hostname, error: String(error)
                };
              });
            })();
        """.trimIndent()
    }
}
