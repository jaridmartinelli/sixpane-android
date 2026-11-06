package app.tessera.browser

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Bottom sheet anchored to the thumb zone. Disabled rows stay visible and say
 * WHY they are unavailable; tapping them repeats the reason.
 */
class Sheet(private val a: MainActivity, title: String, subtitle: String? = null) {
    val dialog = Dialog(a)
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val root = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedTop(T.surface, a.dpf(22f))
        setPadding(0, a.dp(8), 0, a.dp(12))
    }

    init {
        root.addView(View(a).apply { background = rounded(T.text3, a.dpf(2f)) },
            LinearLayout.LayoutParams(a.dp(36), a.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = a.dp(10) })
        root.addView(a.label(title, 17f, T.text, bold = true).apply { setPadding(a.dp(20), a.dp(2), a.dp(20), 0) })
        if (subtitle != null) root.addView(a.label(subtitle, 12.5f, T.text2).apply {
            setPadding(a.dp(20), a.dp(4), a.dp(20), 0); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        })
        root.addView(ScrollView(a).apply { addView(list); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(8) })
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
        }
    }

    fun item(
        icon: Icon?, label: String, detail: String? = null, enabled: Boolean = true,
        danger: Boolean = false, trailing: String? = null, keepOpen: Boolean = false,
        blockedReason: String? = null, onBlocked: (() -> Unit)? = null, onClick: () -> Unit,
    ): Sheet {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(20), a.dp(4), a.dp(16), a.dp(4))
            minimumHeight = a.dp(52)
            background = ripple(ColorDrawable(Color.TRANSPARENT), 0f)
            alpha = if (enabled) 1f else 0.45f
        }
        val tint = if (danger) T.danger else T.text2
        row.addView(ImageView(a).apply { icon?.let { setImageDrawable(IconDrawable(it, tint)) } },
            LinearLayout.LayoutParams(a.dp(22), a.dp(22)))
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(a.label(label, 15f, if (danger) T.danger else T.text))
        val sub = if (!enabled && blockedReason != null) blockedReason else detail
        if (sub != null) texts.addView(a.label(sub, 12f, if (!enabled) T.lock else T.text3).apply {
            setPadding(0, a.dp(3), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(16) })
        if (trailing != null) row.addView(a.label(trailing, 13f, T.text2))
        row.setOnClickListener {
            if (!enabled) { onBlocked?.invoke(); a.toast(blockedReason ?: "Not available right now"); return@setOnClickListener }
            if (!keepOpen) dialog.dismiss()
            onClick()
        }
        list.addView(row)
        return this
    }

    fun toggle(icon: Icon, label: String, checked: Boolean, enabled: Boolean = true,
               detail: String? = null, blockedReason: String? = null, onBlocked: (() -> Unit)? = null,
               onChange: (Boolean) -> Unit): Sheet {
        val sw = Switch(a).apply { isChecked = checked; isClickable = false; isFocusable = false }
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(20), a.dp(4), a.dp(16), a.dp(4)); minimumHeight = a.dp(52)
            background = ripple(ColorDrawable(Color.TRANSPARENT), 0f)
            alpha = if (enabled) 1f else 0.45f
        }
        row.addView(ImageView(a).apply { setImageDrawable(IconDrawable(icon, T.text2)) }, LinearLayout.LayoutParams(a.dp(22), a.dp(22)))
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(a.label(label, 15f))
        val sub = if (!enabled && blockedReason != null) blockedReason else detail
        if (sub != null) texts.addView(a.label(sub, 12f, if (!enabled) T.lock else T.text3).apply { setPadding(0, a.dp(3), 0, 0) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(16) })
        row.addView(sw)
        row.setOnClickListener {
            if (!enabled) { onBlocked?.invoke(); a.toast(blockedReason ?: "Not available right now"); return@setOnClickListener }
            sw.isChecked = !sw.isChecked; onChange(sw.isChecked)
        }
        list.addView(row)
        return this
    }

    fun section(text: String): Sheet {
        list.addView(a.label(text.uppercase(), 11f, T.text3, bold = true).apply {
            letterSpacing = 0.08f; setPadding(a.dp(20), a.dp(14), a.dp(20), a.dp(4))
        })
        return this
    }

    fun note(text: String): Sheet {
        list.addView(a.label(text, 13f, T.text2).apply {
            setPadding(a.dp(20), a.dp(6), a.dp(20), a.dp(6)); setLineSpacing(0f, 1.25f)
        })
        return this
    }

    fun custom(v: View, padH: Int = 20): Sheet {
        list.addView(v, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = a.dp(padH); rightMargin = a.dp(padH); topMargin = a.dp(6); bottomMargin = a.dp(6)
        })
        return this
    }

    /** Row of colour swatches; tap applies immediately. -1 = no colour. */
    fun colors(current: Int?, onPick: (Int) -> Unit): Sheet {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(4)) }
        fun swatch(index: Int) {
            val c = T.color(index)
            val v = FrameLayout(a).apply {
                val size = a.dp(34)
                background = if (c != null) rounded(c, a.dpf(17f), if (current == index) Color.WHITE else 0, if (current == index) a.dp(3) else 0)
                    else rounded(T.surface3, a.dpf(17f), if (current == -1) Color.WHITE else T.text3, a.dp(if (current == -1) 3 else 1))
                if (c == null) addView(ImageView(a).apply { setImageDrawable(IconDrawable(Icon.CLOSE, T.text2)) },
                    FrameLayout.LayoutParams(a.dp(18), a.dp(18), Gravity.CENTER))
                contentDescription = if (c == null) "No colour" else T.colorName(index)
                setOnClickListener { dialog.dismiss(); onPick(index) }
                layoutParams = LinearLayout.LayoutParams(size, size).apply { leftMargin = a.dp(6); rightMargin = a.dp(6) }
            }
            row.addView(v)
        }
        swatch(-1)
        T.palette.indices.forEach { swatch(it) }
        list.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        return this
    }

    fun buttons(vararg b: TextView): Sheet {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(a.dp(16), a.dp(12), a.dp(16), 0) }
        b.forEach { row.addView(it, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = a.dp(10) }) }
        list.addView(row)
        return this
    }

    fun show(): Sheet {
        open += this
        dialog.setOnDismissListener { open -= this; dismissHook?.invoke() }
        dialog.show(); return this
    }
    fun dismiss() = dialog.dismiss()
    private var dismissHook: (() -> Unit)? = null
    fun onDismiss(hook: () -> Unit): Sheet { dismissHook = hook; return this }

    companion object {
        private val open = linkedSetOf<Sheet>()
        /** Closes every open sheet (used when the screen is rebuilt, and by tests). */
        fun dismissAll() { open.toList().forEach { it.dismiss() }; CookieEditor.closeOpen() }
    }
}

