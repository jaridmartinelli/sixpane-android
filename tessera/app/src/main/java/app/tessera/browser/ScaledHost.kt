package app.tessera.browser

import android.content.Context
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * Hosts one child. When narrower than [minWidthPx], the child is laid out at
 * that width (keeping the aspect ratio) and scaled down to fit, so websites
 * render their normal phone layout instead of an oversized narrow one.
 */
class ScaledHost(context: Context) : FrameLayout(context) {
    var minWidthPx = 0
        set(v) { if (field != v) { field = v; requestLayout() } }

    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        scale = if (minWidthPx > 0 && w in 1 until minWidthPx) w.toFloat() / minWidthPx else 1f
        val vw = (w / scale).roundToInt().coerceAtLeast(1)
        val vh = (h / scale).roundToInt().coerceAtLeast(1)
        for (i in 0 until childCount) getChildAt(i).measure(
            MeasureSpec.makeMeasureSpec(vw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(vh, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).apply {
            layout(0, 0, measuredWidth, measuredHeight)
            pivotX = 0f; pivotY = 0f; scaleX = scale; scaleY = scale
        }
    }
}
