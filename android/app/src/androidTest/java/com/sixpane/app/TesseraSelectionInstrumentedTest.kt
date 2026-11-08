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

/** Execute on the disposable CI emulator; never reset an installed personal app. */
@RunWith(AndroidJUnit4::class)
class TesseraSelectionInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)

    @Before fun isolatedFixture(){
        val app=instrumentation.targetContext
        val deviceFingerprint=Build.FINGERPRINT.lowercase()
        val emulator=Build.HARDWARE.lowercase().let{
            it.contains("ranchu")||it.contains("goldfish")
        }||deviceFingerprint.contains("generic")||deviceFingerprint.contains("sdk_gphone")
        assertTrue("Only the disposable emulator may reset the fixture",emulator)
        assertEquals("com.sixpane.app.preview.filterview",app.packageName)
        assertTrue(app.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
            .edit().clear().commit())
    }

    private fun dismissWelcome(){
        device.wait(Until.findObject(By.text("Got it")),6_000L)?.click()
        device.waitForIdle()
    }

    @Test fun selectingAcrossWorkspacesKeepsEarlierChoicesAndAllNoneIsLocal(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            dismissWelcome()
            lateinit var a:String
            lateinit var b:String
            lateinit var bWorkspace:String
            scenario.onActivity{host->
                val first=host.store.active()
                a=first.panes.first().id
                host.tesseraNewWorkspace()
                bWorkspace=host.store.currentId
                b=host.store.active().panes[1].id
                host.tesseraOpenWorkspace(first.id)
                host.enterSelection(a)
                assertEquals(setOf(a),host.tesseraSelected())
                host.tesseraOpenWorkspace(bWorkspace)
                host.enterSelection(b) // The pane-menu action used to reset previous selections.
                assertEquals(setOf(a,b),host.tesseraSelected())
                host.tesseraSelectAll()
                assertEquals(7,host.tesseraSelected().size)
                assertTrue(a in host.tesseraSelected())
                host.tesseraSelectAll()
                assertEquals(setOf(a),host.tesseraSelected())
                host.tesseraSetFilter("nonexistent-fixture.example",emptySet())
                assertEquals(setOf(a),host.tesseraSelected())
                host.tesseraClearFilters()
                assertEquals(setOf(a),host.tesseraSelected())
                host.tesseraOpenWorkspace(first.id)
                assertEquals(setOf(a),host.tesseraSelected())
            }
        } finally {scenario.close()}
    }

    @Test fun bulkOpenAndDesktopSkipLockedPanesAndEndOnlyUnlockedOne(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            dismissWelcome()
            lateinit var lockedId:String
            lateinit var endedId:String
            lateinit var lockedProfile:String
            lateinit var endedProfile:String
            val testUrl="https://appassets.androidplatform.net/assets/isolation_test.html"
            scenario.onActivity{host->
                val panes=host.store.active().panes
                val protected=panes[0]
                val eligible=panes[1]
                lockedId=protected.id;lockedProfile=protected.profile
                endedId=eligible.id;endedProfile=eligible.profile
                protected.locked=true
                host.persist()
                host.enterSelection(protected.id)
                host.toggleWindowSelection(eligible.id)
                assertEquals(setOf(lockedId,endedId),host.tesseraSelected())
                host.tesseraBulkOpenAddress(testUrl)
                assertEquals("about:blank",protected.url)
                assertEquals(testUrl,eligible.url)
                host.tesseraBulkDesktop(true)
                assertFalse(protected.desktop)
                assertTrue(eligible.desktop)
                host.tesseraEnd(host.tesseraSelected())
            }
            val prompt=device.wait(Until.findObject(By.text("End 1 session?")),10_000L)
            assertNotNull("Confirmation must count only eligible panes",prompt)
            val description=device.findObject(By.textContains("Skipped 1 locked pane."))
            assertNotNull("Confirmation must disclose skipped locked pane",description)
            val end=device.wait(Until.findObject(By.text("End sessions")),8_000L)
            assertNotNull(end)
            end.click()
            device.waitForIdle()
            scenario.onActivity{host->
                val current=host.store.active().panes
                assertEquals(lockedId,current[0].id)
                assertEquals(lockedProfile,current[0].profile)
                assertTrue(current[0].locked)
                assertNotEquals(endedId,current[1].id)
                assertNotEquals(endedProfile,current[1].profile)
                assertEquals("about:blank",current[1].url)
                assertFalse(host.tesseraSelecting())
            }
        } finally {scenario.close()}
    }

    @Test fun androidBackExitsSelectionThenCollapsesEnlargedPane(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            dismissWelcome()
            lateinit var paneId:String
            scenario.onActivity{host->
                paneId=host.store.active().panes[0].id
                host.enterSelection(paneId)
                assertTrue(host.tesseraSelecting())
            }
            device.pressBack()
            device.waitForIdle()
            scenario.onActivity{host->
                assertFalse(host.tesseraSelecting())
                host.tesseraActivate(paneId,true)
                assertEquals(paneId,host.store.focusId)
            }
            device.pressBack()
            device.waitForIdle()
            scenario.onActivity{host->assertNull(host.store.focusId)}
        }finally{scenario.close()}
    }
}
