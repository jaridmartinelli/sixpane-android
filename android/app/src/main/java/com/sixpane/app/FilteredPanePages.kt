package com.sixpane.app

/**
 * Read-only filtered display model. The panes stay in their original workspaces
 * and keep the same named WebView profiles/cookies/bookmarks.
 *
 * A result page always uses the geometry of SIX slots, not the number of
 * matches. This leaves unused grid cells transparent to the stage background.
 */
object FilteredPanePages {
    const val SLOTS=6

    fun <T> pages(matches:List<T>):List<List<T>> = matches.chunked(SLOTS)

    fun <T> page(matches:List<T>,pageIndex:Int):List<T> =
        pages(matches).getOrNull(pageIndex).orEmpty()

    fun maxPageIndex(matches:Int):Int =
        if(matches==0)0 else (matches-1)/SLOTS

    fun bounds(
        width:Int,height:Int,gap:Int,columns:Int,
        visibleCount:Int,focusedIndex:Int?,minStackWidth:Int,minMainWidth:Int
    ):List<PaneBounds>{
        require(visibleCount in 0..SLOTS)
        require(focusedIndex==null || focusedIndex in 0 until visibleCount)
        if(visibleCount==0)return emptyList()
        val sixSlots=PaneLayout.visibleBounds(
            width,height,gap,columns,SLOTS,focusedIndex,minStackWidth,minMainWidth
        )
        return sixSlots.take(visibleCount)
    }
}
