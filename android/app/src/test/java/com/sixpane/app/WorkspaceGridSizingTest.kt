package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class WorkspaceGridSizingTest {
    @Test fun cardsFitThePaddedScreenExactlyWithSymmetricMargins(){
        for((w,columns) in listOf(690 to 2,1080 to 2,1900 to 3,1260 to 3)){
            val padding=30
            val margin=11
            val widths=WorkspaceGridSizing.cardWidths(w,padding,margin,columns)
            assertEquals(columns,widths.size)
            assertEquals(w,widths.sum()+padding*2+columns*2*margin)
            assertTrue(widths.maxOrNull()!!-widths.minOrNull()!!<=1)
            widths.forEach{cardWidth->
                val tileWidth=WorkspaceGridSizing.previewWidths(cardWidth,12,3)
                assertEquals(cardWidth,tileWidth.sum()+2*12+4*3)
                assertTrue(tileWidth.maxOrNull()!!-tileWidth.minOrNull()!!<=1)
            }
        }
    }
}
