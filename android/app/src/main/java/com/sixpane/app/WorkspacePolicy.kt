package com.sixpane.app

/** Android-independent policy, exercised by JVM tests. No fingerprint fabrication. */
object WorkspacePolicy {
    const val MAX_WORKSPACES = 7
    const val PANE_COUNT = 6
    const val MIN_ZOOM = 50
    const val MAX_ZOOM = 200
    const val ZOOM_STEP = 10
    fun canCreate(count: Int): Boolean = count in 0 until MAX_WORKSPACES
    fun canClose(tabLocked: Boolean): Boolean = !tabLocked
    /** Protect an entire workspace whenever its tab OR any contained browser is locked. */
    fun canCloseWorkspace(tabLocked:Boolean,paneLocks:List<Boolean>):Boolean =
        !tabLocked && !paneLocks.any{it}
    fun canCloseSelected(count:Int, anyLocked:Boolean):Boolean=count>0&&!anyLocked
    fun selectionActionEnabled(count:Int):Boolean=count>0
    fun canReset(paneLocked: Boolean): Boolean = !paneLocked
    fun canChangeDesktop(paneLocked: Boolean): Boolean = !paneLocked
    fun canModifyCookies(paneLocked: Boolean): Boolean = !paneLocked
    fun zoom(old: Int, direction: Int): Int = (old + direction * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
    fun displayZoom(zoom: Int): String = if (zoom == 100) "" else "$zoom%"
    fun normalViewScale(percent: Int): Float = percent.coerceIn(96, 104) / 100f
}
