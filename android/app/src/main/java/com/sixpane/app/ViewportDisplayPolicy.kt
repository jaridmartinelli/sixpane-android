package com.sixpane.app

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A uniformly scaled real WebView, not a warped screenshot or an altered CSS transform.
 * The display pane remains the same size. Wider logical layouts retain a usable height.
 * Small bounded per-pane viewport differences are retained without distorting content.
 */
object ViewportDisplayPolicy {
    data class Size(val webWidthPx:Int,val webHeightPx:Int,val scale:Float)

    fun fit(visibleWidthPx:Int,visibleHeightPx:Int,minSmallWidthPx:Int,
            fullSizeSmallPanes:Boolean,widthPercent:Int,heightPercent:Int,
            paneOrdinal:Int):Size {
        require(visibleWidthPx>0 && visibleHeightPx>0)
        require(paneOrdinal in 1..WorkspacePolicy.SLOT_CAPACITY)
        val baseScale=WorkspacePolicy.normalViewScale(widthPercent)
        val minimumWidth=if(fullSizeSmallPanes && visibleWidthPx<minSmallWidthPx)
            minSmallWidthPx else visibleWidthPx
        // Both persisted viewport percentages contribute, but no percentage
        // becomes a mismatched horizontal/vertical visual scale.
        val heightRatioBias=1f+(100-heightPercent.coerceIn(96,104))*.001f
        val pageW=max(visibleWidthPx,
            (minimumWidth/baseScale*heightRatioBias).roundToInt())
        val uniformScale=visibleWidthPx.toFloat()/pageW
        val heightVariationPx=paneOrdinal-(WorkspacePolicy.SLOT_CAPACITY/2)
        val pageH=max(1,(visibleHeightPx/uniformScale).roundToInt()+heightVariationPx)
        return Size(pageW,pageH,uniformScale)
    }
}
