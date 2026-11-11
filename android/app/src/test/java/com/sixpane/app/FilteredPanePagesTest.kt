package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class FilteredPanePagesTest {
    @Test fun fourteenMatchesAreRepackedIntoSixSixTwo(){
        val all=(1..14).toList()
        val pages=FilteredPanePages.pages(all)
        assertEquals(3,pages.size)
        assertEquals(listOf(6,6,2),pages.map{it.size})
        assertEquals((1..6).toList(),pages[0])
        assertEquals((7..12).toList(),pages[1])
        assertEquals(listOf(13,14),pages[2])
        assertEquals(2,FilteredPanePages.maxPageIndex(14))
    }

    @Test fun noMatchesHaveNoFakeTabsAndDontMutateOrigins(){
        assertEquals(0,FilteredPanePages.pages(emptyList<Int>()).size)
        assertEquals(emptyList<Int>(),FilteredPanePages.page((1..14).toList(),9))
        assertEquals(0,FilteredPanePages.maxPageIndex(0))
        val originals=(1..42).toList()
        val filtered=originals.filter{it%3==0}
        assertEquals(42,originals.size)
        assertEquals(14,filtered.size)
        assertEquals(listOf(6,6,2),FilteredPanePages.pages(filtered).map{it.size})
    }

    @Test fun partialFilteredPageKeepsFullSixCellGridPositions(){
        for(columns in 2..3){
            val six=PaneLayout.visibleBounds(360,720,2,columns,6,null,54,80)
            for(count in 1..5){
                val partial=FilteredPanePages.bounds(360,720,2,columns,count,null,54,80)
                assertEquals(six.take(count),partial)
                // Remainder pixels are distributed between cells to keep
                // both outer edges and every gap equal; sizes differ by <=1px.
                assertTrue(partial.all{
                    kotlin.math.abs(it.width-six[0].width)<=1 &&
                    kotlin.math.abs(it.height-six[0].height)<=1
                })
            }
            assertTrue(FilteredPanePages.bounds(360,720,2,columns,0,null,54,80).isEmpty())
        }
    }

    @Test fun focusDoesNotRebuildOrResizeSixSlotModel(){
        for(count in 1..6){
            val focused=FilteredPanePages.bounds(700,1000,2,2,count,0,54,80)
            val full=PaneLayout.visibleBounds(700,1000,2,2,6,0,54,80)
            assertEquals(full.take(count),focused)
        }
    }
}
