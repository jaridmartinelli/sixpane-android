package com.sixpane.app

/** Pixel-balanced geometry used by workspace cards and their two-column previews. */
object WorkspaceGridSizing {
    fun cardWidths(screenWidthPx:Int,bodySidePaddingPx:Int,cardMarginPx:Int,
                   columns:Int):List<Int> {
        require(columns in 2..3)
        val usable=(screenWidthPx-2*bodySidePaddingPx-2*columns*cardMarginPx)
            .coerceAtLeast(columns)
        val base=usable/columns
        val extra=usable%columns
        return List(columns){base+if(it<extra)1 else 0}
    }

    fun previewWidths(cardWidthPx:Int,cardSidePaddingPx:Int,tileMarginPx:Int):List<Int> {
        val usable=(cardWidthPx-2*cardSidePaddingPx-4*tileMarginPx)
            .coerceAtLeast(2)
        return listOf(usable/2,usable-usable/2)
    }
}
