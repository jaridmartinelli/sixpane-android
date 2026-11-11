package com.sixpane.app

/** Geometry only: moving between grid and focus never changes a browser or its profile. */
data class PaneBounds(val x:Int,val y:Int,val width:Int,val height:Int)
object PaneLayout {
    private fun balancedSizes(total:Int,count:Int,interiorGap:Int,
                              exteriorGap:Int):IntArray {
        val usable=(total-2*exteriorGap-(count-1)*interiorGap).coerceAtLeast(count)
        val base=usable/count
        val extra=usable%count
        return IntArray(count){base+if(it<extra)1 else 0}
    }

    fun visibleBounds(width:Int,height:Int,gap:Int,columns:Int,count:Int,
        focus:Int?,minStackWidth:Int,minMainWidth:Int):List<PaneBounds> {
        require(columns==2||columns==3)
        require(count in 0..6)
        require(focus==null||focus in 0 until count)
        if(count==0)return emptyList()
        if(focus==null){
            val cols=columns.coerceAtMost(count)
            val rows=(count+cols-1)/cols
            val widths=balancedSizes(width,cols,gap,gap)
            val heights=balancedSizes(height,rows,gap,gap)
            val xStarts=IntArray(cols)
            val yStarts=IntArray(rows)
            for(c in 0 until cols)xStarts[c]=gap+(0 until c).sumOf{widths[it]+gap}
            for(r in 0 until rows)yStarts[r]=gap+(0 until r).sumOf{heights[it]+gap}
            return List(count){i->
                val col=i%cols;val row=i/cols
                PaneBounds(xStarts[col],yStarts[row],widths[col],heights[row])
            }
        }
        if(count==1)return listOf(PaneBounds(gap,gap,
            (width-gap*2).coerceAtLeast(1),(height-gap*2).coerceAtLeast(1)))
        val tall=height.toDouble()>=1.1*width.toDouble()
        if(tall){
            val strip=minStackWidth.coerceAtMost((height/3).coerceAtLeast(1))
            val mainH=(height-strip-gap*3).coerceAtLeast(1)
            val widths=balancedSizes(width,count-1,gap,gap)
            var nextThumb=0
            return List(count){i->
                if(i==focus)PaneBounds(gap,gap,(width-gap*2).coerceAtLeast(1),mainH)
                else {
                    val col=nextThumb++
                    val x=gap+(0 until col).sumOf{widths[it]+gap}
                    PaneBounds(x,mainH+gap*2,widths[col],strip)
                }
            }
        }
        val stackWidth=(minStackWidth*1.3f).toInt()
            .coerceAtMost((width/3).coerceAtLeast(1))
        val mainWidth=(width-stackWidth-gap*3).coerceAtLeast(1)
        val mainHeight=(height-gap*2).coerceAtLeast(1)
        val smallHeights=balancedSizes(mainHeight,count-1,gap,0)
        var nextThumb=0
        return List(count){i->
            if(i==focus)PaneBounds(gap,gap,mainWidth,mainHeight)
            else {
                val row=nextThumb++
                val y=gap+(0 until row).sumOf{smallHeights[it]+gap}
                PaneBounds(mainWidth+gap*2,y,stackWidth,smallHeights[row])
            }
        }
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
