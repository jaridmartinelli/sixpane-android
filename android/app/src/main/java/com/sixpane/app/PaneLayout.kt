package com.sixpane.app

/** Geometry only: moving between grid and focus never changes a browser or its profile. */
data class PaneBounds(val x:Int,val y:Int,val width:Int,val height:Int)
object PaneLayout {
    fun visibleBounds(width:Int,height:Int,gap:Int,columns:Int,count:Int,focus:Int?,minStackWidth:Int,minMainWidth:Int):List<PaneBounds> {
        require(columns==2||columns==3)
        require(count in 0..6)
        require(focus==null||focus in 0 until count)
        if(count==0)return emptyList()
        if(focus==null){
            val cols=columns.coerceAtMost(count)
            val rows=(count+cols-1)/cols
            val w=(width-gap*(cols+1))/cols
            val h=(height-gap*(rows+1))/rows
            return List(count){i->PaneBounds(gap+(i%cols)*(w+gap),gap+(i/cols)*(h+gap),
                w.coerceAtLeast(1),h.coerceAtLeast(1))}
        }
        if(count==1)return listOf(PaneBounds(gap,gap,(width-gap*2).coerceAtLeast(1),(height-gap*2).coerceAtLeast(1)))
        val tall=height.toDouble()>=1.1*width.toDouble()
        if(tall){
            val strip=minStackWidth.coerceAtMost((height/3).coerceAtLeast(1))
            val mainH=(height-strip-gap*3).coerceAtLeast(1)
            val thumbnailW=((width-gap*(count+1))/(count-1)).coerceAtLeast(1)
            var index=0
            return List(count){i->if(i==focus)PaneBounds(gap,gap,(width-gap*2).coerceAtLeast(1),mainH)
                else PaneBounds(gap+(index++)*(thumbnailW+gap),mainH+gap*2,thumbnailW,strip)}
        }
        val stackWidth=(minStackWidth*1.3f).toInt()
            .coerceAtMost((width/3).coerceAtLeast(1))
        val mainWidth=(width-stackWidth-gap*3).coerceAtLeast(1)
        val mainHeight=(height-gap*2).coerceAtLeast(1)
        val smallH=((mainHeight-gap*(count-2))/(count-1)).coerceAtLeast(1)
        var row=0
        return List(count){i->if(i==focus)PaneBounds(gap,gap,mainWidth,mainHeight)
            else PaneBounds(mainWidth+gap*2,gap+(row++)*(smallH+gap),stackWidth,smallH)}
    }
    fun bounds(width:Int,height:Int,gap:Int,columns:Int,focus:Int?,minStackWidth:Int,minMainWidth:Int):List<PaneBounds>{
        require(columns==2||columns==3)
        require(focus==null||focus in 0..5)
        if(focus==null){
            val rows=6/columns
            val w=(width-gap*(columns+1))/columns
            val h=(height-gap*(rows+1))/rows
            return List(6){i->PaneBounds(gap+(i%columns)*(w+gap),gap+(i/columns)*(h+gap),w,h)}
        }
        val stackWidth=(width*.185f).toInt().coerceAtLeast(minStackWidth)
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
