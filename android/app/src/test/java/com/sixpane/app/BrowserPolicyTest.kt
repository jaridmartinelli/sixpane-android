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
    @Test fun duplicateMeansMultiplePanesNotMultipleSources(){
        val samePane=listOf(SessionCandidate(1,"cookie","qid","A"),SessionCandidate(1,"localStorage","qid","A"))
        assertTrue(SessionAuditPolicy.duplicates(samePane).isEmpty())
        val duplicate=samePane+SessionCandidate(42,"cookie","other-name","A")+SessionCandidate(2,"cookie","qid","B")
        assertEquals(setOf("A"),SessionAuditPolicy.duplicates(duplicate))
        assertEquals(2,SessionAuditPolicy.distinctTotal(duplicate))
    }
}
