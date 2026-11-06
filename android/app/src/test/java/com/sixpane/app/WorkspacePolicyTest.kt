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

    @org.junit.Test fun selectionCloseRequiresUnlockedNonempty(){
        org.junit.Assert.assertFalse(WorkspacePolicy.canCloseSelected(0,false))
        org.junit.Assert.assertFalse(WorkspacePolicy.canCloseSelected(2,true))
        org.junit.Assert.assertTrue(WorkspacePolicy.canCloseSelected(2,false))
    }
}
