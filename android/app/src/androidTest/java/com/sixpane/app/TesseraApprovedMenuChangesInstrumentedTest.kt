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

/** The three user-approved menu changes. Cookie editing is intentionally out of scope. */
@RunWith(AndroidJUnit4::class)
class TesseraApprovedMenuChangesInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)
    private fun dp(px:Int):Float =
        px/instrumentation.targetContext.resources.displayMetrics.density

    @Before fun isolatedPreviewOnly(){
        val hw=Build.HARDWARE.lowercase()
        val fp=Build.FINGERPRINT.lowercase()
        assertTrue("Do not reset a real phone",
            hw.contains("ranchu")||hw.contains("goldfish")||
                fp.contains("generic")||fp.contains("sdk_gphone"))
        val ctx=instrumentation.targetContext
        assertEquals("com.sixpane.app.preview.filterview",ctx.packageName)
        assertTrue(ctx.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun dismissWelcome(){
        device.wait(Until.findObject(By.text("Got it")),8_000L)?.click()
        device.waitForIdle()
    }
    private fun openSettings(){
        val menu=device.wait(Until.findObject(
            By.desc("Pane menu and all panes actions")),8_000L)
        assertNotNull(menu)
        menu.click()
        val settings=device.wait(Until.findObject(By.text("Settings")),8_000L)
        assertNotNull(settings)
        settings.click()
        assertNotNull(device.wait(Until.findObject(
            By.text("KEEP SESSIONS ALIVE")),8_000L))
    }
    private fun openWorkspaceMenu(){
        val control=device.wait(Until.findObject(
            By.descContains("Workspaces; current 1")),8_000L)
        assertNotNull(control)
        control.longClick()
        assertNotNull(device.wait(Until.findObject(By.text("Workspace 1")),8_000L))
    }
    private fun openBulkAddress(){
        val more=device.wait(Until.findObject(
            By.desc("More actions for selected panes")),8_000L)
        assertNotNull(more)
        more.click()
        val row=device.wait(Until.findObject(
            By.text("Open an address in selected")),8_000L)
        assertNotNull(row)
        row.click()
        assertNotNull(device.wait(Until.findObject(
            By.text("Open in selected panes")),8_000L))
    }

    @Test fun settingsOmitsDuplicateDeveloperMenuButKeepsCanonicalDiagnostics(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            openSettings()
            assertNull("No duplicate Developer tools link in Settings",
                device.findObject(By.text("Developer tools")))
            assertNotNull(device.findObject(By.text("How sessions work")))
            assertNull("No cleanup row when there is nothing pending",
                device.findObject(By.textContains("Finish erasing ended sessions (")))
            device.pressBack()
            scenario.onActivity{host->
                host.store.pendingDeletion.add("emulator-retired-profile-fixture")
            }
            openSettings()
            assertNotNull("Retired-profile cleanup must stay discoverable",
                device.wait(Until.findObject(
                    By.text("Finish erasing ended sessions (1 waiting)")),8_000L))
            val cleanup=device.findObject(
                By.text("Finish erasing ended sessions (1 waiting)"))
            assertTrue("Cleanup guidance row must be usable",cleanup.parent?.parent?.isClickable==true)
            device.pressBack()
            val menu=device.wait(Until.findObject(
                By.desc("Pane menu and all panes actions")),8_000L)
            menu.click()
            assertNotNull("Per-pane copy diagnostics must remain available",
                device.wait(Until.findObject(By.text("Copy diagnostics")),8_000L))
            assertNotNull("Health report remains in the All panes section",
                device.findObject(By.text("Check isolation & health")))
        }finally{scenario.close()}
    }

    @Test fun workspaceMenuHidesInapplicableLockRowsAndKeepsRemainingActions(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            openWorkspaceMenu()
            assertNotNull(device.findObject(By.text("Lock all 6 panes")))
            assertNull("Unlocked workspace does not offer Unlock all",
                device.findObject(By.text("Unlock all panes")))
            assertNotNull(device.findObject(By.text("New workspace to the right")))
            assertNotNull(device.findObject(By.text("Close workspace")))
            device.findObject(By.text("Lock all 6 panes")).click()
            scenario.onActivity{host->
                assertTrue(host.store.active().panes.all{it.locked})
            }
            openWorkspaceMenu()
            assertNull("Fully locked workspace does not offer Lock all",
                device.findObject(By.text("Lock all 6 panes")))
            assertNotNull(device.findObject(By.text("Unlock all panes")))
            device.findObject(By.text("Unlock all panes")).click()
            scenario.onActivity{host->
                val panes=host.store.active().panes
                assertTrue(panes.none{it.locked})
                panes[0].locked=true
                host.updateWindowLocks()
            }
            openWorkspaceMenu()
            assertNotNull("Partial state can be locked in full",
                device.findObject(By.text("Lock all 6 panes")))
            assertNotNull("Partial state can be unlocked in full",
                device.findObject(By.text("Unlock all panes")))
        }finally{scenario.close()}
    }

    @Test fun bulkAddressIsFullScreenWithCancelOpenAndValidation(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->host.enterSelection(host.store.active().panes[0].id)}
            openBulkAddress()
            val cancel=device.findObject(By.text("Cancel"))
            val open=device.findObject(By.text("Open"))
            val input=device.findObject(By.desc("Address or search for selected panes"))
            assertNotNull(cancel);assertNotNull(open);assertNotNull(input)
            val height=instrumentation.targetContext.resources.displayMetrics.heightPixels
            assertTrue("The bottom actions should belong to a full-screen page",
                cancel.visibleBounds.top>height*.67)
            assertTrue("Cancel and Open targets should be at least 52dp tall",
                dp(cancel.visibleBounds.height())>=50f &&
                    dp(open.visibleBounds.height())>=50f)
            open.click()
            assertNotNull("Empty input must keep the user on the page",
                device.wait(Until.findObject(By.text("Open in selected panes")),3_000L))
            scenario.onActivity{host->
                assertTrue(host.store.active().panes.all{it.url=="about:blank"})
            }
            cancel.click()
            assertNull("Cancel closes bulk address entry",
                device.wait(Until.findObject(By.text("Open in selected panes")),1_000L))
            scenario.onActivity{host->
                assertEquals("Cancel must preserve selection",1,host.tesseraSelected().size)
                assertTrue(host.store.active().panes.all{it.url=="about:blank"})
            }
        }finally{scenario.close()}
    }

    @Test fun bulkAddressSkipsLockedPanesAndPreservesProfiles(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var first:String
            lateinit var second:String
            lateinit var profiles:Map<String,String>
            val target="https://appassets.androidplatform.net/assets/isolation_test.html?bulk=1"
            scenario.onActivity{host->
                val panes=host.store.active().panes
                first=panes[0].id;second=panes[1].id
                profiles=panes.associate{it.id to it.profile}
                host.enterSelection(first)
                host.toggleWindowSelection(second)
                panes[1].locked=true
                host.updateWindowLocks()
            }
            openBulkAddress()
            val address=device.findObject(By.desc("Address or search for selected panes"))
            assertNotNull(address)
            address.setText(target)
            device.findObject(By.text("Open")).click()
            var loaded=false
            for(i in 0 until 35){
                scenario.onActivity{host->
                    loaded=host.store.active().panes.first{it.id==first}.url==target
                }
                if(loaded)break
                Thread.sleep(250)
            }
            assertTrue("Unlocked pane must open the target",loaded)
            scenario.onActivity{host->
                val panes=host.store.active().panes
                assertEquals("Locked pane must not navigate","about:blank",
                    panes.first{it.id==second}.url)
                assertTrue(panes.first{it.id==second}.locked)
                assertEquals("All profiles must remain stable",
                    profiles,panes.associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }
}
