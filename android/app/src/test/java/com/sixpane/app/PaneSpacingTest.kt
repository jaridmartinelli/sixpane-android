package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class PaneSpacingTest {
    @Test fun everyGridHasEqualGapsAndBalancedEdges(){
        for(w in 269..715 step 13)for(h in 431..900 step 19)
            for(columns in 2..3){
                val gap=4
                val cells=PaneLayout.visibleBounds(w,h,gap,columns,6,null,78,80)
                val xSorted=cells.take(columns).sortedBy{it.x}
                assertEquals(gap,xSorted.first().x)
                assertEquals(w-gap,xSorted.last().let{it.x+it.width})
                for(i in 1 until columns)
                    assertEquals(gap,xSorted[i].x-xSorted[i-1].x-xSorted[i-1].width)
                val rows=6/columns
                val ySorted=(0 until rows).map{cells[it*columns]}.sortedBy{it.y}
                assertEquals(gap,ySorted.first().y)
                assertEquals(h-gap,ySorted.last().let{it.y+it.height})
                for(i in 1 until rows)
                    assertEquals(gap,ySorted[i].y-ySorted[i-1].y-ySorted[i-1].height)
                assertTrue(cells.maxOf{it.width}-cells.minOf{it.width}<=1)
                assertTrue(cells.maxOf{it.height}-cells.minOf{it.height}<=1)
            }
    }

    @Test fun focusedThumbnailsAreEvenlySpacedAndFillAvailableRow(){
        val gap=4
        for(w in 360..720 step 17){
            val cells=PaneLayout.visibleBounds(w,1200,gap,2,6,2,78,80)
            val thumbs=cells.filterIndexed{index,_->index!=2}.sortedBy{it.x}
            assertEquals(gap,thumbs.first().x)
            assertEquals(w-gap,thumbs.last().let{it.x+it.width})
            for(i in 1 until thumbs.size)
                assertEquals(gap,thumbs[i].x-thumbs[i-1].x-thumbs[i-1].width)
        }
    }
}
