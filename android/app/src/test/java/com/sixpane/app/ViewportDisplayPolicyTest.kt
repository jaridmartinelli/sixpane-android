package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ViewportDisplayPolicyTest {
    @Test fun smallPaneUsesAProportional320dpLayoutRatherThanVerticalStretch(){
        val size=ViewportDisplayPolicy.fit(320,390,884,true,96,103,17)
        assertTrue("Logical width should remain at least 320dp",size.webWidthPx>=884)
        assertEquals(320f/size.webWidthPx,size.scale,0.0001f)
        assertTrue("Uniformly scaled viewport must fill height within 22px",
            abs(size.webHeightPx*size.scale-390f)<=22f)
        assertTrue(size.webHeightPx>390)
    }

    @Test fun orientationAndExpandedModeAvoidLargeEmptyOrClippedBands(){
        val samples=listOf(
            Triple(338,390,884),Triple(340,420,884),
            Triple(660,1010,884),Triple(1080,1800,884),
            Triple(700,360,884),Triple(320,250,884)
        )
        for((w,h,minW) in samples)for(i in 1..42){
            val layout=ViewportDisplayPolicy.fit(w,h,minW,true,96,104,i)
            val remaining=abs(layout.webHeightPx*layout.scale-h)
            assertTrue("Unfilled/cropped band too large: $w x $h pane $i -> $remaining px",
                remaining<=22f)
            assertTrue(layout.webWidthPx>=w)
        }
    }

    @Test fun fortyTwoIndependentViewportRatiosRemainDistinct(){
        val legacy=listOf(96 to 98,97 to 103,98 to 102,
            102 to 104,103 to 96,104 to 97)
        val pairs=mutableListOf<Pair<Int,Int>>()
        repeat(7){pairs+=ViewportRatioPolicy.allocate(pairs,legacy)}
        for((w,h,minW) in listOf(Triple(340,430,880),
            Triple(360,700,880),Triple(700,360,880),
            Triple(1080,1800,880),Triple(160,640,880))){
            val sizes=pairs.mapIndexed{index,(widthPercent,heightPercent)->
                val size=ViewportDisplayPolicy.fit(w,h,minW,true,
                    widthPercent,heightPercent,index+1)
                ViewportRatioPolicy.ratioKey(size.webWidthPx,size.webHeightPx)
            }
            assertEquals("All 42 should retain different browser viewport ratios",
                42,sizes.toSet().size)
        }
    }
}
