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
    /** Whether the content under the last touch can still scroll up (see PullRefreshPolicy). */
    @Volatile var inner = PullRefreshPolicy.Inner.UNKNOWN
        private set

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            inner = PullRefreshPolicy.Inner.UNKNOWN
            @Suppress("DEPRECATION") val s = scale.takeIf { it > 0f } ?: 1f
            runCatching {
                evaluateJavascript(PullRefreshPolicy.probeScript(ev.x / s, ev.y / s)) { r ->
                    inner = if (r?.trim() == "0") PullRefreshPolicy.Inner.AT_TOP else PullRefreshPolicy.Inner.SCROLLED
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepAwake) View.VISIBLE else visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, if (keepAwake) View.VISIBLE else visibility)
    }
}
