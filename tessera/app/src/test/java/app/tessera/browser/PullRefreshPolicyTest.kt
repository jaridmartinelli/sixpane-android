package app.tessera.browser

import app.tessera.browser.PullRefreshPolicy.Inner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshPolicyTest {
    @Test fun pageAtTop() = assertTrue(PullRefreshPolicy.allow(false, 0, Inner.AT_TOP))
    @Test fun lockedNeverRefreshes() = assertFalse(PullRefreshPolicy.allow(true, 0, Inner.AT_TOP))
    @Test fun scrolledInnerBoxScrollsInstead() = assertFalse(PullRefreshPolicy.allow(false, 0, Inner.SCROLLED))
    @Test fun unknownWaits() = assertFalse(PullRefreshPolicy.allow(false, 0, Inner.UNKNOWN))
    @Test fun pageScrolledDown() = assertFalse(PullRefreshPolicy.allow(false, 120, Inner.AT_TOP))
    @Test fun probeHasCoordinates() = assertTrue(PullRefreshPolicy.probeScript(10f, 20f).contains("(10.0,20.0)"))
}
