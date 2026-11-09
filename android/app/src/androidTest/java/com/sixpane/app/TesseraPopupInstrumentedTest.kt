package com.sixpane.app

import android.content.Context
import android.os.Build
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

/** Run ONLY on ephemeral emulator; the fixture deletes no real phone data. */
@RunWith(AndroidJUnit4::class)
class TesseraPopupInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private val target="https://appassets.androidplatform.net/assets/isolation_test.html"

    @Before fun isolatedFixture(){
        val ctx=instrumentation.targetContext
        val hw=Build.HARDWARE.lowercase()
        val fp=Build.FINGERPRINT.lowercase()
        assertTrue("Never reset a real user's browser profiles",
            hw.contains("ranchu")||hw.contains("goldfish")||
                fp.contains("generic")||fp.contains("sdk_gphone"))
        assertEquals("com.sixpane.app.preview.filterview",ctx.packageName)
        assertTrue(ctx.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun dismissWelcome(){
        device.wait(Until.findObject(By.text("Got it")),6000L)?.click()
        device.waitForIdle()
    }
    private fun waitForPopupUrl(scenario:ActivityScenario<MainActivity>):Boolean{
        for(i in 0 until 35){
            var loaded=false
            scenario.onActivity{host->
                loaded=host.tesseraActiveCard()?.popupForTest()?.url?.startsWith(target)==true
            }
            if(loaded)return true
            Thread.sleep(200)
        }
        return false
    }

    @Test fun scriptedSignInPopupOpensFullScreenAndMayOpenInOriginalUnlockedPane(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var firstProfile:String
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                firstId=pane.id
                firstProfile=pane.profile
                val card=host.windowCard(pane.id)
                assertNotNull(card)
                card!!.webView.evaluateJavascript(
                    "window.open('"+target+"','_blank')",null)
            }
            assertNotNull("Script-opened window must show full-screen pop-up",
                device.wait(Until.findObject(By.desc("Close pop-up")),10000L))
            assertTrue("Pop-up must load local test page in same named session",
                waitForPopupUrl(scenario))
            val open=device.findObject(By.text("Open in pane"))
            assertNotNull(open)
            open.click()
            device.waitForIdle()
            // WebView navigates asynchronously (and may try a shallow entry
            // candidate first). Do not mistake an in-flight URL for a final one.
            var observed=false
            for(i in 0 until 50){
                scenario.onActivity{host->
                    val pane=host.store.active().panes.first()
                    observed=pane.url==target &&
                        host.windowCard(firstId)?.webView?.url==target
                }
                if(observed)break
                Thread.sleep(200)
            }
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                assertEquals(firstId,pane.id)
                assertEquals(firstProfile,pane.profile)
                assertTrue("Original pane must finally navigate to the popup URL",observed)
                assertNull(host.windowCard(firstId)?.popupForTest())
            }
        }finally{scenario.close()}
    }

    @Test fun lockedPanePopupCannotReplaceItsOriginalPage(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var firstProfile:String
            lateinit var original:String
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                firstId=pane.id;firstProfile=pane.profile;original=pane.url
                pane.locked=true
                host.updateWindowLocks()
                host.windowCard(pane.id)!!.webView.evaluateJavascript(
                    "window.open('"+target+"','_blank')",null)
            }
            val close=device.wait(Until.findObject(By.desc("Close pop-up")),10000L)
            assertNotNull("Locked pane must open new-window request in a popup",close)
            assertTrue(waitForPopupUrl(scenario))
            val reason=device.findObject(By.text("Unlock pane to open this page"))
            assertNotNull("Locked pop-up must show the refusal reason",reason)
            val open=device.findObject(By.text("Open in pane"))
            assertNotNull(open)
            open.click()
            device.waitForIdle()
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                assertEquals(firstId,pane.id)
                assertEquals(firstProfile,pane.profile)
                assertEquals(original,pane.url)
                assertTrue(pane.locked)
                assertNotNull(host.windowCard(firstId)?.popupForTest())
            }
            close.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertNull("Closing popup must release its WebView",
                    host.windowCard(firstId)?.popupForTest())
            }
        }finally{scenario.close()}
    }
}
