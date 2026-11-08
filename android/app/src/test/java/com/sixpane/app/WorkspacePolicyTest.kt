package com.sixpane.app
import org.junit.Assert.*
import org.junit.Test
class WorkspacePolicyTest {
    @Test fun workspaceLimit() { assertTrue(WorkspacePolicy.canCreate(6)); assertFalse(WorkspacePolicy.canCreate(7)) }
    @Test fun locksProtectDestructiveActions() {
        assertFalse(WorkspacePolicy.canClose(true)); assertFalse(WorkspacePolicy.canReset(true))
        assertFalse(WorkspacePolicy.canChangeDesktop(true)); assertFalse(WorkspacePolicy.canModifyCookies(true))
        assertTrue(WorkspacePolicy.canClose(false))
    }
    @Test fun closingWorkspaceProtectsLockedChildSessions(){
        assertTrue(WorkspacePolicy.canCloseWorkspace(false,List(6){false}))
        assertFalse(WorkspacePolicy.canCloseWorkspace(true,List(6){false}))
        assertFalse(WorkspacePolicy.canCloseWorkspace(false,listOf(false,false,true,false,false,false)))
        assertFalse(WorkspacePolicy.canCloseWorkspace(true,List(6){true}))
    }
    @Test fun zoomIndependentOfLocks() {
        assertEquals(110,WorkspacePolicy.zoom(100,1))
        assertEquals(50,WorkspacePolicy.zoom(50,-1))
        assertEquals(200,WorkspacePolicy.zoom(200,1))
        assertEquals("",WorkspacePolicy.displayZoom(100)); assertEquals("90%",WorkspacePolicy.displayZoom(90))
    }

    @Test fun allNoneIsScopedToTheCurrentWorkspace(){
        val selected=setOf("workspace-a-1", "workspace-b-1")
        val visible=setOf("workspace-b-1", "workspace-b-2")
        val all=WorkspacePolicy.toggleVisibleSelection(selected,visible)
        assertEquals(setOf("workspace-a-1","workspace-b-1","workspace-b-2"),all)
        val none=WorkspacePolicy.toggleVisibleSelection(all,visible)
        assertEquals(setOf("workspace-a-1"),none)
        assertEquals(6,WorkspacePolicy.toggleVisibleSelection(emptySet(),
            (1..6).map{it.toString()}.toSet()).size)
    }

    @Test fun destructiveSelectionFindsEveryWorkspace(){
        val active=setOf("a1","a2","a3","a4","a5","a6")
        assertFalse(WorkspacePolicy.crossesWorkspace(active,setOf("a1","a2"),false))
        assertTrue(WorkspacePolicy.crossesWorkspace(active,setOf("a1","b1"),false))
        assertTrue(WorkspacePolicy.crossesWorkspace(active,setOf("a1"),true))
    }

    @Test fun selectionBarReflectsWorkspaceSwitchEvenWhenCountUnchanged(){
        val selected=setOf("p1","p2")
        assertNotEquals(WorkspacePolicy.selectionBarKey("A",selected),
            WorkspacePolicy.selectionBarKey("B",selected))
        assertEquals(WorkspacePolicy.selectionBarKey("A",setOf("p1","p2")),
            WorkspacePolicy.selectionBarKey("A",setOf("p2","p1")))
        assertNotEquals(WorkspacePolicy.selectionBarKey("A",setOf("p1","p2")),
            WorkspacePolicy.selectionBarKey("A",setOf("p1","p3")))
    }

    @Test fun workspaceClosingKeepsCurrentOrSelectsNeighbour(){
        val remaining=listOf("w1","w3","w4")
        assertEquals("w3",WorkspacePolicy.workspaceAfterClose(remaining,1,"w2"))
        assertEquals("w4",WorkspacePolicy.workspaceAfterClose(remaining,10,"w2"))
        assertEquals("w1",WorkspacePolicy.workspaceAfterClose(remaining,1,"w1"))
        assertEquals("fresh",WorkspacePolicy.workspaceAfterClose(listOf("fresh"),0,"w1"))
    }

    @org.junit.Test fun selectionCloseRequiresUnlockedNonempty(){
        org.junit.Assert.assertFalse(WorkspacePolicy.canCloseSelected(0,false))
        org.junit.Assert.assertFalse(WorkspacePolicy.canCloseSelected(2,true))
        org.junit.Assert.assertTrue(WorkspacePolicy.canCloseSelected(2,false))
    }
}