fun MainActivity.confirm(title: String, message: String, action: String, danger: Boolean = true, onConfirm: () -> Unit) {
    val s = Sheet(this, title, null)
    s.note(message)
    s.buttons(
        pillButton("Cancel", filled = false) { s.dismiss() },
        pillButton(action, filled = true, danger = danger) { s.dismiss(); onConfirm() },
    )
    s.show()
}

/** Transient message above the dock, with an optional Undo. */
class Snack(private val a: MainActivity) : LinearLayout(a) {
    private val text = a.label("", 14f, T.text)
    private val action = a.label("", 14f, T.accent, bold = true)
    private val hide = Runnable { animate().alpha(0f).translationY(a.dpf(12f)).setDuration(160).withEndAction { visibility = GONE }.start() }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(T.surface3, a.dpf(14f), T.stroke, 1)
        elevation = a.dpf(8f)
        setPadding(a.dp(16), a.dp(4), a.dp(8), a.dp(4))
        minimumHeight = a.dp(48)
        text.maxLines = 2
        addView(text, LayoutParams(0, -2, 1f))
        action.setPadding(a.dp(14), a.dp(12), a.dp(10), a.dp(12))
        action.background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(10f))
        addView(action)
        visibility = GONE
    }

    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        removeCallbacks(hide)
        text.text = message
        action.visibility = if (actionLabel != null) VISIBLE else GONE
        action.text = actionLabel ?: ""
        action.setOnClickListener { removeCallbacks(hide); hide.run(); onAction?.invoke() }
        visibility = VISIBLE; alpha = 0f; translationY = a.dpf(12f)
        animate().alpha(1f).translationY(0f).setDuration(180).start()
        postDelayed(hide, if (actionLabel != null) 6000 else 3200)
    }
}
