package app.tessera.browser

/**
 * Pull-to-refresh, Chrome-style: only when the page is at its top AND nothing under
 * the finger is a scrolled-down inner box (that box should scroll instead), and never
 * on a locked pane. The inner-box answer comes from the page asynchronously; until it
 * arrives, refresh is held back. Pure; unit tested.
 */
object PullRefreshPolicy {
    enum class Inner { UNKNOWN, AT_TOP, SCROLLED }

    fun allow(locked: Boolean, pageScrollY: Int, inner: Inner): Boolean =
        !locked && pageScrollY <= 0 && inner == Inner.AT_TOP

    /** JS: 1 if an element under (x,y) CSS px is a scrollable box scrolled away from its top. */
    fun probeScript(xCss: Float, yCss: Float): String = """(function(x,y){var e=document.elementFromPoint(x,y);
        while(e&&e!==document.body&&e!==document.documentElement){var s=getComputedStyle(e).overflowY;
        if((s=='auto'||s=='scroll'||s=='overlay')&&e.scrollHeight>e.clientHeight&&e.scrollTop>0)return 1;e=e.parentElement}
        return (window.scrollY>0)?1:0})($xCss,$yCss)"""
}
