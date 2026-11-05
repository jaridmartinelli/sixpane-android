package com.sixpane.app

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Use real screen-coordinate taps and keyboard input on a clean emulator.
 * Test colors are fixtures; navigation, selection and clearing use visible UI.
 */
@RunWith(AndroidJUnit4::class)
class FilteredTabsInstrumentedTest {
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun find(root:View,test:(View)->Boolean):View? {
        if(test(root)&&root.isShown)return root
        if(root is ViewGroup)for(i in 0 until root.childCount){
            find(root.getChildAt(i),test)?.let{return it}
        }
        return null
    }
    private fun click(scenario:ActivityScenario<MainActivity>,description:String){
        var x=-1;var y=-1
        scenario.onActivity{a->
            val v=find(a.window.decorView){
                it.contentDescription?.toString()?.startsWith(description)==true
            }
            assertNotNull("Missing visible "+description,v)
            val xy=IntArray(2);v!!.getLocationOnScreen(xy)
            x=xy[0]+v.width/2;y=xy[1]+v.height/2
        }
        assertTrue("Off-screen "+description,x>=0&&y>=0)
        assertTrue("Tap failed "+description,device.click(x,y))
        device.waitForIdle()
    }
    private fun waitFor(scenario:ActivityScenario<MainActivity>,condition:(MainActivity)->Boolean){
        for(i in 0 until 60){
            var okay=false
            scenario.onActivity{a->okay=condition(a)}
            if(okay)return
            Thread.sleep(300)
        }
        fail("Expected device state never appeared")
    }

    @Test fun fourteenBlueWindowsUseThreeSixSlotPagesAcrossOriginalWorkspaces(){
        assertTrue("MULTI_PROFILE needed to test isolated live browsers",
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            waitFor(scenario){a->a.auditPanes().size==6&&a.auditPanes().all{it.second!=null}}
            var gridWidth=0;var gridHeight=0
            scenario.onActivity{a->
                val card=a.auditPanes().first().second!!
                gridWidth=card.width;gridHeight=card.height
            }
            assertTrue(gridWidth>0&&gridHeight>0)
            click(scenario,"Create workspace 2")
            waitFor(scenario){a->a.auditPanes().size==12&&a.auditPanes().all{it.second!=null}}
            click(scenario,"Create workspace 3")
            waitFor(scenario){a->a.auditPanes().size==18&&a.auditPanes().all{it.second!=null}}
            val blue=FramePalette.colors[1]
            val originalIds=mutableListOf<String>()
            val profileNames=mutableMapOf<String,String>()
            val originalWebViews=mutableMapOf<String,android.webkit.WebView>()
            scenario.onActivity{a->
                a.store.spaces.flatMap{it.panes}.forEach{
                    originalIds.add(it.id);profileNames[it.id]=it.profile
                }
                a.auditPanes().forEach{(p,card)->originalWebViews[p.id]=card!!.webView}
                a.recolorWindows(originalIds.take(14).toSet(),blue)
            }
            // Enter the color through the same field the user sees.
            var x=0;var y=0
            scenario.onActivity{a->
                val field=find(a.window.decorView){
                    it is EditText && it.hint?.toString()?.contains("Filter URL")==true
                }
                assertNotNull("Top filter field unavailable",field)
                val xy=IntArray(2);field!!.getLocationOnScreen(xy)
                x=xy[0]+field.width/2;y=xy[1]+field.height/2
            }
            assertTrue(device.click(x,y))
            device.executeShellCommand("input text Blue")
            waitFor(scenario){a->a.store.filterQuery=="Blue"}
            device.pressBack() // Dismiss keyboard so grid cells are comparable in size.
            waitFor(scenario){a->
                find(a.window.decorView){
                    it.contentDescription?.toString()?.startsWith("Filtered tab 3, 2 windows")==true
                }!=null
            }
            scenario.onActivity{a->
                assertEquals(3,a.store.spaces.size) // Source workspaces remain intact.
                assertEquals(originalIds,a.store.spaces.flatMap{it.panes}.map{it.id})
                assertNull(find(a.window.decorView){
                    it.contentDescription?.toString()?.startsWith("Create workspace")==true
                })
                assertNull(find(a.window.decorView){
                    it.contentDescription?.toString()?.startsWith("Workspace 1;")==true
                })
            }
            click(scenario,"Filtered tab 3, 2 windows")
            Thread.sleep(500)
            scenario.onActivity{a->
                assertEquals(18,a.auditPanes().size)
                val visible=a.auditPanes().filter{
                    it.second!=null&&it.second!!.x>=0f&&it.second!!.x<10000f
                }
                assertEquals(2,visible.size)
                assertEquals(originalIds.slice(12..13),visible.map{it.first.id})
                visible.forEach{
                    assertEquals(gridWidth,it.second!!.width)
                    assertEquals(gridHeight,it.second!!.height)
                }
                // Filtering must never replace a WebView or its named cookie profile.
                a.auditPanes().forEach{(p,card)->
                    assertEquals(profileNames[p.id],p.profile)
                    assertSame(originalWebViews[p.id],card!!.webView)
                }
            }
            click(scenario,"Lock filtered group 3")
            scenario.onActivity{a->
                assertTrue(a.store.spaces.flatMap{it.panes}.slice(12..13).all{it.locked})
                assertTrue(a.store.spaces.flatMap{it.panes}.take(12).none{it.locked})
            }
            click(scenario,"Unlock filtered group 3")
            click(scenario,"Filtered tab 1, 6 windows")
            // Tap clear in the actual top bar; original tabs must reappear.
            var clearX=-1;var clearY=-1
            scenario.onActivity{a->
                val clear=find(a.window.decorView){
                    it is TextView && it.text?.toString()=="×"
                }
                assertNotNull("Filter clear control missing",clear)
                val xy=IntArray(2);clear!!.getLocationOnScreen(xy)
                clearX=xy[0]+clear.width/2;clearY=xy[1]+clear.height/2
            }
            assertTrue(device.click(clearX,clearY))
            waitFor(scenario){a->a.store.filterQuery==""}
            scenario.onActivity{a->
                assertEquals(18,a.auditPanes().size)
                assertEquals(originalIds,a.store.spaces.flatMap{it.panes}.map{it.id})
                assertNotNull(find(a.window.decorView){
                    it.contentDescription?.toString()?.startsWith("Create workspace 4")==true
                })
                assertNull(find(a.window.decorView){
                    it.contentDescription?.toString()?.startsWith("Filtered tab")==true
                })
            }
        }finally{scenario.close()}
    }
}
