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
    private fun openTopMenu(){
        val menu=device.wait(Until.findObject(By.descContains("Window selection and batch actions")),8_000L)
        assertNotNull("Universal top-right dots missing",menu)
        menu.click()
        device.waitForIdle()
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
            openTopMenu()
            clickLabel("Select multiple")
            openTopMenu()
            clickLabel("Select all visible")
            openTopMenu()
            clickLabel("Lock selected")
            scenario.onActivity{a->assertTrue(a.store.active().panes.all{it.locked})}
            openTopMenu()
            clickLabel("Unlock selected")
            scenario.onActivity{a->assertTrue(a.store.active().panes.none{it.locked})}
            openTopMenu()
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
