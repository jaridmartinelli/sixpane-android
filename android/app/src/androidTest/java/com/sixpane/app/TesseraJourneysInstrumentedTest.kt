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

/**
 * The requested emulator-only JM checks. All fixture resets are confined to
 * a disposable preview package, never an installed SIXPANE user's profiles.
 */
@RunWith(AndroidJUnit4::class)
class TesseraJourneysInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val device=UiDevice.getInstance(instrumentation)

    @Before fun previewFixtureOnly(){
        val hw=Build.HARDWARE.lowercase()
        val fp=Build.FINGERPRINT.lowercase()
        assertTrue("Never clear user data on real hardware",
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

    @Test fun bookmarkStorageStaysPerPaneAcrossWorkspaceSwitchAndRecreation(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var aId:String
            lateinit var bId:String
            lateinit var cId:String
            lateinit var originalProfiles:Map<String,String>
            lateinit var firstWorkspace:String
            val one="https://one.sixpane-fixture.invalid/own-bookmark"
            val two="https://two.sixpane-fixture.invalid/other-bookmark"
            val three="https://three.sixpane-fixture.invalid/third-bookmark"
            scenario.onActivity{host->
                val first=host.store.active()
                firstWorkspace=first.id
                aId=first.panes[0].id;bId=first.panes[1].id
                host.store.bookmarkForPane(aId,one)
                host.store.bookmarkForPane(bId,two)
                host.tesseraNewWorkspace()
                cId=host.store.active().panes[0].id
                host.store.bookmarkForPane(cId,three)
                host.tesseraOpenWorkspace(firstWorkspace)
                originalProfiles=host.store.spaces.flatMap{it.panes}
                    .associate{it.id to it.profile}
                assertEquals(listOf(one),host.store.bookmarksForPane(aId))
                assertEquals(listOf(two),host.store.bookmarksForPane(bId))
                assertEquals(listOf(three),host.store.bookmarksForPane(cId))
            }
            scenario.recreate()
            scenario.onActivity{host->
                assertEquals(firstWorkspace,host.store.currentId)
                assertEquals(originalProfiles,host.store.spaces.flatMap{it.panes}
                    .associate{it.id to it.profile})
                assertEquals(listOf(one),host.store.bookmarksForPane(aId))
                assertEquals(listOf(two),host.store.bookmarksForPane(bId))
                assertEquals(listOf(three),host.store.bookmarksForPane(cId))
                assertFalse(two in host.store.bookmarksForPane(aId))
                assertFalse(one in host.store.bookmarksForPane(bId))
                assertFalse(three in host.store.bookmarksForPane(aId))
            }
        }finally{scenario.close()}
    }

    @Test fun bookmarkSuggestionShowsTitleAndHostAndRemovesOnlyItsPane(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var secondId:String
            val one="https://one.sixpane-fixture.invalid/saved-product"
            val two="https://two.sixpane-fixture.invalid/other-product"
            scenario.onActivity{host->
                val panes=host.store.active().panes
                firstId=panes[0].id
                secondId=panes[1].id
                host.store.bookmarkForPane(firstId,one)
                host.store.bookmarkForPane(secondId,two)
                host.tesseraFocusAddress(firstId)
            }
            assertNotNull("First-pane bookmark must display a title or path",
                device.wait(Until.findObject(By.text("saved-product")),8_000L))
            assertNotNull("Bookmark must display its actual host",
                device.findObject(By.text("one.sixpane-fixture.invalid")))
            val remove=device.findObject(
                By.desc("Remove bookmark one.sixpane-fixture.invalid from this pane"))
            assertNotNull("Bookmark needs a labelled remove control",remove)
            assertTrue("Remove control needs a click target",remove.isClickable)
            remove.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertTrue("Removal should affect only the source pane",
                    host.store.bookmarksForPane(firstId).isEmpty())
                assertEquals("Other pane bookmark must survive",
                    listOf(two),host.store.bookmarksForPane(secondId))
            }
        }finally{scenario.close()}
    }

    @Test fun dockPaneChipTapAndHoldChangeViewWithoutReplacingProfiles(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var profiles:Map<String,String>
            scenario.onActivity{host->
                firstId=host.store.active().panes[0].id
                profiles=host.store.active().panes.associate{it.id to it.profile}
            }
            val chip=device.wait(Until.findObject(
                By.descContains("Pane 1; tap to expand or shrink")),8_000L)
            assertNotNull(chip)
            chip.click()
            device.waitForIdle()
            scenario.onActivity{host->assertEquals(firstId,host.store.focusId)}
            chip.click()
            device.waitForIdle()
            scenario.onActivity{host->assertNull(host.store.focusId)}
            chip.longClick()
            assertNotNull("Hold must open the pane menu",
                device.wait(Until.findObject(
                    By.textContains("Pane 1 · Workspace 1")),8_000L))
            assertNotNull(device.findObject(By.text("Select multiple")))
            device.pressBack()
            device.waitForIdle()
            scenario.onActivity{host->
                assertEquals(profiles,host.store.active().panes
                    .associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }

    @Test fun workspaceDockHoldOpensFullMenuAndCreatesAdjacentWorkspace(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstWorkspace:String
            lateinit var firstProfiles:Map<String,String>
            scenario.onActivity{host->
                firstWorkspace=host.store.currentId
                firstProfiles=host.store.active().panes.associate{it.id to it.profile}
            }
            val workspaces=device.wait(Until.findObject(
                By.descContains("Workspaces; current 1")),8_000L)
            assertNotNull(workspaces)
            workspaces.longClick()
            assertNotNull("Holding workspace button should open tools",
                device.wait(Until.findObject(By.text("Workspace 1")),8_000L))
            val newWorkspace=device.findObject(By.text("New workspace to the right"))
            assertNotNull(newWorkspace)
            newWorkspace.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertEquals(2,host.store.spaces.size)
                assertEquals(firstWorkspace,host.store.spaces[0].id)
                assertEquals(host.store.spaces[1].id,host.store.currentId)
                assertEquals(firstProfiles,host.store.spaces[0].panes
                    .associate{it.id to it.profile})
                assertEquals(6,host.store.spaces[1].panes.size)
                assertTrue(host.store.spaces[1].panes.all{
                    it.profile !in firstProfiles.values
                })
            }
        }finally{scenario.close()}
    }

    @Test fun addressShowsFullUrlOnlyWhenFocusedAndSubmitsViaEnter(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            val testUrl="https://appassets.androidplatform.net/assets/isolation_test.html"
            scenario.onActivity{host->
                val pane=host.store.active().panes[0]
                host.windowCard(pane.id)?.loadAddress(testUrl,exact=true)
            }
            var loaded=false
            for(i in 0 until 40){
                scenario.onActivity{host->
                    loaded=host.store.active().panes[0].url==testUrl
                }
                if(loaded)break
                Thread.sleep(150)
            }
            assertTrue("Local fixture must have loaded",loaded)
            val edit=device.wait(Until.findObject(
                By.clazz("android.widget.EditText")),8_000L)
            assertNotNull(edit)
            assertEquals("Unfocused dock should show host only",
                "appassets.androidplatform.net",edit.text)
            edit.click()
            // IME opening can reconstruct accessibility nodes. Reacquire
            // the focused field rather than reusing a stale UiObject2.
            val activeEditor=device.wait(Until.findObject(
                By.clazz("android.widget.EditText").focused(true)),8_000L)
            assertNotNull("Address field must retain focus while suggestions show",activeEditor)
            assertEquals("Focused field should expose complete address",testUrl,activeEditor.text)
            var typed=false
            for(attempt in 0 until 4){
                try{
                    val fresh=device.findObject(
                        By.clazz("android.widget.EditText").focused(true))
                    if(fresh!=null){fresh.setText("about:blank");typed=true;break}
                }catch(_:androidx.test.uiautomator.StaleObjectException){
                    Thread.sleep(200)
                }
            }
            assertTrue("The focused address control should accept text",typed)
            device.pressEnter()
            var submitted=false
            for(i in 0 until 30){
                scenario.onActivity{host->
                    submitted=host.store.active().panes[0].url=="about:blank"
                }
                if(submitted)break
                Thread.sleep(150)
            }
            assertTrue("Hardware Enter must submit the typed URL",submitted)
        }finally{scenario.close()}
    }


    @Test fun multiPaneColourUndoSkipsSupersededColourEdits(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var secondId:String
            scenario.onActivity{host->
                val panes=host.store.active().panes
                firstId=panes[0].id
                secondId=panes[1].id
                host.tesseraRecolor(setOf(firstId),FramePalette.tesseraColors[1])
                host.tesseraRecolor(setOf(secondId),FramePalette.tesseraColors[2])
                host.enterSelection(firstId)
                host.toggleWindowSelection(secondId)
            }
            val change=device.wait(Until.findObject(
                By.desc("Change colour of selected panes")),8_000L)
            assertNotNull(change)
            change.click()
            val choose=device.wait(Until.findObject(
                By.desc("Set selected panes colour to Blue")),8_000L)
            assertNotNull("Bulk colour swatches need accessible names",choose)
            choose.click()
            scenario.onActivity{host->
                assertEquals(FramePalette.tesseraColors[0],host.store.frameColor(firstId))
                assertEquals(FramePalette.tesseraColors[0],host.store.frameColor(secondId))
                // A separate newer edit is authoritative, not an Undo casualty.
                host.tesseraRecolor(setOf(firstId),FramePalette.tesseraColors[3])
            }
            val undo=device.wait(Until.findObject(By.text("Undo")),3_000L)
            assertNotNull("Colour changes must offer Undo",undo)
            undo.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertEquals("Undo must preserve the user's newer colour edit",
                    FramePalette.tesseraColors[3],host.store.frameColor(firstId))
                assertEquals("Undo must restore the other pane's prior colour",
                    FramePalette.tesseraColors[2],host.store.frameColor(secondId))
            }
        }finally{scenario.close()}
    }

    @Test fun paneLockUndoDoesNotReverseAnInterveningLockChange(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            scenario.onActivity{host->
                firstId=host.store.active().panes[0].id
                host.tesseraMenuAll()
            }
            val lock=device.wait(Until.findObject(By.text("Lock pane")),8_000L)
            assertNotNull(lock)
            lock.click()
            scenario.onActivity{host->
                val pane=host.store.active().panes[0]
                assertTrue(pane.locked)
                pane.locked=false
                host.updateWindowLocks()
            }
            val undo=device.wait(Until.findObject(By.text("Undo")),3_000L)
            assertNotNull(undo)
            undo.click()
            scenario.onActivity{host->
                assertEquals(firstId,host.store.active().panes[0].id)
                assertFalse("Undo must not overwrite a newer unlock",
                    host.store.active().panes[0].locked)
            }
        }finally{scenario.close()}
    }
}
