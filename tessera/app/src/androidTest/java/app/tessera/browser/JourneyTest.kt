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
        for (i in 1..20) inst.sendPointerSync(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, x, y + dist * i / 20, 0))
        inst.sendPointerSync(MotionEvent.obtain(t, t + 340, MotionEvent.ACTION_UP, x, y + dist, 0))
    }

    private fun launch(block: (ActivityScenario<MainActivity>, MainActivity) -> Unit) {
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var act: MainActivity; sc.onActivity { act = it }
            waitFor(30_000, "sessions") { main { act.hubRef().liveCount() } >= 6 }
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
        assertEquals("pane page must stay", base, main { act.hubRef().view(p.id)!!.url })
        Log.i("TesseraTest", "JOURNEY popup: shown=true paneUrl=${main { act.hubRef().view(p.id)!!.url }}")
        main { act.closePopup() }
    }

    @Test fun fileUploadOpensPicker() = launch { _, act ->
        val p = main { act.stateRef().current().panes[1] }
        load(act, p, "<input id=ready type=file style='display:block;width:100%;height:3000px'>")
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE) },
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
        listOf(normal, inner, lockedP).forEach { js(act, it, "window.__mark=1;1") }
        listOf(normal, inner, lockedP).forEach { p ->
            val (x, y) = center(act, p); swipeDown(x, y - 150f, 600f); Thread.sleep(2500)
        }
        val reloaded = listOf(normal, inner, lockedP).map { js(act, it, "String(window.__mark)") != "1" }
        Log.i("TesseraTest", "JOURNEY pull: normalReloaded=${reloaded[0]} innerReloaded=${reloaded[1]} lockedReloaded=${reloaded[2]}")
        assertTrue("normal page should reload on pull", reloaded[0])
        assertTrue("inner scroller must NOT reload", !reloaded[1])
        assertTrue("locked pane must NOT reload", !reloaded[2])
        main { lockedP.locked = false; act.refreshAll() }
    }

    /** Evidence only: what happens to page timers when the task is swiped away from Recents. */
    @Test fun recentsSwipeEvidence() {
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
        Log.i("TesseraTest", "JOURNEY recents: after20s ticks/visibility=${out.get()} (200 = full speed) service=${KeepAliveService.running}")
        runCatching { sc.close() }
    }
}
