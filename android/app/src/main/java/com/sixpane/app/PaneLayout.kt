package com.sixpane.app

/** Geometry only: moving between grid and focus never changes a browser or its profile. */
data class PaneBounds(val x:Int,val y:Int,val width:Int,val height:Int)
object PaneLayout {
    fun bounds(width:Int,height:Int,gap:Int,columns:Int,focus:Int?,minStackWidth:Int,minMainWidth:Int):List<PaneBounds>{
        require(columns==2||columns==3)
        require(focus==null||focus in 0..5)
        if(focus==null){
            val rows=6/columns
            val w=(width-gap*(columns+1))/columns
            val h=(height-gap*(rows+1))/rows
            return List(6){i->PaneBounds(gap+(i%columns)*(w+gap),gap+(i/columns)*(h+gap),w,h)}
        }
        val stackWidth=(width*.235f).toInt().coerceAtLeast(minStackWidth)
        val mainWidth=(width-stackWidth-gap*3).coerceAtLeast(minMainWidth)
        val mainHeight=height-gap*2
        val stackHeight=(mainHeight-gap*4)/5
        var row=0
        return List(6){i->
            if(i==focus)PaneBounds(gap,gap,mainWidth,mainHeight)
            else PaneBounds(mainWidth+gap*2,gap+(row++)*(stackHeight+gap),stackWidth,stackHeight)
        }
    }
}
