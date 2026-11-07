package app.tessera.browser

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONTokener
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Captures every screen, menu and state as numbered screenshots ("tour-NN-name.png")
 * so the design can be handed over with pictures. Numbers follow the design prompt.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTour {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val shots = File(inst.targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }

    private fun <T> main(block: () -> T): T { val r = AtomicReference<T>(); inst.runOnMainSync { r.set(block()) }; return r.get() }
    private fun shell(cmd: String) = inst.uiAutomation.executeShellCommand(cmd).close()
    private fun shot(name: String) {
        inst.waitForIdleSync(); Thread.sleep(2500)
        inst.uiAutomation.takeScreenshot()?.let { b -> File(shots, "tour-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    private fun clear(act: MainActivity) {
        main { Sheet.dismissAll(); act.closePopup() }
        shell("input keyevent 111") // ESC hides any keyboard
        Thread.sleep(600)
    }
    private fun js(act: MainActivity, p: Pane, script: String): String {
        val latch = CountDownLatch(1); val out = AtomicReference("")
        inst.runOnMainSync { act.hubRef().view(p.id)!!.evaluateJavascript(script) { v ->
            out.set(runCatching { JSONTokener(v).nextValue()?.toString() ?: "" }.getOrDefault(v ?: "")); latch.countDown() } }
        latch.await(15, TimeUnit.SECONDS); return out.get()
    }
    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        inst.sendPointerSync(MotionEvent.obtain(t, t + 80, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun everyScreen() {
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        main { (inst.targetContext.applicationContext as TesseraApp).store.state.onboarded = true }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            lateinit var act: MainActivity; sc.onActivity { act = it }
            val end = SystemClock.uptimeMillis() + 30_000
            while (main { act.hubRef().liveCount() } < 6 && SystemClock.uptimeMillis() < end) Thread.sleep(250)
            val st = main { act.stateRef() }
            val ws = main { st.current() }
            val sites = listOf("https://example.com", "https://www.wikipedia.org", "https://news.ycombinator.com", "", "https://example.org", "")
            main {
                st.filter = PaneFilter(); st.expandedPaneId = null
                ws.panes.forEachIndexed { i, p -> p.locked = false; p.color = -1; if (sites[i].isNotEmpty()) act.hubRef().navigate(p, sites[i]) }
                ws.panes[0].color = 0; ws.panes[1].color = 1; ws.panes[2].color = 2; ws.panes[4].color = 5
                ws.panes[2].locked = true
                if (st.bookmarks.none { it.url == "https://www.wikipedia.org/" })
                    st.bookmarks.add(0, Bookmark("https://www.wikipedia.org/", "Wikipedia"))
                act.refreshAll()
            }
            Thread.sleep(7000)
            clear(act)

            // 1–4: main screen, command bar, panes, enlarged pane
            main { st.activePaneId = ws.panes[0].id; act.refreshAll() }; shot("01-grid-and-command-bar")
            main { act.toggleExpandActive() }; shot("05-enlarged-pane-with-strip")
            main { act.toggleExpandActive() }; Thread.sleep(400)

            // 3: address field focused with suggestions (Paste & go + bookmarks)
            main { (act.getSystemService(android.content.ClipboardManager::class.java)).setPrimaryClip(android.content.ClipData.newPlainText("u", "https://example.net")) }
            main { act.openAddress(ws.panes[0]) }; shot("03-address-field-with-suggestions")
            clear(act)

            // 6: pane menu (unlocked), main ⋮ menu, locked pane menu
            main { act.openPaneMenu(ws.panes[0]) }; shot("06-pane-menu")
            clear(act)
            main { act.openMainMenu() }; shot("06-main-menu-with-all-panes")
            clear(act)
            main { act.openPaneMenu(ws.panes[2]) }; shot("13-pane-menu-locked")
            clear(act)

            // 6: confirmation card
            main { act.endSessions(listOf(ws.panes[1], ws.panes[2], ws.panes[4])) }; shot("06-confirm-end-sessions")
            clear(act)

            // 7: workspace screen and menu
            main { if (st.workspaces.size < 2) act.addWorkspace(ws) }; Thread.sleep(1500)
            main { act.switchWorkspace(ws.id) }; Thread.sleep(600)
            main { act.openWorkspaces() }; shot("07-workspace-screen")
            main { act.openWorkspaceMenu(ws) }; shot("14-workspace-menu")
            clear(act)
            main { act.requestCloseWorkspace(st.workspaces.last()) }; shot("06-confirm-close-workspace")
            clear(act)
            main { act.refreshAll() } // overview closes via back in real use
            sc.onActivity { it.onBackPressed() }; Thread.sleep(500)

            // 8: multi-select
            main { act.enterSelection(ws.panes[0].id); act.onSelectToggle(ws.panes[1].id); act.onSelectToggle(ws.panes[4].id) }
            shot("08-selection-bar")
            main { act.openSelectionMenu() }; shot("08-selection-menu")
            clear(act)
            main { act.bulkColor() }; shot("08-colour-picker-card")
            clear(act)
            main { act.bulkOpen(ws.panes.take(3)) }; shot("08-open-in-selected")
            clear(act)
            main { act.exitSelection() }

            // 9: filters
            main { act.openFilterSheet() }; shot("09-filter-page")
            clear(act)
            main { st.filter = PaneFilter(setOf(0, 1), "example"); act.refreshAll() }; shot("09-filter-chips-and-result")
            main { st.filter = PaneFilter(emptySet(), "nothing-matches-this"); act.refreshAll() }; shot("14-no-panes-match")
            main { act.clearFilters() }; Thread.sleep(600)

            // 10: pop-up (locked pane link) and keyboard while typing in a site
            val lockedP = ws.panes[2]
            main { act.hubRef().view(lockedP.id)!!.loadDataWithBaseURL("https://tour.tessera.test/",
                "<meta name=viewport content='width=device-width'><a href='https://example.org/' target='_blank' style='display:block;height:3000px;font-size:28px'>Open in a new tab</a>",
                "text/html", "utf-8", null) }
            Thread.sleep(2500)
            val (lx, ly) = main { val v = act.cardRef(lockedP.id)!!; val l = IntArray(2); v.getLocationOnScreen(l); (l[0] + v.width / 2f) to (l[1] + v.height / 2f) }
            tap(lx, ly); Thread.sleep(4000); shot("10-popup-locked-pane")
            clear(act)

            val chat = ws.panes[5]
            main { act.hubRef().view(chat.id)!!.loadDataWithBaseURL("https://chat.tessera.test/",
                "<meta name=viewport content='width=device-width,initial-scale=1'><style>body{margin:0;height:100vh;font-family:sans-serif}#bar{position:fixed;left:0;right:0;bottom:0;height:64px;display:flex}#box{flex:1;font-size:20px}#send{width:90px}</style><p style='padding:12px'>Chat page</p><div id=bar><input id=box placeholder='Ask anything'><button id=send>Send</button></div>",
                "text/html", "utf-8", null) }
            Thread.sleep(2500)
            main { act.onPaneTouched(chat.id); act.toggleExpandActive() }; Thread.sleep(1200)
            val r = js(act, chat, "(function(){var r=document.getElementById('box').getBoundingClientRect();return (r.left+r.width/2)+','+(r.top+r.height/2)+','+innerWidth})()").split(',').map { it.toFloat() }
            val (kx, ky) = main { val wv = act.hubRef().view(chat.id)!!; val l = IntArray(2); wv.getLocationOnScreen(l)
                val ratio = wv.width * ((wv.parent as? android.view.View)?.scaleX ?: 1f) / r[2]; (l[0] + r[0] * ratio) to (l[1] + r[1] * ratio) }
            tap(kx, ky); Thread.sleep(3000); shot("10-keyboard-typing-in-site")
            clear(act)
            main { if (st.expandedPaneId != null) act.toggleExpandActive() }

            // 14: cookie editor and its edit card, diagnostics, health check
            main { CookieEditor(act, ws.panes[0]).show() }; shot("14-cookie-editor")
            main { CookieEditor(act, ws.panes[0]).edit("example_cookie", "value123") }; shot("14-cookie-edit-card")
            clear(act)
            main { Diagnostics.isolationCheck(act) }; Thread.sleep(5000); shot("14-isolation-and-health")
            clear(act)

            // 12 + 14: settings, help, welcome
            main { act.openSettings() }; shot("12-settings")
            clear(act)
            main { act.explainSessions() }; shot("14-how-sessions-work")
            clear(act)
            main { st.onboarded = false; act.showOnboarding() }; shot("14-welcome-page")
            clear(act)
            main { st.onboarded = true }

            // 17: notifications
            shell("cmd statusbar expand-notifications"); Thread.sleep(2000); shot("17-notifications")
            shell("cmd statusbar collapse")

            // 14: unsupported-WebView screen (replaces the whole UI, so it goes last)
            main { act.showUnsupported() }; shot("14-unsupported-webview")
        }
    }
}
