package app.tessera.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * A WebView that always reports itself visible to Chromium, so pages in other
 * workspaces, behind the lock screen, or with the app in the background keep
 * running timers and network activity at full rate instead of being throttled
 * as "hidden". The app never calls onPause()/pauseTimers() on these views.
 */
@SuppressLint("ViewConstructor")
class PaneWebView(context: Context, val paneId: String) : WebView(context) {
    var keepAwake = true
    /** uptimeMillis when the page's own scroller was last pulled past its top. */
    var lastTopOverscroll = -1L
        private set

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedY && scrollY <= 0) lastTopOverscroll = android.os.SystemClock.uptimeMillis()
    }

    fun msSinceTopOverscroll(): Long =
        if (lastTopOverscroll < 0) -1 else android.os.SystemClock.uptimeMillis() - lastTopOverscroll

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepAwake) View.VISIBLE else visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, if (keepAwake) View.VISIBLE else visibility)
    }
}
