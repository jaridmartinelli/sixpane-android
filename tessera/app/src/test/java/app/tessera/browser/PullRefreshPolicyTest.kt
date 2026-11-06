package app.tessera.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshPolicyTest {
    @Test fun pageAtTopJustOverscrolled() = assertTrue(PullRefreshPolicy.allow(false, 0, 50))
    @Test fun lockedNeverRefreshes() = assertFalse(PullRefreshPolicy.allow(true, 0, 50))
    @Test fun innerScrollBoxNeverOverscrollsPage() = assertFalse(PullRefreshPolicy.allow(false, 0, -1))
    @Test fun staleOverscrollIgnored() = assertFalse(PullRefreshPolicy.allow(false, 0, PullRefreshPolicy.WINDOW_MS + 1))
    @Test fun pageScrolledDown() = assertFalse(PullRefreshPolicy.allow(false, 120, 10))
}
