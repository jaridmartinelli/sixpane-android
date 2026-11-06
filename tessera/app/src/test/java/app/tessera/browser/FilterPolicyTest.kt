package app.tessera.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPolicyTest {
    private val url = "https://mail.google.com/inbox"

    @Test fun noFilterMatchesEverything() = assertTrue(FilterPolicy.matches("", -1, PaneFilter()))
    @Test fun colorsAreOr() {
        val f = PaneFilter(colors = setOf(1, 3))
        assertTrue(FilterPolicy.matches(url, 1, f)); assertTrue(FilterPolicy.matches(url, 3, f)); assertFalse(FilterPolicy.matches(url, 2, f))
    }
    @Test fun uncolouredExcludedByColourFilter() = assertFalse(FilterPolicy.matches(url, -1, PaneFilter(colors = setOf(1))))
    @Test fun urlTextAndColourAreAnd() {
        val f = PaneFilter(colors = setOf(1), urlText = "google")
        assertTrue(FilterPolicy.matches(url, 1, f)); assertFalse(FilterPolicy.matches("https://bing.com", 1, f))
    }
    @Test fun urlIsCaseInsensitive() = assertTrue(FilterPolicy.matches(url, -1, PaneFilter(urlText = "GOOGLE")))
    @Test fun schemeInQueryIgnored() = assertTrue(FilterPolicy.matches(url, -1, PaneFilter(urlText = "https://mail.google")))
    @Test fun blankPaneNeverMatchesUrl() = assertFalse(FilterPolicy.matches("", -1, PaneFilter(urlText = "a")))
}
