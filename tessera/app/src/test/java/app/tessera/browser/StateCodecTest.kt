package app.tessera.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateCodecTest {
    @Test fun freshStateHasOneWorkspaceOfSix() {
        val s = AppState().also { it.ensureValid() }
        assertEquals(1, s.workspaces.size); assertEquals(6, s.current().panes.size)
    }

    @Test fun fortyTwoProfilesAreAllDistinct() {
        val s = AppState()
        repeat(7) { s.workspaces += s.newWorkspace() }
        val names = s.allPanes().map { it.profile }
        assertEquals(42, names.size); assertEquals(42, names.toSet().size)
        assertTrue(names.all { it.startsWith("tp_") })
    }

    @Test fun issuedProfilesAreNeverReused() {
        val s = AppState()
        val first = s.newPane()
        val again = (0 until 200).map { s.newPane().profile }
        assertTrue(first.profile !in again)
        assertEquals(201, s.issuedProfiles.size)
    }

    @Test fun roundTrip() {
        val s = AppState()
        s.workspaces += s.newWorkspace(); s.workspaces += s.newWorkspace()
        s.ensureValid()
        val p = s.workspaces[1].panes[4]
        p.url = "https://example.com"; p.title = "Ex"; p.color = 3; p.locked = true; p.desktop = true; p.zoom = 125
        s.currentId = s.workspaces[1].id; s.activePaneId = p.id; s.expandedPaneId = p.id
        s.filter = PaneFilter(setOf(1, 3), "exa"); s.layout = LayoutMode.THREE_BY_TWO
        s.bookmarks += Bookmark("https://a.com", "A"); s.pendingDeletes += "tp_old"; s.keepWifiAwake = false
        val d = StateCodec.decode(StateCodec.encode(s))
        assertEquals(2, d.workspaces.size)
        val q = d.workspaces[1].panes[4]
        assertEquals(p.id, q.id); assertEquals(p.profile, q.profile); assertEquals("https://example.com", q.url)
        assertEquals(3, q.color); assertTrue(q.locked); assertTrue(q.desktop); assertEquals(125, q.zoom)
        assertEquals(p.id, d.activePaneId); assertEquals(p.id, d.expandedPaneId)
        assertEquals(PaneFilter(setOf(1, 3), "exa"), d.filter); assertEquals(LayoutMode.THREE_BY_TWO, d.layout)
        assertEquals(listOf(Bookmark("https://a.com", "A")), d.bookmarks)
        assertTrue("tp_old" in d.pendingDeletes); assertEquals(false, d.keepWifiAwake)
        assertTrue(d.allPanes().all { it.profile in d.issuedProfiles })
    }

    @Test fun capAtSeven() {
        val s = AppState(); repeat(9) { s.workspaces += s.newWorkspace() }; s.ensureValid()
        assertEquals(AppState.MAX_WORKSPACES, s.workspaces.size)
    }

    @Test fun activeFallsBackIntoCurrentWorkspace() {
        val s = AppState(); s.workspaces += s.newWorkspace(); s.workspaces += s.newWorkspace()
        s.currentId = s.workspaces[0].id; s.activePaneId = s.workspaces[1].panes[0].id
        s.ensureValid()
        assertNotEquals(s.workspaces[1].panes[0].id, s.activePaneId)
        assertEquals(s.workspaces[0].id, s.workspaceOf(s.activePaneId)?.id)
    }
}
