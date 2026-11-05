package com.sixpane.app

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An isolated emulator user-journey test: uses visible controls and input injection.
 * Never run this suite against an installed user app or its real profiles.
 */
@RunWith(AndroidJUnit4::class)
class UserJourneyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun clickLabel(label:String){
        val item=device.wait(Until.findObject(By.text(label)),8_000L)
        assertNotNull("Expected menu item: $label",item)
        item.click()
        device.waitForIdle()
    }
    private fun findControl(v:View,prefix:String):View? {
        if(v.contentDescription?.toString()?.startsWith(prefix)==true && v.isShown)return v
        if(v is ViewGroup)for(i in 0 until v.childCount){
            findControl(v.getChildAt(i),prefix)?.let{return it}
        }
        return null
    }
    /** Inject a real screen tap using the native View's on-screen bounds.
      * Accessibility queries can omit the top bar while multiple WebViews load.
      */
    private fun tapControl(scenario:ActivityScenario<MainActivity>,prefix:String){
        var x=-1;var y=-1
        scenario.onActivity{activity->
            val target=findControl(activity.window.decorView,prefix)
                ?:if(prefix=="Window selection and batch actions")
                    findControl(activity.window.decorView,"Window batch actions:") else null
            assertNotNull("On-screen control not found: "+prefix,target)
            val loc=IntArray(2);target!!.getLocationOnScreen(loc)
            x=loc[0]+target.width/2
            y=loc[1]+target.height/2
        }
        assertTrue("Control outside screen: "+prefix+" "+x+","+y,x>=0&&y>=0)
        assertTrue("Touch injection failed: "+prefix,device.click(x,y))
        device.waitForIdle()
    }
    private fun openTopMenu(scenario:ActivityScenario<MainActivity>) {
        tapControl(scenario,"Window selection and batch actions")
    }
    private fun findExpand(v:View):View? {
        if(v is CornerIcon&&v.kind==CornerIcon.Kind.EXPAND)return v
        if(v is ViewGroup)for(i in 0 until v.childCount){
            findExpand(v.getChildAt(i))?.let{return it}
        }
        return null
    }
    private fun tapView(scenario:ActivityScenario<MainActivity>,id:String){
        var x=-1;var y=-1
        scenario.onActivity{ activity->
            val card=activity.auditPanes().firstOrNull{it.first.id==id}?.second
                ?:error("Pane not allocated")
            val view=findExpand(card)?:error("Missing expand control")
            val loc=IntArray(2);view.getLocationOnScreen(loc)
            x=loc[0]+view.width/2
            y=loc[1]+view.height/2
        }
        assertTrue("Expand tap target off screen ($x, $y)",x>=0&&y>=0)
        assertTrue("Expand tap failed",device.click(x,y))
        device.waitForIdle()
    }


    @Test fun holdingNumberBadgeOpensPaneMenuWithoutHijackingWebView(){
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            var id=""
            var ready=false
            repeat(30){
                scenario.onActivity{ a->
                    val first=a.auditPanes().firstOrNull()
                    id=first?.first?.id.orEmpty()
                    ready=first?.second!=null
                }
                if(ready)return@repeat
                Thread.sleep(230)
            }
            assertTrue("Window 1 failed to start",ready)
            var x=0;var y=0
            scenario.onActivity{a->
                val first=a.auditPanes().first{it.first.id==id}.second!!
                val badge=first.menuAnchor
                assertTrue("Number badge must own a long-press handler",badge.isLongClickable)
                val loc=IntArray(2);badge.getLocationOnScreen(loc)
                x=loc[0]+badge.width/2
                y=loc[1]+badge.height/2
            }
            assertTrue("Holding number failed",device.swipe(x,y,x,y,130))
            val menu=device.wait(Until.findObject(By.text("Window 1")),5_000L)
            assertNotNull("Number long press did not open window controls",menu)
            device.pressBack()
            device.waitForIdle()
            scenario.onActivity{ a->
                assertEquals(6,a.store.active().panes.size)
                assertTrue(a.auditPanes().all{it.second==null ||
                    it.second!!.webView.settings.javaScriptEnabled})
            }
        } finally {scenario.close()}
    }


    @Test fun tappingBlankPaneAndRightStackMiniaturesSwapsFocusWithoutSessionReset(){
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            var first="";var second="";var ready=false
            for(attempt in 0 until 45){
                scenario.onActivity{activity->
                    val entries=activity.auditPanes()
                    ready=entries.size==6&&entries.all{it.second!=null}
                    if(ready){first=entries[0].first.id;second=entries[1].first.id}
                }
                if(ready)break
                Thread.sleep(220)
            }
            assertTrue("Six panes did not become available",ready)
            fun clickCenter(id:String){
                var x=-1;var y=-1
                scenario.onActivity{activity->
                    val pane=activity.auditPanes().first{it.first.id==id}.second!!
                    val point=IntArray(2);pane.getLocationOnScreen(point)
                    x=point[0]+pane.width/2;y=point[1]+pane.height/2
                }
                assertTrue("Pane center is outside the device",x>=0&&y>=0)
                assertTrue("Touch injection failed",device.click(x,y))
                Thread.sleep(500)
            }
            val originals=mutableMapOf<String,android.webkit.WebView>()
            scenario.onActivity{activity->
                activity.auditPanes().forEach{(pane,card)->originals[pane.id]=card!!.webView}
                assertNull(activity.store.focusId)
            }
            clickCenter(first) // Empty page: single tap expands without pressing arrows.
            scenario.onActivity{activity->
                assertEquals(first,activity.store.focusId)
                assertTrue(activity.auditPanes().first{it.first.id==second}.second!!.compact)
            }
            clickCenter(second) // Right stack: any tap switches expanded browser.
            scenario.onActivity{activity->
                assertEquals(second,activity.store.focusId)
                assertTrue(activity.auditPanes().first{it.first.id==first}.second!!.compact)
                assertEquals(6,activity.auditPanes().size)
                assertTrue(activity.auditPanes().all{(pane,card)->
                    originals[pane.id]===card!!.webView
                })
            }
            // Tab 1 plus only +2: no unused 3-7 tabs should be rendered.
            assertNotNull(device.wait(Until.findObject(By.desc("Create workspace 2")),3_000L))
            assertNull(device.findObject(By.desc("Create workspace 7")))
        } finally{scenario.close()}
    }


    @Test fun settingsLayoutAndWorkspaceTabLockRespondToRealUserTaps(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            var columns=2
            scenario.onActivity{a->columns=a.store.columns}
            openTopMenu(scenario)
            clickLabel(if(columns==2)"Layout: 2 × 3 → 3 × 2" else "Layout: 3 × 2 → 2 × 3")
            scenario.onActivity{a->assertNotEquals(columns,a.store.columns)}
            openTopMenu(scenario)
            clickLabel(if(columns==2)"Layout: 3 × 2 → 2 × 3" else "Layout: 2 × 3 → 3 × 2")
            scenario.onActivity{a->assertEquals(columns,a.store.columns)}
            openTopMenu(scenario)
            clickLabel("Settings")
            clickLabel("Appearance")
            clickLabel("Light")
            scenario.onActivity{a->assertTrue(a.store.lightMode)}
            openTopMenu(scenario)
            clickLabel("Settings")
            clickLabel("Appearance")
            clickLabel("Dark")
            scenario.onActivity{a->assertFalse(a.store.lightMode)}
            var x=-1;var y=-1;var wasLocked=false
            scenario.onActivity{a->
                wasLocked=a.store.active().locked
                val tab=findControl(a.window.decorView,"Workspace 1")
                assertNotNull("Workspace 1 tab absent",tab)
                val location=IntArray(2);tab!!.getLocationOnScreen(location)
                x=location[0]+tab.width/2
                y=location[1]+tab.height/2
            }
            assertTrue(device.swipe(x,y,x,y,130))
            scenario.onActivity{a->assertEquals(!wasLocked,a.store.active().locked)}
            // The second hold restores original state so no other journey is affected.
            assertTrue(device.swipe(x,y,x,y,130))
            scenario.onActivity{a->assertEquals(wasLocked,a.store.active().locked)}
        }finally{scenario.close()}
    }


    @Test fun paneZoomButtonsChangeAndResetScaleThroughRealWindowMenu(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            var x=0;var y=0;var id=""
            scenario.onActivity{a->
                val entry=a.auditPanes().first()
                id=entry.first.id
                val badge=entry.second?.menuAnchor
                assertNotNull("Pane number absent",badge)
                val point=IntArray(2);badge!!.getLocationOnScreen(point)
                x=point[0]+badge.width/2;y=point[1]+badge.height/2
            }
            assertTrue(device.click(x,y))
            clickLabel("Zoom")
            val plus=device.wait(Until.findObject(By.desc("Zoom in 10 percent")),4_000L)
            assertNotNull("Zoom + unavailable",plus)
            plus.click()
            scenario.onActivity{a->assertEquals(110,a.store.active().panes.first{it.id==id}.zoom)}
            val reset=device.wait(Until.findObject(By.desc("Reset zoom to 100 percent")),4_000L)
            assertNotNull("Zoom reset unavailable",reset)
            reset.click()
            scenario.onActivity{a->assertEquals(100,a.store.active().panes.first{it.id==id}.zoom)}
            device.pressBack()
        }finally{scenario.close()}
    }

    @Test fun userFlowGridBatchLocksFocusSearchBookmarksAndWorkspace(){
        assertTrue("Emulator WebView lacks MULTI_PROFILE; no credible 6-pane user test is possible",
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val expected=WebSettings.getDefaultUserAgent(instrumentation.targetContext)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            var complete=false
            repeat(40){
                scenario.onActivity{ activity ->
                    complete=activity.auditPanes().size==6 &&
                        activity.auditPanes().all{it.second!=null}
                }
                if(complete)return@repeat
                Thread.sleep(220)
            }
            assertTrue("All six WebViews did not start",complete)
            var first=""
            scenario.onActivity{a->
                val entries=a.auditPanes()
                assertEquals(6,entries.size)
                assertEquals(6,entries.map{it.first.profile}.distinct().size)
                assertTrue(entries.all{it.second!=null})
                assertTrue(entries.all{it.second!!.webView.settings.userAgentString==expected})
                first=entries.first().first.id
            }

            // Operate the actual permanent top-right menu like a user.
            openTopMenu(scenario)
            clickLabel("Select multiple")
            openTopMenu(scenario)
            clickLabel("Select all visible")
            openTopMenu(scenario)
            clickLabel("Lock selected")
            scenario.onActivity{a->assertTrue(a.store.active().panes.all{it.locked})}
            openTopMenu(scenario)
            clickLabel("Unlock selected")
            scenario.onActivity{a->assertTrue(a.store.active().panes.none{it.locked})}
            openTopMenu(scenario)
            clickLabel("Exit selection")

            // Tap the native expand control, then use the actual address bar and bookmark star.
            tapView(scenario,first)
            scenario.onActivity{a->assertEquals(first,a.store.focusId)}
            val url=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5_000L)
            assertNotNull("Focused URL field not shown",url)
            val testUrl="https://appassets.androidplatform.net/assets/isolation_test.html"
            url.text=testUrl
            device.pressEnter()
            Thread.sleep(1500)
            scenario.onActivity{a->
                assertEquals(testUrl,a.store.active().panes.first{it.id==first}.url)
                assertEquals(expected,a.auditPanes().first{it.first.id==first}.second!!.webView.settings.userAgentString)
            }
            val star=device.wait(Until.findObject(By.desc("Bookmark this page in this browser")),5_000L)
            assertNotNull("Bookmark star missing",star)
            star.click()
            scenario.onActivity{a->assertTrue(a.store.bookmarkedInPane(first,testUrl))}

            val screenshot=File(instrumentation.targetContext.getExternalFilesDir(null),"sixpane-user-journey.png")
            assertTrue("Screenshot capture failed",device.takeScreenshot(screenshot))
            assertTrue(screenshot.length()>0)

            tapView(scenario,first)
            scenario.onActivity{a->assertNull(a.store.focusId)}
            val filter=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5_000L)
            assertNotNull("Grid URL/color filter missing",filter)
            filter.text="Blue"
            scenario.onActivity{a->
                assertEquals("Blue",a.store.filterQuery)
                assertEquals(6,a.auditPanes().size)
                assertEquals(6,a.auditPanes().count{it.second!=null})
            }
            filter.text=""
            scenario.onActivity{a->assertEquals("",a.store.filterQuery)}

            // Create a separate workspace through the visible Chrome-style tab.
            val plus=device.wait(Until.findObject(By.descContains("Create workspace 2")),5_000L)
            assertNotNull("Workspace add tab missing",plus)
            plus.click()
            Thread.sleep(1500)
            scenario.onActivity{a->
                assertEquals(2,a.store.spaces.size)
                assertEquals(12,a.store.spaces.flatMap{it.panes}.map{it.profile}.distinct().size)
                assertTrue(a.store.spaces.first().panes.first().bookmarks.contains(testUrl))
                assertTrue(a.store.spaces.last().panes.all{it.bookmarks.isEmpty()})
                assertTrue(a.auditPanes().all{it.second==null||
                    it.second!!.webView.settings.userAgentString==expected})
            }
        }finally{scenario.close()}
    }
}
