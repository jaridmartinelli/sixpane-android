package app.tessera.browser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

/** Single dark theme. Secondary icons/text use ~70% white. */
object T {
    const val bg = 0xFF0B0E13.toInt()
    const val surface = 0xFF141920.toInt()
    const val surface2 = 0xFF1C222B.toInt()
    const val surface3 = 0xFF252C37.toInt()
    const val stroke = 0xFF2B3340.toInt()
    const val text = 0xFFEEF2F7.toInt()
    const val text2 = 0xB3FFFFFF.toInt()
    const val text3 = 0x73FFFFFF
    const val accent = 0xFF7EA6FF.toInt()
    const val accentSoft = 0x297EA6FF
    const val lock = 0xFFFF4D57.toInt()
    const val lockSoft = 0x33FF4D57
    const val select = 0xFF3DDC97.toInt()
    const val selectSoft = 0x2E3DDC97
    const val danger = 0xFFFF6B6B.toInt()
    const val ok = 0xFF3DDC97.toInt()

    /** The user's six tag colours, exactly as supplied (sampled from their swatch images). */
    val palette = intArrayOf(
        0xFF204DDA.toInt(), 0xFF59B435.toInt(), 0xFFB5381C.toInt(),
        0xFFC39533.toInt(), 0xFF9A9D32.toInt(), 0xFF7CC5B4.toInt(),
    )
    val paletteNames = listOf("Blue", "Green", "Red", "Gold", "Olive", "Teal")
    fun color(index: Int): Int? = palette.getOrNull(index)

    /** Readable text/icon colour on top of [bg]: white on dark tags, near-black on light ones. */
    fun ink(bg: Int, alpha: Int = 0xFF): Int {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        val l = 0.2126 * lin((bg shr 16) and 0xFF) + 0.7152 * lin((bg shr 8) and 0xFF) + 0.0722 * lin(bg and 0xFF)
        // Pick whichever of white / near-black gives the higher contrast ratio.
        val vsWhite = 1.05 / (l + 0.05); val vsDark = (l + 0.05) / (0.0045 + 0.05)
        val base = if (vsWhite >= vsDark) 0xFFFFFF else 0x0B0E13
        return (alpha shl 24) or base
    }
    fun colorName(index: Int): String = paletteNames.getOrElse(index) { "None" }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun View.dp(v: Int): Int = context.dp(v)
fun View.dpf(v: Float): Float = context.dpf(v)

fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun roundedTop(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
}

/** Pressed ripple, bounded to a rounded shape. */
fun ripple(base: Drawable?, radius: Float, rippleColor: Int = 0x33FFFFFF): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(rippleColor), base, rounded(Color.WHITE, radius))

fun Context.label(text: String, size: Float, color: Int = T.text, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
    }

/** Square-ish icon button with a 44dp+ touch target, subtle rounded press state. */
fun Context.iconButton(icon: Icon, description: String, sizeDp: Int = 44, tint: Int = T.text2, onClick: (View) -> Unit): ImageView =
    ImageView(this).apply {
        setImageDrawable(IconDrawable(icon, tint))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(((sizeDp - 22) / 2).coerceAtLeast(6))
        setPadding(pad, pad, pad, pad)
        background = ripple(ColorDrawable(Color.TRANSPARENT), dpf(10f))
        contentDescription = description
        tooltipText = description
        minimumWidth = dp(sizeDp); minimumHeight = dp(sizeDp)
        setOnClickListener(onClick)
        Motion.pressable(this, 0.9f)
    }

fun Context.pillButton(text: String, filled: Boolean, danger: Boolean = false, onClick: (View) -> Unit): TextView =
    label(text, 14f, if (filled) (if (danger) Color.WHITE else 0xFF0B0E13.toInt()) else (if (danger) T.danger else T.text), bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        minHeight = dp(44); minWidth = dp(88)
        val base = if (filled) rounded(if (danger) T.danger else T.accent, dpf(12f))
            else rounded(T.surface3, dpf(12f))
        background = ripple(base, dpf(12f))
        setOnClickListener(onClick)
        Motion.pressable(this, 0.96f)
    }
