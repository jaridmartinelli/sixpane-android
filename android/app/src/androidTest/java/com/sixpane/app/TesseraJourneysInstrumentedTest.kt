package com.sixpane.app

import android.content.Context
import android.content.Intent
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
            // A popup/IME transition can rebuild accessibility nodes, and
            // UiAutomator's focused selector is not always the same as Android
            // EditText.hasFocus(). Assert real view focus, then acquire a fresh node.
            var actualFocus=false
            scenario.onActivity{host->
                val f=MainActivity::class.java.getDeclaredField("tessera")
                f.isAccessible=true
                actualFocus=(f.get(host) as TesseraBar).addressFocused()
            }
            assertTrue("The native address field must retain input focus",actualFocus)
            // UIAutomator cannot always enumerate a text node while the IME
            // and suggestion PopupWindow are visible. Exercise the user input
            // channel directly, rather than changing the field programmatically.
            scenario.onActivity{host->
                val f=MainActivity::class.java.getDeclaredField("tessera")
                f.isAccessible=true
                val bar=f.get(host) as TesseraBar
                val editorField=TesseraBar::class.java.getDeclaredField("address")
                editorField.isAccessible=true
                val editor=editorField.get(bar) as android.widget.EditText
                assertEquals("The full URL must appear in the focused native field",
                    testUrl,editor.text.toString())
                editor.selectAll()
            }
            device.executeShellCommand("input text about:blank")
            var typed=false
            var observed=""
            for(attempt in 0 until 30){
                scenario.onActivity{host->
                    val f=MainActivity::class.java.getDeclaredField("tessera")
                    f.isAccessible=true
                    val bar=f.get(host) as TesseraBar
                    val editorField=TesseraBar::class.java.getDeclaredField("address")
                    editorField.isAccessible=true
                    val editor=editorField.get(bar) as android.widget.EditText
                    observed=editor.text.toString()
                    typed=observed=="about:blank"
                }
                if(typed)break
                Thread.sleep(200)
            }
            assertTrue("System input must reach the focused address editor; was="+observed,
                typed)
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


    @Test fun paneHeaderTapSelectsAndHoldOpensItsMenu(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var paneOne:String
            lateinit var paneTwo:String
            lateinit var profiles:Map<String,String>
            scenario.onActivity{host->
                paneOne=host.store.active().panes[0].id
                paneTwo=host.store.active().panes[1].id
                profiles=host.store.active().panes.associate{it.id to it.profile}
                host.tesseraActivate(paneTwo)
                assertEquals(paneTwo,host.store.activePaneId)
            }
            val header=device.wait(Until.findObject(By.desc(
                "Pane header; make active, double tap to expand, hold for pane menu")),8_000L)
            assertNotNull("Pane header needs an accessible tap/hold target",header)
            header.click()
            Thread.sleep(500) // GestureDetector single-tap confirmation
            scenario.onActivity{host->assertEquals(
                "Header tap must make the correct pane active",paneOne,host.store.activePaneId)}
            val firstHeader=device.findObject(By.desc(
                "Pane header; make active, double tap to expand, hold for pane menu"))
            assertNotNull(firstHeader)
            firstHeader.longClick()
            assertNotNull("Header hold must show the pane's own menu",
                device.wait(Until.findObject(
                    By.textContains("Pane 1 · Workspace 1")),8_000L))
            device.pressBack()
            scenario.onActivity{host->
                assertEquals(profiles,host.store.active().panes.associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }

    @Test fun paneMenuSelectionAndAllNoneWorkThroughVisibleControls(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->host.tesseraMenuAll()}
            val select=device.wait(Until.findObject(By.text("Select multiple")),8_000L)
            assertNotNull(select)
            select.click()
            scenario.onActivity{host->
                assertTrue(host.tesseraSelecting())
                assertEquals(1,host.tesseraSelected().size)
            }
            val all=device.wait(Until.findObject(
                By.desc("Select all visible panes")),8_000L)
            assertNotNull(all)
            all.click()
            scenario.onActivity{host->assertEquals(6,host.tesseraSelected().size)}
            val none=device.wait(Until.findObject(
                By.desc("Deselect all visible panes")),8_000L)
            assertNotNull(none)
            none.click()
            scenario.onActivity{host->
                assertTrue(host.tesseraSelecting())
                assertTrue(host.tesseraSelected().isEmpty())
            }
        }finally{scenario.close()}
    }

    @Test fun multiColourChipsRemoveOneFilterWithoutChangingProfiles(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var ids:List<String>
            lateinit var profiles:Map<String,String>
            scenario.onActivity{host->
                ids=host.store.active().panes.map{it.id}
                profiles=host.store.active().panes.associate{it.id to it.profile}
                host.store.setFrameColor(setOf(ids[0]),FramePalette.tesseraColors[0])
                host.store.setFrameColor(setOf(ids[1]),FramePalette.tesseraColors[1])
                host.tesseraSetFilter("",setOf("Blue","Green"))
            }
            assertNotNull(device.wait(Until.findObject(
                By.desc("Remove Blue colour filter")),8_000L))
            val green=device.findObject(By.desc("Remove Green colour filter"))
            assertNotNull(green)
            assertNotNull("Two active colours must report two matches",
                device.findObject(By.text("2 matches")))
            val remove=device.findObject(By.desc("Remove Blue colour filter"))
            assertNotNull(remove)
            remove.click()
            device.waitForIdle()
            scenario.onActivity{host->
                assertEquals(setOf("Green"),host.store.selectedFrameColors)
                assertEquals(setOf(ids[1]),host.tesseraVisibleSelectionIds())
                assertEquals(profiles,host.store.active().panes.associate{it.id to it.profile})
            }
            assertNotNull(device.wait(Until.findObject(By.text("1 matches")),8_000L))
            val clear=device.findObject(By.desc("Clear all filters"))
            assertNotNull(clear)
            clear.click()
            scenario.onActivity{host->
                assertTrue(host.store.selectedFrameColors.isEmpty())
                assertEquals(profiles,host.store.active().panes.associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }


    @Test fun snackbarWithUndoSitsAboveDockWhenKeyboardIsVisible(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->
                val f=MainActivity::class.java.getDeclaredField("tessera")
                f.isAccessible=true
                (f.get(host) as TesseraBar).focusAddress(host.store.active().panes[0].id)
            }
            var imeVisible=false
            for(attempt in 0 until 35){
                scenario.onActivity{host->
                    imeVisible=Build.VERSION.SDK_INT>=30 &&
                        host.window.decorView.rootWindowInsets?.isVisible(
                            android.view.WindowInsets.Type.ime())==true
                }
                if(imeVisible)break
                Thread.sleep(200)
            }
            assertTrue("Emulator must display an IME to validate keyboard anchoring",
                imeVisible)
            var dockTop=0
            var density=1f
            scenario.onActivity{host->
                val f=MainActivity::class.java.getDeclaredField("tessera")
                f.isAccessible=true
                val bar=f.get(host) as TesseraBar
                val position=IntArray(2)
                bar.dock.getLocationOnScreen(position)
                dockTop=position[1]
                density=host.resources.displayMetrics.density
                bar.snack("Check dock anchor"){}
            }
            val undo=device.wait(Until.findObject(By.text("Undo")),3_000L)
            assertNotNull("Snack with action should show Undo",undo)
            val snap=undo.visibleBounds
            assertTrue("Snack should be placed above the keyboard-lifted dock",
                snap.bottom<=dockTop-(6*density).toInt())
            assertTrue("Snack should be within 64dp of dock top",
                dockTop-snap.bottom<(64*density).toInt())
            undo.click()
        }finally{scenario.close()}
    }


    @Test fun closingPaneRetiresOnlyItsBookmarksAndNeverCopiesThemToFreshPane(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var first:String
            lateinit var second:String
            lateinit var formerProfile:String
            val bookmarkA="https://fixture.one.invalid/private-p1"
            val bookmarkB="https://fixture.two.invalid/private-p2"
            scenario.onActivity{host->
                val panes=host.store.active().panes
                first=panes[0].id
                second=panes[1].id
                formerProfile=panes[0].profile
                host.store.bookmarkForPane(first,bookmarkA)
                host.store.bookmarkForPane(second,bookmarkB)
                host.tesseraEnd(setOf(first))
            }
            assertNotNull(device.wait(Until.findObject(By.text("End Pane 1?")),8_000L))
            val confirm=device.findObject(By.text("End sessions"))
            assertNotNull(confirm)
            confirm.click()
            device.waitForIdle()
            scenario.onActivity{host->
                val panes=host.store.active().panes
                assertNotEquals("Ended slot must receive a fresh pane ID",first,panes[0].id)
                assertNotEquals("Ended slot must receive a fresh isolated profile",
                    formerProfile,panes[0].profile)
                assertTrue("A new session must not inherit the ended pane's bookmarks",
                    panes[0].bookmarks.isEmpty())
                assertEquals("The neighboring pane must keep its own bookmark",second,panes[1].id)
                assertEquals(listOf(bookmarkB),host.store.bookmarksForPane(second))
                assertNull("Retired pane must not be addressed as live",
                    host.store.spaces.flatMap{it.panes}.firstOrNull{it.id==first})
                assertTrue("Former profile must be queued for retirement",
                    formerProfile in host.store.pendingDeletion)
            }
        }finally{scenario.close()}
    }

    @Test fun sevenWorkspaceMaximumPreserves42DistinctSessionProfiles(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            scenario.onActivity{host->
                repeat(6){host.tesseraNewWorkspace()}
                assertEquals("SIXPANE holds seven workspaces",7,host.store.spaces.size)
                assertTrue(host.store.spaces.all{it.panes.size==6})
                val ids=host.store.spaces.flatMap{it.panes}.map{it.id}
                val profiles=host.store.spaces.flatMap{it.panes}.map{it.profile}
                assertEquals("42 distinct pane IDs",42,ids.toSet().size)
                assertEquals("42 distinct named WebView profiles",42,profiles.toSet().size)
                assertTrue("42 unique declared viewport dimensions",
                    ViewportRatioPolicy.allUnique(host.store.spaces.flatMap{it.panes}
                        .map{it.widthPercent to it.heightPercent}))
                val snapshot=host.store.spaces.map{it.id to it.panes.map{p->p.profile}}
                host.tesseraNewWorkspace()
                assertEquals("Creating an eighth workspace must be refused",
                    snapshot,host.store.spaces.map{it.id to it.panes.map{p->p.profile}})
            }
        }finally{scenario.close()}
    }

    @Test fun manualGridOverrideChangesActualPaneGeometryAndPersists(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var profiles:Map<String,String>
            lateinit var p:List<String>
            scenario.onActivity{host->
                p=host.store.active().panes.map{it.id}
                profiles=host.store.active().panes.associate{it.id to it.profile}
                host.tesseraSetGrid("3x2")
            }
            fun assertShape(threeAcross:Boolean){
                var ready=false
                var diagnostic=""
                for(i in 0 until 30){
                    scenario.onActivity{host->
                        val layout=p.map{id->
                            host.windowCard(id)?.layoutParams as? android.widget.FrameLayout.LayoutParams
                        }
                        if(layout.all{it!=null}){
                            val cells=layout.map{it!!}
                            ready=if(threeAcross)
                                cells[0].topMargin==cells[2].topMargin &&
                                    cells[3].topMargin>cells[0].topMargin
                            else cells[0].topMargin==cells[1].topMargin &&
                                cells[2].topMargin>cells[0].topMargin
                            diagnostic=cells.map{it.leftMargin to it.topMargin}.toString()
                        }
                    }
                    if(ready)break
                    Thread.sleep(250)
                }
                assertTrue("Grid preference did not produce expected pane positions: "+diagnostic,
                    ready)
            }
            assertShape(true)
            scenario.onActivity{host->assertEquals("3x2",host.store.gridPreference)}
            scenario.recreate()
            scenario.onActivity{host->
                assertEquals("Grid override must persist", "3x2",host.store.gridPreference)
                assertEquals("Grid change cannot replace profiles",
                    profiles,host.store.active().panes.associate{it.id to it.profile})
                host.tesseraSetGrid("2x3")
            }
            assertShape(false)
        }finally{scenario.close()}
    }

    @Test fun unsupportedWebViewShowsShieldAndStoreActionWithoutCreatingWebViews(){
        val intent=Intent(instrumentation.targetContext,MainActivity::class.java).apply{
            putExtra("sixpane.qa.forceUnsupportedWebView",true)
        }
        val scenario=ActivityScenario.launch<MainActivity>(intent)
        try{
            assertNotNull("Unsupported-WebView mode should explain the failure",
                device.wait(Until.findObject(
                    By.text("Update Android System WebView")),8_000L))
            assertNotNull("A shield should identify the isolation warning",
                device.findObject(
                    By.desc("Shield: browser profile isolation unavailable")))
            val store=device.findObject(
                By.desc("Open Play Store for Android System WebView"))
            assertNotNull("The recovery action should be accessible",store)
            assertTrue("The Play Store action must be tappable",store.isClickable)
            scenario.onActivity{host->
                assertTrue("Never allocate any profiles when isolation is unavailable",
                    host.auditPanes().all{(_,card)->card==null})
            }
        }finally{scenario.close()}
    }


    @Test fun cookieEditorHidesMutationControlsWhenLockedAndShowsOnlyValidActions(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var profileId:String
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                profileId=pane.profile
                pane.locked=true
                host.updateWindowLocks()
                host.tesseraMenuAll()
            }
            val cookies=device.wait(Until.findObject(By.text("Cookies & site data")),8_000L)
            assertNotNull("The locked pane must still allow cookie inspection",cookies)
            cookies.click()
            assertNotNull("Locked pane should clearly state view-only",
                device.wait(Until.findObject(By.textContains("VIEW ONLY")),8_000L))
            assertNull("Locked cookie editor must hide Add action",
                device.findObject(By.text("Add cookie")))
            assertNull("Locked cookie editor must hide Delete all action",
                device.findObject(By.text("Delete all for this site")))
            assertNull("Locked cookie editor must hide Edit action",
                device.findObject(By.text("✎ Edit")))
            assertNull("Locked cookie editor must hide Delete action",
                device.findObject(By.text("⌫ Delete")))
            device.pressBack()
            scenario.onActivity{host->
                val pane=host.store.active().panes.first()
                pane.locked=false
                host.updateWindowLocks()
                host.tesseraMenuAll()
            }
            val unlocked=device.wait(Until.findObject(
                By.text("Cookies & site data")),8_000L)
            assertNotNull(unlocked)
            unlocked.click()
            assertNotNull("An unlocked pane can add its own site cookie",
                device.wait(Until.findObject(By.text("Add cookie")),8_000L))
            assertNotNull("Unrestricted site actions should remain in original layout",
                device.findObject(By.text("Delete all for this site")))
            assertNull("With no cookie selected, omit the irrelevant Edit row",
                device.findObject(By.text("✎ Edit")))
            val search=device.findObject(By.clazz("android.widget.EditText"))
            val add=device.findObject(By.text("Add cookie"))
            val storage=device.wait(Until.findObject(By.textContains("Storage on this page")),
                5_000L)
            assertNotNull(search);assertNotNull(add);assertNotNull(storage)
            assertTrue("Cookie actions belong below search",search.visibleBounds.bottom<=add.visibleBounds.top)
            assertTrue("Cookie actions belong ABOVE page storage, not pinned to the bottom",
                add.visibleBounds.bottom<=storage.visibleBounds.top)
            scenario.onActivity{host->
                assertEquals(profileId,host.store.active().panes.first().profile)
            }
        }finally{scenario.close()}
    }


    @Test fun bookmarkStarUndoIsPaneScopedAndDoesNotOverwriteNewerEdits(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            dismissWelcome()
            lateinit var firstId:String
            lateinit var secondId:String
            lateinit var profiles:Map<String,String>
            val firstUrl="https://appassets.androidplatform.net/assets/isolation_test.html"
            val secondUrl="https://other.sixpane-fixture.invalid/own"
            scenario.onActivity{host->
                val panes=host.store.active().panes
                firstId=panes[0].id
                secondId=panes[1].id
                profiles=panes.associate{it.id to it.profile}
                host.store.bookmarkForPane(secondId,secondUrl)
                host.windowCard(firstId)?.loadAddress(firstUrl,exact=true)
            }
            var loaded=false
            for(i in 0 until 45){
                scenario.onActivity{host->
                    loaded=host.store.active().panes[0].url==firstUrl &&
                        host.windowCard(firstId)?.loading==false
                }
                if(loaded)break
                Thread.sleep(200)
            }
            assertTrue("Local bookmark fixture should finish navigating",loaded)
            val initial=device.wait(Until.findObject(By.text("☆")),10_000L)
            assertNotNull("Unbookmarked page must show the star action",initial)
            initial.click()
            val undo=device.wait(Until.findObject(By.text("Undo")),4_000L)
            assertNotNull("New bookmark should offer Undo",undo)
            scenario.onActivity{host->
                assertEquals(listOf(firstUrl),host.store.bookmarksForPane(firstId))
                assertEquals(listOf(secondUrl),host.store.bookmarksForPane(secondId))
            }
            undo.click()
            scenario.onActivity{host->
                assertTrue("Undo should revert only the bookmark just added",
                    host.store.bookmarksForPane(firstId).isEmpty())
                assertEquals(listOf(secondUrl),host.store.bookmarksForPane(secondId))
            }
            // Recreate the action and make a newer change before Undo. An older
            // Undo must never re-add or remove a changed bookmark.
            val star=device.wait(Until.findObject(By.text("☆")),5_000L)
            assertNotNull(star)
            star.click()
            val staleUndo=device.wait(Until.findObject(By.text("Undo")),4_000L)
            assertNotNull(staleUndo)
            scenario.onActivity{host->
                assertEquals(listOf(firstUrl),host.store.bookmarksForPane(firstId))
                host.store.toggleBookmarkForPane(firstId,firstUrl)
                assertTrue(host.store.bookmarksForPane(firstId).isEmpty())
            }
            staleUndo.click()
            scenario.onActivity{host->
                assertTrue("Undo must not overwrite a newer bookmark edit",
                    host.store.bookmarksForPane(firstId).isEmpty())
                assertEquals(listOf(secondUrl),host.store.bookmarksForPane(secondId))
                assertEquals(profiles,host.store.active().panes.associate{it.id to it.profile})
            }
        }finally{scenario.close()}
    }


    @Test fun welcomeDoesNotPromiseUnstoppableBackgroundWebViews(){
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            assertNotNull("First-run screen should explain background permissions",
                device.wait(Until.findObject(
                    By.text("Help keep sessions available")),8_000L))
            assertNull("Android cannot guarantee pages never stop in background",
                device.findObject(By.text("So sessions never drop")))
            assertNotNull(device.findObject(By.text("Got it")))
        }finally{scenario.close()}
    }
}
