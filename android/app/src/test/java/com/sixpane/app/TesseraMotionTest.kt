package com.sixpane.app

import org.junit.Assert.assertTrue
import org.junit.Test

class TesseraMotionTest {
    @Test fun transitionsNeverExceedSixHundredMilliseconds(){
        assertTrue(TesseraMotion.transitions.isNotEmpty())
        assertTrue(TesseraMotion.transitions.all{it in 0L..600L})
    }
}
