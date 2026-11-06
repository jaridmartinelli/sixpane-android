package app.tessera.browser

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** 24x24 Material-style glyphs drawn from SVG path data (no resources needed). */
enum class Icon(val data: String) {
    FORWARD("M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z"),
    BACK("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"),
    RELOAD("M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"),
    CLOSE("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"),
    MORE("M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"),
    LOCK("M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"),
    UNLOCK("M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM18,20H6V10h12v10z"),
    ADD("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
    REMOVE("M19,13H5v-2h14v2z"),
    CHECK("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"),
    STAR("M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"),
    STAR_OUTLINE("M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"),
    SEARCH("M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"),
    EXPAND("M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z"),
    COLLAPSE("M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,5h-2v5h5V8h-3V5z"),
    FILTER("M10,18h4v-2h-4v2zM3,6v2h18V6H3zM6,13h12v-2H6v2z"),
    KEY("M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"),
    COPY("M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"),
    DESKTOP("M21,2H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h7v2H8v2h8v-2h-2v-2h7c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM21,16H3V4h18v12z"),
    TAG("M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z"),
    SELECT("M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.11,0 2,-0.9 2,-2V5c0,-1.1 -0.89,-2 -2,-2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"),
    DELETE("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"),
    POWER("M13,3h-2v10h2L13,3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z"),
    SHIELD("M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z"),
    PASTE("M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z"),
    LINK("M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"),
    GRID("M4,11h5V5H4v6zM4,18h5v-6H4v6zM10,18h5v-6h-5v6zM16,18h5v-6h-5v6zM10,11h5V5h-5v6zM16,5v6h5V5h-5z"),
    SETTINGS("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"),
    PULSE("M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99z"),
    ERROR("M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z"),
    BOOKMARKS("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z"),
    ZOOM("M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14zM12,10h-2v2H9v-2H7V9h2V7h1v2h2v1z");

    val path: Path by lazy { SvgPath.parse(data) }
}

class IconDrawable(private val icon: Icon, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val scaled = Path()
    private val m = Matrix()
    var color: Int
        get() = paint.color
        set(v) { paint.color = v; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        m.reset(); m.setScale(s, s)
        m.postTranslate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f)
        icon.path.transform(m, scaled)
        canvas.drawPath(scaled, paint)
    }
    override fun getIntrinsicWidth() = 66
    override fun getIntrinsicHeight() = 66
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Minimal SVG path-data parser: M L H V C S Q T Z (absolute and relative). */
object SvgPath {
    private val token = Regex("[MmLlHhVvCcSsQqTtZz]|-?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")

    fun parse(d: String): Path {
        val p = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val tk = token.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f; var y = 0f; var sx = 0f; var sy = 0f
        var cx = 0f; var cy = 0f // last control point
        var last = ' '
        fun num(): Float = tk[i++].toFloat()
        fun isNum() = i < tk.size && !tk[i][0].isLetter()
        while (i < tk.size) {
            if (!isNum()) { cmd = tk[i++][0] }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    val nx = num() + if (rel) x else 0f; val ny = num() + if (rel) y else 0f
                    p.moveTo(nx, ny); x = nx; y = ny; sx = nx; sy = ny
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> { val nx = num() + if (rel) x else 0f; val ny = num() + if (rel) y else 0f; p.lineTo(nx, ny); x = nx; y = ny }
                'H' -> { val nx = num() + if (rel) x else 0f; p.lineTo(nx, y); x = nx }
                'V' -> { val ny = num() + if (rel) y else 0f; p.lineTo(x, ny); y = ny }
                'C' -> {
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    val x1 = num() + ox; val y1 = num() + oy; val x2 = num() + ox; val y2 = num() + oy
                    val nx = num() + ox; val ny = num() + oy
                    p.cubicTo(x1, y1, x2, y2, nx, ny); cx = x2; cy = y2; x = nx; y = ny
                }
                'S' -> {
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    val x1 = if (last.uppercaseChar() == 'C' || last.uppercaseChar() == 'S') 2 * x - cx else x
                    val y1 = if (last.uppercaseChar() == 'C' || last.uppercaseChar() == 'S') 2 * y - cy else y
                    val x2 = num() + ox; val y2 = num() + oy; val nx = num() + ox; val ny = num() + oy
                    p.cubicTo(x1, y1, x2, y2, nx, ny); cx = x2; cy = y2; x = nx; y = ny
                }
                'Q' -> {
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    val x1 = num() + ox; val y1 = num() + oy; val nx = num() + ox; val ny = num() + oy
                    p.quadTo(x1, y1, nx, ny); cx = x1; cy = y1; x = nx; y = ny
                }
                'T' -> {
                    val ox = if (rel) x else 0f; val oy = if (rel) y else 0f
                    val x1 = if (last.uppercaseChar() == 'Q' || last.uppercaseChar() == 'T') 2 * x - cx else x
                    val y1 = if (last.uppercaseChar() == 'Q' || last.uppercaseChar() == 'T') 2 * y - cy else y
                    val nx = num() + ox; val ny = num() + oy
                    p.quadTo(x1, y1, nx, ny); cx = x1; cy = y1; x = nx; y = ny
                }
                'Z' -> { p.close(); x = sx; y = sy }
                else -> i++
            }
            last = cmd
        }
        return p
    }
}
