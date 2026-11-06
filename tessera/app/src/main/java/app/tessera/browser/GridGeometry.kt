package app.tessera.browser

import kotlin.math.roundToInt

data class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w get() = r - l
    val h get() = b - t
    fun lerp(to: Box, f: Float) = Box(
        (l + (to.l - l) * f).roundToInt(), (t + (to.t - t) * f).roundToInt(),
        (r + (to.r - r) * f).roundToInt(), (b + (to.b - b) * f).roundToInt()
    )
}

/** Pure layout math for the six slots of a workspace. Unit tested. */
object GridGeometry {
    const val SLOTS = 6

    /** Auto layout: wide/square area -> 3 columns x 2 rows, tall area -> 2 x 3. */
    fun autoColumns(w: Int, h: Int): Int = if (w >= h * 0.95f) 3 else 2

    fun grid(w: Int, h: Int, cols: Int, gap: Int): List<Box> {
        val rows = SLOTS / cols
        val cw = (w - gap * (cols - 1)).toFloat() / cols
        val ch = (h - gap * (rows - 1)).toFloat() / rows
        return (0 until SLOTS).map { i ->
            val c = i % cols; val r = i / cols
            val l = (c * (cw + gap)).roundToInt(); val t = (r * (ch + gap)).roundToInt()
            Box(l, t, if (c == cols - 1) w else (l + cw).roundToInt(), if (r == rows - 1) h else (t + ch).roundToInt())
        }
    }

    /**
     * One slot takes most of the area; the other five compress into a strip.
     * Tall screens put the strip along the bottom (closest to the thumb); wide
     * screens put it in a right-hand column.
     */
    fun expanded(w: Int, h: Int, focus: Int, gap: Int, stripPx: Int): List<Box> {
        val others = (0 until SLOTS).filter { it != focus }
        val out = arrayOfNulls<Box>(SLOTS)
        if (h >= w * 1.1f) {
            val stripH = stripPx.coerceAtMost(h / 3)
            out[focus] = Box(0, 0, w, h - stripH - gap)
            val mw = (w - gap * 4).toFloat() / 5
            others.forEachIndexed { k, s ->
                val l = (k * (mw + gap)).roundToInt()
                out[s] = Box(l, h - stripH, if (k == 4) w else (l + mw).roundToInt(), h)
            }
        } else {
            val stripW = (stripPx * 1.3f).roundToInt().coerceAtMost(w / 3)
            out[focus] = Box(0, 0, w - stripW - gap, h)
            val mh = (h - gap * 4).toFloat() / 5
            others.forEachIndexed { k, s ->
                val t = (k * (mh + gap)).roundToInt()
                out[s] = Box(w - stripW, t, w, if (k == 4) h else (t + mh).roundToInt())
            }
        }
        return out.map { it!! }
    }
}
