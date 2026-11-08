package com.sixpane.app

import android.content.Context
import android.os.Build
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated emulator-only checks of Claude handoff menu arrangement and filter geometry. */
@RunWith(AndroidJUnit4::class)
class TesseraLayoutInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)

    @Before fun resetFixture(){
        val ctx=instrumentation.targetContext
        val hw=Build.HARDWARE.lowercase()
        val fp=Build.FINGERPRINT.lowercase()
        assertTrue("Never run destructive fixture reset on a physical phone",
            hw.contains("ranchu")||hw.contains("goldfish")||
                fp.contains("generic")||fp.contains("sdk_gphone"))
        assertEquals("com.sixpane.app.preview.filterview",ctx.packageName)
        assertTrue(ctx.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun dismissWelcome(){
        device.wait(Until.findObject(By.text("Got it")),6_000L)?.click()
        device.waitForIdle()
    }

    @Test fun paneMenuHasInlineColourChoicesSwitchAndCorrectGroupOrder(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            var ready=false
            for(i in 0 until 30){
                scenario.onActivity{host->ready=host.auditPanes().firstOrNull()?.second!=null}
                if(ready)break
                Thread.sleep(300)
            }
            assertTrue("First browser view must exist",ready)
            scenario.onActivity{host->host.tesseraMenuAll()}
            val heading=device.wait(Until.findObject(By.textContains("Workspace 1")),8_000L)
            assertNotNull("Pane menu missing",heading)
            val inline=device.wait(Until.findObject(By.text("COLOUR TAG")),5_000L)
            assertNotNull("Reference requires colour choices on this menu, not a second dialog",inline)
            for(name in listOf("None","Blue","Green","Red","Gold","Olive","Teal")){
                assertNotNull("Missing inline colour swatch: "+name,
                    device.findObject(By.desc("Set pane colour to "+name)))
            }
            val desktop=device.findObject(By.text("Desktop site"))
            assertNotNull(desktop)
            assertNotNull("Desktop site needs a switch on the same row",
                device.findObject(By.clazz("android.widget.Switch")))
            val lock=device.findObject(By.text("Lock pane"))
            val select=device.findObject(By.text("Select multiple"))
            val end=device.findObject(By.text("End session"))
            val filter=device.findObject(By.text("Filter panes"))
            assertNotNull(lock);assertNotNull(select);assertNotNull(end);assertNotNull(filter)
            assertTrue(inline.visibleBounds.top<lock.visibleBounds.top)
            assertTrue(lock.visibleBounds.top<desktop.visibleBounds.top)
            assertTrue(desktop.visibleBounds.top<select.visibleBounds.top)
            assertTrue(select.visibleBounds.top<end.visibleBounds.top)
            assertTrue(end.visibleBounds.top<filter.visibleBounds.top)
        }finally{scenario.close()}
    }

    @Test fun workspaceCardLockAndCloseAreInItsHeader(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            val workspace=device.wait(Until.findObject(By.text("▣1")),10_000L)
            assertNotNull("Workspace dock button unavailable",workspace)
            workspace.click()
            assertNotNull(device.wait(Until.findObject(By.text("Workspaces")),8_000L))
            val lock=device.findObject(By.desc("Lock all panes in workspace 1"))
            val close=device.findObject(By.desc("Close workspace 1"))
            assertNotNull("Workspace card must expose lock in its header",lock)
            assertNotNull("Workspace card must expose close in its header",close)
            assertTrue("Header controls should share a row",
                kotlin.math.abs(lock.visibleBounds.centerY()-close.visibleBounds.centerY())<=12)
            val snapshot=device.findObject(By.descContains("Snapshot of pane"))
            if(snapshot!=null){
                assertTrue("Preview belongs below header actions",
                    lock.visibleBounds.bottom<=snapshot.visibleBounds.top)
            }
        }finally{scenario.close()}
    }

    @Test fun touchingACompactPaneSwitchesExpandedFocusWithoutProfileReplacement(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            dismissWelcome()
            lateinit var initial:String
            lateinit var profileIds:Map<String,String>
            var ready=false
            for(i in 0 until 30){
                scenario.onActivity{host->
                    ready=host.auditPanes().take(6).all{it.second!=null}
                }
                if(ready)break
                Thread.sleep(300)
            }
            assertTrue("Six visible panes must exist before focus test",ready)
            scenario.onActivity{host->
                initial=host.store.active().panes.first().id
                profileIds=host.store.spaces.flatMap{it.panes}
                    .associate{it.id to it.profile}
                host.tesseraActivate(initial,true)
                assertEquals(initial,host.store.focusId)
            }
            val target=device.wait(Until.findObject(
                By.desc("Switch window into expanded view")),8_000L)
            assertNotNull("Compact pane needs a dedicated clickable thumbnail",target)
            assertTrue("Thumbnail overlay must accept click",target.isClickable)
            target.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertNotNull("Expanded mode should remain active",host.store.focusId)
                assertNotEquals("Tap on miniature should switch focus",initial,host.store.focusId)
                assertEquals("Focus switch must not replace cookie profile identities",
                    profileIds,host.store.spaces.flatMap{it.panes}
                        .associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }

    @Test fun newWorkspaceDisabledByFilterShowsReasonBelowFooter(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->
                host.tesseraSetFilter("unmatched-fixture.example",emptySet())
            }
            val workspaces=device.wait(Until.findObject(
                By.descContains("Workspaces; current")),8_000L)
            assertNotNull("Workspace screen should remain reachable while filtering",workspaces)
            workspaces.click()
            assertNotNull(device.wait(Until.findObject(By.text("Workspaces")),8_000L))
            val reason=device.findObject(By.text("Clear filters to add a workspace"))
            assertNotNull("Reference displays disabled reason below new workspace button",reason)
            val create=device.findObject(By.descContains("New workspace unavailable:"))
            assertNotNull("Disabled footer needs an accessible reason",create)
            create.click()
            device.waitForIdle()
            scenario.onActivity{host->assertEquals(1,host.store.spaces.size)}
            assertNotNull("Blocked action should leave workspace menu open",
                device.findObject(By.text("Workspaces")))
        }finally{scenario.close()}
    }

    @Test fun filteredCrossWorkspacePanesFillOnlyMatchingSixSlotPositions(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var expected:Set<String>
            lateinit var profiles:Map<String,String>
            scenario.onActivity{host->
                val first=host.store.active()
                val a=first.panes[1]
                host.tesseraNewWorkspace()
                val b=host.store.active().panes[4]
                // Navigate any allocated WebViews rather than mutating their
                // saved URLs while about:blank completion callbacks are pending.
                // A real local asset URL avoids external network variability.
                val urlA="https://appassets.androidplatform.net/assets/isolation_test.html?qa-filter-fixture=a"
                val urlB="https://appassets.androidplatform.net/assets/isolation_test.html?qa-filter-fixture=b"
                host.windowCard(a.id)?.loadAddress(urlA)?:run{a.url=urlA}
                host.windowCard(b.id)?.loadAddress(urlB)?:run{b.url=urlB}
                host.persist()
                expected=setOf(a.id,b.id)
                profiles=host.store.spaces.flatMap{it.panes}.associate{it.id to it.profile}
                host.tesseraSetFilter("qa-filter-fixture",emptySet())
            }
            var actual:Set<String> = emptySet()
            for(i in 0 until 30){
                scenario.onActivity{host->
                    actual=host.auditPanes().filter{(_,card)->
                        if(card==null)return@filter false
                        val lp=card.layoutParams as? FrameLayout.LayoutParams
                        lp!=null && lp.leftMargin>=0 && card.width>0 && card.height>0
                    }.map{it.first.id}.toSet()
                }
                if(actual==expected)break
                Thread.sleep(300)
            }
            assertEquals("Only matching source panes may occupy visible result slots",expected,actual)
            scenario.onActivity{host->
                assertEquals(2,host.store.spaces.size)
                assertEquals(2,host.store.spaces.flatMap{it.panes}.count{
                    it.url.contains("qa-filter-fixture")
                })
                assertTrue(host.tesseraActivePane()?.id in expected)
                assertEquals(profiles,host.store.spaces.flatMap{it.panes}
                    .associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }
}
