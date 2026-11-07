package app.tessera.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Journey checks with REAL touch input on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class JourneyTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val base = "https://journey.tessera.test/"

    private fun <T> main(block: () -> T): T { val r = AtomicReference<T>(); inst.runOnMainSync { r.set(block()) }; return r.get() }
    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (cond()) return; Thread.sleep(200) }
        throw AssertionError("Timed out: $what")
    }
    private fun js(act: MainActivity, p: Pane, script: String): String {
        val latch = CountDownLatch(1); val out = AtomicReference("")
        inst.runOnMainSync { act.hubRef().view(p.id)!!.evaluateJavascript(script) { v ->
            out.set(runCatching { JSONTokener(v).nextValue()?.toString() ?: "null" }.getOrDefault(v ?: "null")); latch.countDown() } }
        assertTrue(latch.await(15, TimeUnit.SECONDS)); return out.get()
    }
    private fun load(act: MainActivity, p: Pane, html: String) {
        main { act.hubRef().view(p.id)!!.loadDataWithBaseURL(base, html, "text/html", "utf-8", null) }
        waitFor(20_000, "page") { js(act, p, "document.getElementById('ready')?'y':'n'") == "y" }
    }
    private fun center(act: MainActivity, p: Pane): Pair<Float, Float> = main {
        val v = act.cardRef(p.id)!!; val loc = IntArray(2); v.getLocationOnScreen(loc)
        (loc[0] + v.width / 2f) to (loc[1] + v.height / 2f)
    }
    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        inst.sendPointerSync(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, x, y, 0))
    }
    private fun swipeDown(x: Float, y: Float, dist: Float) {
        val t = SystemClock.uptimeMillis()
        inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        // A deliberate human pull (~0.9s), giving the page time to answer the touch-down probe.
        for (i in 1..45) { inst.sendPointerSync(MotionEvent.obtain(t, t + i * 20L, MotionEvent.ACTION_MOVE, x, y + dist * i / 45, 0)); Thread.sleep(20) }
        inst.sendPointerSync(MotionEvent.obtain(t, t + 920, MotionEvent.ACTION_UP, x, y + dist, 0))
    }

    private fun launch(block: (ActivityScenario<MainActivity>, MainActivity) -> Unit) {
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var act: MainActivity; sc.onActivity { act = it }
            waitFor(30_000, "sessions") { main { act.hubRef().liveCount() } >= 6 }
            // Close any emulator system dialog (e.g. "app isn't responding") that would swallow the taps.
            inst.uiAutomation.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS").close()
            main { Sheet.dismissAll(); act.stateRef().filter = PaneFilter(); act.stateRef().expandedPaneId = null
                act.stateRef().current().panes.forEach { it.locked = false }; act.refreshAll() }
            Thread.sleep(800)
            block(sc, act)
        }
    }

    @Test fun newTabLinkOpensPopupAndKeepsPanePage() = launch { _, act ->
        val p = main { act.stateRef().current().panes[0] }
        load(act, p, "<a id=ready href='https://example.org/' target='_blank' style='display:block;height:3000px;font-size:40px'>open new tab</a>")
        val (x, y) = center(act, p); tap(x, y)
        waitFor(15_000, "pop-up shown") { main { act.popupShowing } }
        // getUrl() reports about:blank for loadDataWithBaseURL pages, so check the page content itself.
        main { act.closePopup() }
        assertEquals("pane page must stay", "y", js(act, p, "document.getElementById('ready')?'y':'n'"))
        Log.i("TesseraTest", "JOURNEY popup: shown=true panePageKept=true")
    }

    @Test fun fileUploadOpensPicker() = launch { _, act ->
        val p = main { act.stateRef().current().panes[1] }
        load(act, p, "<input id=ready type=file style='display:block;width:100%;height:3000px'>")
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") },
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        val monitorAny = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_CHOOSER), Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        inst.addMonitor(monitor); inst.addMonitor(monitorAny)
        val (x, y) = center(act, p); tap(x, y)
        waitFor(10_000, "file picker requested") { monitor.hits + monitorAny.hits > 0 }
        Log.i("TesseraTest", "JOURNEY upload: pickerRequested=true")
        inst.removeMonitor(monitor); inst.removeMonitor(monitorAny)
    }

    @Test fun pullToRefreshOnlyForRealPageTopAndNeverWhenLocked() = launch { _, act ->
        val panes = main { act.stateRef().current().panes }
        val normal = panes[2]; val inner = panes[3]; val lockedP = panes[4]
        val longPage = "<div id=ready style='height:3000px;font-size:30px'>long page</div>"
        val innerPage = "<style>html,body{margin:0;height:100%;overflow:hidden}</style>" +
            "<div id=ready style='height:100%;overflow-y:auto'><div style='height:3000px'>inner scroller</div></div>"
        load(act, normal, longPage); load(act, inner, innerPage); load(act, lockedP, longPage)
        main { lockedP.locked = true; act.refreshAll() }
        // The inner box is scrolled down: a pull must scroll it back, not reload the page.
        js(act, inner, "document.getElementById('ready').scrollTop=800;1")
        listOf(normal, inner, lockedP).forEach { js(act, it, "window.__mark=1;1") }
        listOf(normal, inner, lockedP).forEach { p ->
            val (x, y) = center(act, p); swipeDown(x, y - 150f, 600f); Thread.sleep(2500)
        }
        val reloaded = listOf(normal, inner, lockedP).map { js(act, it, "String(window.__mark)") != "1" }
        Log.i("TesseraTest", "JOURNEY pull: normalReloaded=${reloaded[0]} innerReloaded=${reloaded[1]} lockedReloaded=${reloaded[2]}")
        assertTrue("normal page should reload on pull", reloaded[0])
        assertTrue("scrolled inner box must NOT reload", !reloaded[1])
        assertTrue("locked pane must NOT reload", !reloaded[2])
        main { lockedP.locked = false; act.refreshAll() }
    }

    @Test fun typingInPageKeepsItsSendButtonAboveKeyboard() = launch { _, act ->
        val p = main { act.stateRef().current().panes[2] }
        load(act, p, "<meta name=viewport content='width=device-width,initial-scale=1'><style>body{margin:0;height:100vh}#bar{position:fixed;left:0;right:0;bottom:0;height:64px;display:flex}" +
            "#box{flex:1;font-size:20px}#send{width:90px}</style><div id=ready>chat</div>" +
            "<div id=bar><input id=box placeholder='Ask anything'><button id=send>Send</button></div>")
        main { act.onPaneTouched(p.id); act.toggleExpandActive() }
        Thread.sleep(1200)
        // Tap exactly where the page reports its text box is (CSS px -> screen px).
        val rect = js(act, p, "(function(){var r=document.getElementById('box').getBoundingClientRect();return (r.left+r.width/2)+','+(r.top+r.height/2)+','+innerWidth})()")
        val (cx, cy, iw) = rect.split(',').map { it.toFloat() }
        val (x, y) = main {
            // Page CSS px -> screen px using the page's real width on screen.
            val wv = act.hubRef().view(p.id)!!; val l = IntArray(2); wv.getLocationOnScreen(l)
            val ratio = wv.width * (wv.parent as? android.view.View)?.scaleX.let { it ?: 1f } / iw
            (l[0] + cx * ratio) to (l[1] + cy * ratio)
        }
        Log.i("TesseraTest", "JOURNEY keyboard: tapping css=($cx,$cy) screen=($x,$y)")
        tap(x, y)
        try {
            waitFor(15_000, "keyboard up while typing in the page") {
                js(act, p, "document.activeElement&&document.activeElement.id") == "box" &&
                    main { act.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
            }
        } catch (e: AssertionError) {
            Log.i("TesseraTest", "JOURNEY keyboard: FAILED focus=${js(act, p, "String(document.activeElement&&document.activeElement.id)")} " +
                "ime=${main { act.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) }}")
            throw e
        }
        Thread.sleep(1500) // keyboard slide + pane resize settle
        val visible = js(act, p, "(function(){var r=document.getElementById('send').getBoundingClientRect();return r.bottom<=innerHeight&&r.top>=0?'y':'n'})()")
        Log.i("TesseraTest", "JOURNEY keyboard: sendVisible=$visible innerHeight=${js(act, p, "String(innerHeight)")}")
        assertEquals("page's Send button must be visible above the keyboard", "y", visible)
        main { act.toggleExpandActive() }
    }

    /** With "Display over other apps" allowed, pages keep full speed after the task is swiped away. */
    @Test fun recentsSwipeKeepsFullSpeed() {
        inst.uiAutomation.executeShellCommand("appops set ${inst.targetContext.packageName} SYSTEM_ALERT_WINDOW allow").close()
        Thread.sleep(500)
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        val sc = ActivityScenario.launch(MainActivity::class.java)
        lateinit var act: MainActivity; sc.onActivity { act = it }
        waitFor(30_000, "sessions") { main { act.hubRef().liveCount() } >= 6 }
        val hub = main { act.hubRef() }
        val p = main { act.stateRef().current().panes[0] }
        load(act, p, "<div id=ready>t</div>")
        js(act, p, "window.__t=0;setInterval(function(){window.__t++},100);1")
        sc.onActivity { it.finishAndRemoveTask() }
        Thread.sleep(20_000)
        val latch = CountDownLatch(1); val out = AtomicReference("?")
        inst.runOnMainSync { hub.view(p.id)?.evaluateJavascript("String(window.__t)+'/'+document.visibilityState") { out.set(it); latch.countDown() } ?: latch.countDown() }
        latch.await(15, TimeUnit.SECONDS)
        Log.i("TesseraTest", "JOURNEY recents: after20s ticks/visibility=${out.get()} (200 = full speed) parked=${main { hub.parked }} service=${KeepAliveService.running}")
        runCatching { sc.close() }
        val parts = out.get().trim('"').split('/')
        assertEquals("page visibility after closing from Recents", "visible", parts.getOrNull(1))
        assertTrue("timers slowed after closing from Recents: ${parts[0]}", (parts[0].toDoubleOrNull() ?: 0.0) >= 120)
    }
}
