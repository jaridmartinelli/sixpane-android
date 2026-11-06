package app.tessera.browser

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs on a disposable emulator. Proves, with real WebViews:
 *  1. 42 sessions start, each bound to its own unique profile;
 *  2. cookies, localStorage and IndexedDB written in each pane are invisible to the other 41;
 *  3. with the app in the background, pages in the current AND other workspaces keep
 *     running timers at full rate and report themselves visible (not slept/throttled).
 * Also saves UI screenshots for design review.
 */
@RunWith(AndroidJUnit4::class)
class TesseraDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val shots = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun <T> main(block: () -> T): T {
        val r = AtomicReference<T>()
        inst.runOnMainSync { r.set(block()) }
        return r.get()
    }

    private fun waitFor(timeoutMs: Long, what: String, cond: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) { if (cond()) return; Thread.sleep(250) }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** evaluateJavascript on the main thread; returns the decoded JS value as a string. */
    private fun js(act: MainActivity, pane: Pane, script: String): String {
        val latch = CountDownLatch(1)
        val out = AtomicReference("")
        inst.runOnMainSync {
            act.hubRef().view(pane.id)!!.evaluateJavascript(script) { v ->
                out.set(runCatching { JSONTokener(v).nextValue()?.toString() ?: "null" }.getOrDefault(v ?: "null")); latch.countDown()
            }
        }
        assertTrue("JS timed out", latch.await(15, TimeUnit.SECONDS))
        return out.get()
    }

    private fun shot(name: String) {
        // Software-rendered emulator with live pages draws slowly: let frames settle.
        inst.waitForIdleSync(); Thread.sleep(2500)
        val bmp: Bitmap? = inst.uiAutomation.takeScreenshot()
        bmp?.let { File(shots, "$name.png").outputStream().use { o -> it.compress(Bitmap.CompressFormat.PNG, 100, o) } }
    }

    @Test
    fun fortyTwoIsolatedSessionsStayAwakeInBackground() {
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var act: MainActivity
            sc.onActivity { act = it }
            main { act.stateRef().onboarded = true }
            waitFor(30_000, "first workspace") { main { act.hubRef().liveCount() } >= 6 }
            main { repeat(6) { act.addWorkspace() } }
            waitFor(180_000, "42 live sessions") { main { act.hubRef().liveCount() } == 42 }

            val panes = main { act.stateRef().allPanes() }
            assertEquals(42, panes.size)
            assertEquals("profiles must be unique", 42, panes.map { it.profile }.toSet().size)
            main {
                panes.forEach { p ->
                    assertEquals(p.profile, WebViewCompat.getProfile(act.hubRef().view(p.id)!!).name)
                }
            }

            // --- cookie jars ----------------------------------------------------
            val cUrl = "https://cookie-check.tessera.test/"
            val tokens = panes.associate { it.id to UUID.randomUUID().toString() }
            val set = CountDownLatch(42)
            main {
                panes.forEach { p ->
                    ProfileStore.getInstance().getOrCreateProfile(p.profile).cookieManager
                        .setCookie(cUrl, "iso=${tokens[p.id]}; Path=/") { set.countDown() }
                }
            }
            assertTrue(set.await(30, TimeUnit.SECONDS))
            main {
                panes.forEach { p ->
                    val seen = ProfileStore.getInstance().getOrCreateProfile(p.profile).cookieManager.getCookie(cUrl)
                    assertEquals("cookie jar of ${p.id}", "iso=${tokens[p.id]}", seen)
                }
            }

            // --- localStorage + IndexedDB on the SAME origin in all 42 panes ---------
            val html = "<html><body><h1 id=h>storage check</h1></body></html>"
            main { panes.forEach { act.hubRef().view(it.id)!!.loadDataWithBaseURL("https://storage-check.tessera.test/", html, "text/html", "utf-8", null) } }
            waitFor(90_000, "test pages loaded") { panes.all { js(act, it, "document.getElementById('h')?'y':'n'") == "y" } }
            panes.forEachIndexed { i, p ->
                js(act, p, """(function(){localStorage.setItem('who','$i');
                  window.__w='pending';var r=indexedDB.open('isodb',1);
                  r.onupgradeneeded=function(){r.result.createObjectStore('s')};
                  r.onsuccess=function(){var tx=r.result.transaction('s','readwrite');tx.objectStore('s').put('$i','who');tx.oncomplete=function(){window.__w='ok'}};
                  r.onerror=function(){window.__w='err'};return 1})()""")
            }
            waitFor(60_000, "IndexedDB writes") { panes.all { js(act, it, "String(window.__w)") == "ok" } }
            panes.forEachIndexed { i, p ->
                assertEquals("localStorage of pane $i", "$i", js(act, p, "localStorage.getItem('who')"))
                js(act, p, """(function(){window.__r='pending';var r=indexedDB.open('isodb',1);
                  r.onupgradeneeded=function(){r.result.createObjectStore('s')};
                  r.onsuccess=function(){var g=r.result.transaction('s').objectStore('s').get('who');g.onsuccess=function(){window.__r=String(g.result)}};return 1})()""")
            }
            waitFor(60_000, "IndexedDB reads") { panes.all { js(act, it, "String(window.__r)") != "pending" } }
            panes.forEachIndexed { i, p -> assertEquals("IndexedDB of pane $i", "$i", js(act, p, "String(window.__r)")) }

            // --- background: timers keep full rate, pages stay 'visible' ----------------
            panes.forEach { js(act, it, "window.__t=0;setInterval(function(){window.__t++},100);1") }
            sc.onActivity { it.moveTaskToBack(true) }
            Thread.sleep(20_000)
            val current = main { act.stateRef().current().panes.first() }
            val other = main { act.stateRef().workspaces.first().panes[3] }
            listOf(current, other).forEach { p ->
                val ticks = js(act, p, "String(window.__t)").toDouble().toInt()
                val vis = js(act, p, "document.visibilityState")
                android.util.Log.i("TesseraTest", "pane ${p.id} ticks=$ticks visibility=$vis")
                assertEquals("visibility while backgrounded", "visible", vis)
                assertTrue("timer throttled in background: $ticks ticks in 20s", ticks >= 120)
            }
            assertTrue("keep-alive service", KeepAliveService.running)
        }
    }

    @Test
    fun uiTour() {
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var act: MainActivity
            sc.onActivity { act = it }
            main { act.stateRef().onboarded = true }
            waitFor(30_000, "sessions") { main { act.hubRef().liveCount() } >= 6 }
            val ws = main { act.stateRef().current() }
            val sites = listOf("https://example.com", "https://www.wikipedia.org", "https://news.ycombinator.com", "", "https://example.org", "")
            main {
                ws.panes.forEachIndexed { i, p -> if (sites[i].isNotEmpty()) act.hubRef().navigate(p, sites[i]) }
                ws.panes[0].color = 6; ws.panes[1].color = 3; ws.panes[2].color = 1; ws.panes[4].color = 6
                ws.panes[2].locked = true
                act.refreshAll()
            }
            Thread.sleep(6000)
            main { Sheet.dismissAll() }
            shot("01-grid")
            main { act.toggleExpandActive() }; Thread.sleep(800); shot("02-expanded")
            main { act.toggleExpandActive() }; Thread.sleep(600)
            main { act.openPaneMenu(ws.panes[2]) }; shot("03-pane-menu-locked")
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { act.openMainMenu() }; shot("04-main-menu")
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { act.openAddress(act.activePane()) }; shot("05-address")
            main { Sheet.dismissAll() }; Thread.sleep(300)
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { act.enterSelection(ws.panes[0].id); act.onSelectToggle(ws.panes[1].id); act.onSelectToggle(ws.panes[2].id) }; shot("06-selection")
            main { act.exitSelection(); act.stateRef().filter = PaneFilter(setOf(6)); act.refreshAll() }; shot("07-filter-blue")
            main { act.clearFilters(); act.addWorkspace() }; Thread.sleep(1500); shot("08-second-workspace")
            main { act.openWorkspaceMenu(act.stateRef().workspaces[0]) }; shot("09-workspace-menu")
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { act.switchWorkspace(act.stateRef().workspaces[0].id) }
            main { CookieEditor(act, ws.panes[0]).show() }; shot("10-cookies")
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { Diagnostics.isolationCheck(act) }; Thread.sleep(3000); shot("11-isolation-check")
            main { Sheet.dismissAll() }; Thread.sleep(500)
            main { act.openSettings() }; shot("12-settings")
            main { Sheet.dismissAll(); act.openWorkspaces() }; shot("13-workspaces")
        }
    }
}
