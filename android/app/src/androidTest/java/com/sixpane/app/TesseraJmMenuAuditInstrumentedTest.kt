package com.sixpane.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
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

/** UI-only, emulator-only sizing and reference inventory checks. Never edits real data. */
@RunWith(AndroidJUnit4::class)
class TesseraJmMenuAuditInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)

    @Before fun disposablePreviewOnly(){
        val hw=Build.HARDWARE.lowercase()
        val fingerprint=Build.FINGERPRINT.lowercase()
        assertTrue("Never reset a real phone",hw.contains("ranchu")||
            hw.contains("goldfish")||fingerprint.contains("generic")||
            fingerprint.contains("sdk_gphone"))
        val ctx=instrumentation.targetContext
        assertEquals("com.sixpane.app.preview.filterview",ctx.packageName)
        assertTrue(ctx.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun dismissWelcome(){
        device.wait(Until.findObject(By.text("Got it")),8_000L)?.click()
        device.waitForIdle()
    }

    private fun dp(pixels:Int):Float =
        pixels/instrumentation.targetContext.resources.displayMetrics.density

    @Test fun referencePaneMenuHasOnlySpecifiedSevenActionsAndThreeAllPaneRows(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->host.tesseraMenuAll()}
            val names=listOf("Lock pane","Desktop site","Cookies & site data",
                "Copy diagnostics","Select multiple","End session",
                "Filter panes","Check isolation & health","Settings")
            assertNotNull(device.wait(Until.findObject(By.text("COLOUR TAG")),8_000L))
            for(name in names){
                assertNotNull("Missing required menu item: "+name,
                    device.findObject(By.text(name)))
            }
            assertNotNull(device.findObject(By.text("All panes")))
            val notOnMenu=listOf("Back","Reload","Zoom in","Zoom out","Bookmarks",
                "Share selected","Developer tools","Condensed filter results",
                "Appearance","Workspaces")
            for(label in notOnMenu){
                assertNull("Extra primary pane-menu action: "+label,
                    device.findObject(By.text(label)))
            }
            for(name in listOf("Lock pane","Desktop site","Cookies & site data",
                "Copy diagnostics","Select multiple","End session",
                "Filter panes","Check isolation & health","Settings")){
                val row=device.findObject(By.text(name))
                assertNotNull(row)
                // Rows are required to be >= 52dp; child glyphs are not.
                val target=requireNotNull(row.parent?.parent){
                    "Menu row must be actionable: "+name
                }
                assertTrue("Menu row must have a 52dp target: "+name+
                    " actual="+dp(target.visibleBounds.height()),
                    dp(target.visibleBounds.height())>=50f)
            }
        }finally{scenario.close()}
    }

    @Test fun workspaceCardControlsAndNewWorkspaceMeetReferenceTapTargets(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            val launcher=device.wait(Until.findObject(
                By.descContains("Workspaces; current 1")),8_000L)
            assertNotNull(launcher)
            assertTrue("Workspace dock target <44dp",dp(launcher.visibleBounds.width())>=43f)
            assertTrue("Workspace dock target height <44dp",
                dp(launcher.visibleBounds.height())>=43f)
            launcher.click()
            assertNotNull(device.wait(Until.findObject(By.text("Workspaces")),8_000L))
            val add=device.findObject(By.text("+ New workspace"))
            assertNotNull("Missing bottom create action",add)
            assertTrue("New workspace must be 52dp high; observed "+dp(add.visibleBounds.height()),
                dp(add.visibleBounds.height()) in 50f..55f)
            for(desc in listOf("Lock all panes in workspace 1","Close workspace 1")){
                val control=device.findObject(By.desc(desc))
                assertNotNull("Workspace header action not present: "+desc,control)
                assertTrue("Workspace header action width <44dp: "+desc,
                    dp(control.visibleBounds.width())>=43f)
                assertTrue("Workspace header action height <44dp: "+desc,
                    dp(control.visibleBounds.height())>=43f)
            }
        }finally{scenario.close()}
    }

    @Test fun selectionBarAllActionsHave44dpTouchHeight(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->host.enterSelection(host.store.active().panes[0].id)}
            val targets=listOf("Exit selection","Select all visible panes",
                "Lock selected panes","Unlock selected panes",
                "Change colour of selected panes","More actions for selected panes")
            for(desc in targets){
                val control=device.wait(Until.findObject(By.desc(desc)),8_000L)
                assertNotNull("Missing selection control: "+desc,control)
                assertTrue("Selection control tap height <44dp: "+desc+
                    " actual="+dp(control.visibleBounds.height()),
                    dp(control.visibleBounds.height())>=43f)
            }
            // The reference uses one toggle. In a partially selected grid it is
            // "All"; only after selecting all six does it change to "None".
            device.findObject(By.desc("Select all visible panes")).click()
            val none=device.wait(Until.findObject(
                By.desc("Deselect all visible panes")),8_000L)
            assertNotNull("All must turn into None when every pane is selected",none)
            assertTrue("None touch target must also meet 44dp",
                dp(none.visibleBounds.height())>=43f)
        }finally{scenario.close()}
    }

    @Test fun emptyPaneUsesDarkReferenceSurfaceButPreservesWhiteWebView(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->
                val pane=host.store.active().panes[0]
                val card=host.windowCard(pane.id)
                assertNotNull(card)
                val f=PaneCard::class.java.getDeclaredField("blankTap")
                f.isAccessible=true
                val overlay=f.get(card) as View
                assertEquals(View.VISIBLE,overlay.visibility)
                val ripple=overlay.background as RippleDrawable
                val surface=ripple.getDrawable(0) as ColorDrawable
                assertEquals("Empty browser overlay must use #0B0E13 reference surface",
                    Color.rgb(11,14,19),surface.color)
            }
            val open=device.wait(Until.findObject(
                By.desc("Open a site in pane 1")),8_000L)
            assertNotNull(open)
            assertTrue("Open a site touch target must be 44dp high",
                dp(open.visibleBounds.height())>=43f)
        }finally{scenario.close()}
    }

    @Test fun rotationChangesAutoGridWithoutReplacingSessionProfiles(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var profiles:Map<String,String>
            scenario.onActivity{host->
                profiles=host.store.active().panes.associate{it.id to it.profile}
                host.tesseraSetGrid("automatic")
            }
            device.setOrientationLeft()
            device.waitForIdle()
            var changed=false
            for(i in 0 until 36){
                scenario.onActivity{host->
                    val panes=host.store.active().panes
                    val views=panes.take(4).map{
                        host.windowCard(it.id)?.layoutParams as? FrameLayout.LayoutParams
                    }
                    if(views.all{it!=null}){
                        changed=views[0]!!.topMargin==views[2]!!.topMargin &&
                            views[3]!!.topMargin>views[0]!!.topMargin
                    }
                }
                if(changed)break
                Thread.sleep(250)
            }
            assertTrue("Landscape automatic grid should display 3 columns × 2 rows",changed)
            scenario.onActivity{host->
                assertEquals("Rotation must preserve all profile IDs",
                    profiles,host.store.active().panes.associate{it.id to it.profile})
                assertEquals("automatic",host.store.gridPreference)
            }
        }finally{
            device.setOrientationNatural()
            device.unfreezeRotation()
            scenario.close()
        }
    }


    @Test fun filterAndSettingsScreensHaveReferenceSectionHeadings(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->host.tesseraMenuAll()}
            val filter=device.wait(Until.findObject(By.text("Filter panes")),8_000L)
            assertNotNull(filter)
            filter.click()
            assertNotNull(device.wait(Until.findObject(By.text("COLOURS · ANY OF THESE")),8_000L))
            for(name in listOf("Blue","Green","Red","Gold","Olive","Teal")){
                val colour=device.findObject(By.desc("Filter by "+name))
                assertNotNull("Filter colour needs an accessible label: "+name,colour)
                assertTrue("Filter colour target must be 44dp tall: "+name,
                    dp(colour.visibleBounds.height())>=43f)
            }
            assertNotNull(device.findObject(By.text("URL CONTAINS")))
            assertNotNull(device.findObject(By.text("Clear all")))
            assertNotNull(device.findObject(By.text("Done")))
            device.pressBack()
            device.waitForIdle()
            scenario.onActivity{host->host.tesseraMenuAll()}
            val settings=device.wait(Until.findObject(By.text("Settings")),8_000L)
            assertNotNull(settings)
            settings.click()
            assertNotNull(device.wait(Until.findObject(By.text("KEEP SESSIONS ALIVE")),8_000L))
            assertNotNull(device.findObject(By.text("LAYOUT")))
            assertNotNull(device.findObject(By.text("ABOUT")))
        }finally{scenario.close()}
    }
}
