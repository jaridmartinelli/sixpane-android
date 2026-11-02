package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class BrowserPolicyTest {
    @Test fun recoveryIsBoundedAndBacksOff(){
        assertEquals(listOf(2000L,4000L,8000L,16000L,30000L,30000L,30000L,30000L,30000L,30000L),
            (1..10).map{BrowserPolicy.recoveryDelayMs(it)})
        assertThrows(IllegalArgumentException::class.java){BrowserPolicy.recoveryDelayMs(11)}
    }
    @Test fun sharedUaHasNoMobileWebViewTokens(){
        val ua=BrowserPolicy.DESKTOP_USER_AGENT
        assertTrue(ua.contains("Chrome/"))
        listOf("wv","Mobile","Version/4.0").forEach{assertFalse(ua.contains(it))}
    }
    @Test fun explicitPortalInputIsValidatedWithoutRewritingItsQuery(){
        assertEquals("https://portal.example/start?a=1&b=2",BrowserPolicy.normalizeAddress("portal.example/start?a=1&b=2"))
        assertNull(BrowserPolicy.normalizeAddress("javascript://alert(1)"))
        assertEquals("https://user:password@example.com",BrowserPolicy.normalizeAddress("https://user:password@example.com"))
        assertNull(BrowserPolicy.normalizeAddress("not a host"))
    }

    @Test fun omniboxSearchesTextInsteadOfTreatingItAsHostname(){
        assertEquals("https://www.google.com/search?q=cats",BrowserPolicy.resolveAddressOrSearch("cats"))
        assertEquals("https://www.google.com/search?q=best+isekai+anime",
            BrowserPolicy.resolveAddressOrSearch(" best isekai anime "))
        assertEquals("https://www.google.com/search?q=weather+tomorrow",
            BrowserPolicy.resolveAddressOrSearch("weather tomorrow"))
        assertEquals("https://www.google.com/search?q=hello+%26+goodbye",
            BrowserPolicy.resolveAddressOrSearch("hello & goodbye"))
        assertNull(BrowserPolicy.resolveAddressOrSearch("   "))
    }
    @Test fun omniboxPreservesRealWebAddresses(){
        assertEquals("https://google.com",BrowserPolicy.resolveAddressOrSearch("google.com"))
        assertEquals("https://www.youtube.com",BrowserPolicy.resolveAddressOrSearch("www.youtube.com"))
        assertEquals("https://example.com/page?a=1",BrowserPolicy.resolveAddressOrSearch("https://example.com/page?a=1"))
        assertEquals("http://localhost:8080/",BrowserPolicy.resolveAddressOrSearch("http://localhost:8080/"))
        assertEquals("https://localhost:8080",BrowserPolicy.resolveAddressOrSearch("localhost:8080"))
        assertEquals("about:blank",BrowserPolicy.resolveAddressOrSearch("about:blank"))
        assertEquals("https://example.com/a%20b",BrowserPolicy.resolveAddressOrSearch("https://example.com/a b"))
    }
    @Test fun strictBulkPortalAddressesStillRejectSearchQueries(){
        assertNull(BrowserPolicy.normalizeAddress("best isekai anime"))
        assertNull(BrowserPolicy.normalizeAddress("javascript://alert(1)"))
    }
    @Test fun duplicateMeansMultiplePanesNotMultipleSources(){
        val samePane=listOf(SessionCandidate(1,"cookie","qid","A"),SessionCandidate(1,"localStorage","qid","A"))
        assertTrue(SessionAuditPolicy.duplicates(samePane).isEmpty())
        val duplicate=samePane+SessionCandidate(42,"cookie","other-name","A")+SessionCandidate(2,"cookie","qid","B")
        assertEquals(setOf("A"),SessionAuditPolicy.duplicates(duplicate))
        assertEquals(2,SessionAuditPolicy.distinctTotal(duplicate))
    }
}
