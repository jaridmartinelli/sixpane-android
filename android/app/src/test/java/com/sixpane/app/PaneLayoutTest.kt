package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class PaneLayoutTest {
    @Test fun gridsAndFocusFitWithoutOverlap(){
        for((w,h) in listOf(360 to 700,700 to 360,800 to 1000))for(columns in 2..3)for(focus in listOf(null,0,1,2,3,4,5)){
            val panes=PaneLayout.bounds(w,h,2,columns,focus,72,80)
            assertEquals(6,panes.size)
            panes.forEach{assertTrue(it.width>0&&it.height>0);assertTrue(it.x>=0&&it.y>=0&&it.x+it.width<=w&&it.y+it.height<=h)}
            panes.forEachIndexed{i,a->panes.drop(i+1).forEach{b->assertFalse(a.x<b.x+b.width&&b.x<a.x+a.width&&a.y<b.y+b.height&&b.y<a.y+a.height)}}
            if(focus!=null){val main=panes[focus];assertEquals(h-4,main.height);assertTrue(panes.filterIndexed{i,_->i!=focus}.all{it.x>main.x+main.width&&it.width<main.width})}
        }
    }
    @Test fun restoreReturnsOriginalGridAfterResizing(){
        for(columns in 2..3){
            val original=PaneLayout.bounds(360,700,2,columns,null,72,80)
            for(focus in 0..5){PaneLayout.bounds(700,360,2,columns,focus,72,80);assertEquals(original,PaneLayout.bounds(360,700,2,columns,null,72,80))}
        }
    }
}
