package app.tessera.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.min

/**
 * Pull-down-to-reload for one pane. Unlike a stock refresh layout it can start
 * part-way through a drag, so it can wait for the page's answer to "is a scrolled
 * box under the finger?" (asked on touch-down) before taking over the gesture.
 */
@SuppressLint("ViewConstructor")
class PullHost(context: Context, private val canStart: () -> Boolean, private val onRefresh: () -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val threshold = context.dpf(72f)
    private val maxPull = context.dpf(120f)
    private val indicator = ImageView(context)
    private var startX = 0f
    private var startY = 0f
    private var pulling = false
    var refreshing = false
        private set

    init {
        indicator.setImageDrawable(IconDrawable(Icon.RELOAD, T.bg))
        indicator.background = rounded(T.text, context.dpf(18f))
        val pad = context.dp(8); indicator.setPadding(pad, pad, pad, pad)
        indicator.elevation = context.dpf(6f)
        indicator.alpha = 0f
        addView(indicator, LayoutParams(context.dp(36), context.dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    override fun onViewAdded(child: android.view.View?) {
        super.onViewAdded(child)
        if (child !== indicator) indicator.bringToFront()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || refreshing) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = ev.x; startY = ev.y; pulling = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - startY; val dx = ev.x - startX
                if (!pulling && dy > slop && dy > abs(dx) * 1.5f && canStart()) {
                    pulling = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!pulling) return super.onTouchEvent(ev)
        val pull = min((ev.y - startY) * 0.5f, maxPull).coerceAtLeast(0f)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> show(pull)
            MotionEvent.ACTION_UP -> { pulling = false; if (pull >= threshold) startRefresh() else hide() }
            MotionEvent.ACTION_CANCEL -> { pulling = false; hide() }
        }
        return true
    }

    private fun show(pull: Float) {
        indicator.animate().cancel()
        indicator.translationY = pull - context.dpf(28f)
        indicator.alpha = min(1f, pull / threshold)
        indicator.rotation = pull * 3f
    }

    private fun startRefresh() {
        refreshing = true
        indicator.translationY = threshold - context.dpf(28f); indicator.alpha = 1f
        // A status indicator, not a transition: it spins until the page finishes loading.
        spin = android.animation.ObjectAnimator.ofFloat(indicator, ROTATION, indicator.rotation, indicator.rotation + 360f).apply {
            duration = 800; repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator(); start()
        }
        onRefresh()
    }

    /** Called when the page finished loading (or refresh was not possible). */
    fun done() {
        if (!refreshing && indicator.alpha == 0f) return
        refreshing = false
        hide()
    }

    private var spin: android.animation.ObjectAnimator? = null

    private fun hide() {
        spin?.cancel(); spin = null
        indicator.animate().cancel()
        indicator.animate().alpha(0f).translationY(-context.dpf(28f)).setDuration(Motion.EXIT_MS)
            .setInterpolator(Motion.accelerate).start()
    }
}
