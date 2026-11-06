package app.tessera.browser

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * One pane: a thin header strip (the ONLY place SIXPANE-style app gestures
 * live: tap, double-tap, long-press) above the website. Website touches are
 * never consumed; touching a page just makes its pane active.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PaneView(context: Context, val paneId: String, private val cb: Callbacks) : FrameLayout(context) {
    interface Callbacks {
        fun onPaneTouched(id: String)
        fun onHeaderDoubleTap(id: String)
        fun onHeaderLongPress(id: String)
        fun onSelectToggle(id: String)
        fun onMiniTap(id: String)
        fun onOpenAddress(id: String)
        fun onRetry(id: String)
        fun onPullRefresh(id: String)
        fun isSelecting(): Boolean
    }

    /** False while hidden by a filter or in another workspace: no touches at all. */
    var interactive = true

    private val headerH = dp(26)
    private val header = FrameLayout(context)
    private val headerRow = LinearLayout(context)
    private val chip = context.label("", 11f, T.text, bold = true)
    private val lockIcon = ImageView(context)
    private val site = context.label("", 11.5f, T.text2)
    private val badge = context.label("", 10f, T.text2, bold = true)
    private val progress = View(context)
    private val content = FrameLayout(context)
    private val host = ScaledHost(context)
    private val refresher = SwipeRefreshLayout(context)
    private val blank = LinearLayout(context)
    private val error = LinearLayout(context)
    private val errorTitle = context.label("", 14f, T.text, bold = true)
    private val errorText = context.label("", 12f, T.text2)
    private val errorRetry = context.pillButton("Retry", filled = true) { cb.onRetry(paneId) }
    private val mini = View(context)
    private val select = FrameLayout(context)
    private val check = ImageView(context)
    private val frame = GradientDrawable()

    init {
        setBackgroundColor(T.surface)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        background = rounded(T.surface, dpf(8f))
        foreground = frame
        frame.cornerRadius = dpf(8f)

        // Header -----------------------------------------------------------
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.setPadding(dp(5), 0, dp(6), 0)
        chip.gravity = Gravity.CENTER
        chip.minWidth = dp(20)
        chip.setPadding(dp(5), dp(2), dp(5), dp(2))
        headerRow.addView(chip, LinearLayout.LayoutParams(-2, dp(18)))
        // Lock sits on a dark badge so it reads on any colour tag.
        lockIcon.setImageDrawable(IconDrawable(Icon.LOCK, T.lock))
        lockIcon.background = rounded(0xE60B0E13.toInt(), dpf(5f))
        lockIcon.setPadding(dp(2), dp(2), dp(2), dp(2))
        headerRow.addView(lockIcon, LinearLayout.LayoutParams(dp(18), dp(18)).apply { leftMargin = dp(5) })
        site.setSingleLine(); site.ellipsize = TextUtils.TruncateAt.END
        headerRow.addView(site, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        badge.setPadding(dp(5), dp(2), dp(5), dp(2))
        headerRow.addView(badge, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(4) })
        header.addView(headerRow, LayoutParams(-1, -1))
        progress.setBackgroundColor(0xD9FFFFFF.toInt())
        header.addView(progress, LayoutParams(0, dp(2), Gravity.BOTTOM))
        addView(header, LayoutParams(-1, headerH))

        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (cb.isSelecting()) cb.onSelectToggle(paneId)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!cb.isSelecting()) cb.onHeaderDoubleTap(paneId); return true
            }
            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                cb.onHeaderLongPress(paneId)
            }
        })
        header.setOnTouchListener { _, ev -> gestures.onTouchEvent(ev) }
        header.contentDescription = "Pane controls. Double-tap to expand, long-press for menu"

        // Content ----------------------------------------------------------
        refresher.setColorSchemeColors(T.bg)
        refresher.setProgressBackgroundColorSchemeColor(T.text)
        refresher.setOnRefreshListener { cb.onPullRefresh(paneId) }
        host.addView(refresher, LayoutParams(-1, -1))
        content.addView(host, LayoutParams(-1, -1))

        blank.orientation = LinearLayout.VERTICAL
        blank.gravity = Gravity.CENTER
        blank.addView(context.label("New session", 13f, T.text3).apply { gravity = Gravity.CENTER })
        blank.addView(context.pillButton("Open a site", filled = false) { cb.onOpenAddress(paneId) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        blank.setBackgroundColor(T.surface)
        content.addView(blank, LayoutParams(-1, -1))

        error.orientation = LinearLayout.VERTICAL
        error.gravity = Gravity.CENTER
        error.setPadding(dp(14), dp(14), dp(14), dp(14))
        error.setBackgroundColor(T.surface)
        error.isClickable = true
        error.addView(ImageView(context).apply { setImageDrawable(IconDrawable(Icon.ERROR, T.text3)) },
            LinearLayout.LayoutParams(dp(28), dp(28)))
        errorTitle.gravity = Gravity.CENTER
        error.addView(errorTitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        errorText.gravity = Gravity.CENTER; errorText.maxLines = 3
        error.addView(errorText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        error.addView(errorRetry, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        content.addView(error, LayoutParams(-1, -1))

        mini.isClickable = true
        mini.background = ripple(ColorDrawable(Color.TRANSPARENT), 0f)
        mini.contentDescription = "Show this pane large"
        mini.setOnClickListener { cb.onMiniTap(paneId) }
        content.addView(mini, LayoutParams(-1, -1))

        select.isClickable = true
        select.setOnClickListener { cb.onSelectToggle(paneId) }
        select.addView(check, LayoutParams(dp(26), dp(26), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8); rightMargin = dp(8)
        })
        content.addView(select, LayoutParams(-1, -1))

        addView(content, LayoutParams(-1, -1).apply { topMargin = headerH })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!interactive) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) cb.onPaneTouched(paneId)
        return super.dispatchTouchEvent(ev)
    }

    fun attachWeb(wv: WebView) {
        if (wv.parent === refresher) return
        (wv.parent as? ViewGroup)?.removeView(wv)
        refresher.removeAllViews()
        refresher.addView(wv, ViewGroup.LayoutParams(-1, -1))
    }

    fun hasWeb(wv: WebView?) = wv != null && wv.parent === refresher

    fun bind(
        pane: Pane, rt: PaneRuntime, number: Int,
        active: Boolean, selecting: Boolean, selected: Boolean, isMini: Boolean,
        minPageWidthPx: Int = 0,
    ) {
        val color = T.color(pane.color)
        val ink = if (color != null) 0xFF0B0E13.toInt() else T.text
        chip.text = number.toString()
        chip.background = rounded(if (color != null) 0x2E000000 else T.surface3, dpf(5f))
        chip.setTextColor(ink)
        host.minWidthPx = minPageWidthPx
        refresher.isEnabled = !selecting && !isMini
        if (!rt.loading && refresher.isRefreshing) refresher.isRefreshing = false
        lockIcon.visibility = if (pane.locked) VISIBLE else GONE
        site.text = when {
            rt.crashed -> "Restoring…"
            pane.url.isBlank() -> "New session"
            else -> AddressPolicy.label(pane.url)
        }
        site.setTextColor(if (color != null) 0xD90B0E13.toInt() else if (active) T.text else T.text2)
        badge.setTextColor(if (color != null) ink else T.text2)
        badge.background = rounded(if (color != null) 0x2E000000 else T.surface3, dpf(6f))
        val extras = buildList {
            if (pane.zoom != 100) add("${pane.zoom}%")
            if (pane.desktop) add("Desktop")
        }
        badge.text = extras.joinToString(" · ")
        badge.visibility = if (extras.isEmpty() || isMini) GONE else VISIBLE
        // The header carries the pane's colour tag; untagged panes stay neutral.
        header.setBackgroundColor(color ?: if (active) T.surface3 else T.surface2)
        val showProgress = rt.loading && rt.progress in 1..99
        progress.visibility = if (showProgress) VISIBLE else GONE
        if (showProgress) post {
            progress.layoutParams = (progress.layoutParams as LayoutParams).apply { width = (header.width * rt.progress / 100f).toInt() }
        }

        blank.visibility = if (pane.url.isBlank() && rt.error == null && !rt.crashed && !isMini && !selecting) VISIBLE else GONE
        when {
            rt.crashed -> {
                error.visibility = VISIBLE
                errorTitle.text = "Page crashed"
                errorText.text = "Restoring this session automatically. Logins and cookies are kept."
                errorRetry.visibility = GONE
            }
            rt.error != null -> {
                error.visibility = VISIBLE
                errorTitle.text = "Can't open this page"
                errorText.text = rt.error
                errorRetry.visibility = VISIBLE
            }
            else -> error.visibility = GONE
        }
        mini.visibility = if (isMini && !selecting) VISIBLE else GONE

        select.visibility = if (selecting) VISIBLE else GONE
        // Selection and the active outline use white, never a tag colour.
        select.setBackgroundColor(if (selected) 0x29FFFFFF else 0x40000000)
        check.setImageDrawable(IconDrawable(Icon.CHECK, if (selected) 0xFF0B0E13.toInt() else Color.TRANSPARENT))
        check.background = if (selected) rounded(Color.WHITE, dpf(13f))
            else rounded(0x55000000, dpf(13f), T.text2, dp(2))
        check.setPadding(dp(4), dp(4), dp(4), dp(4))

        when {
            selected -> frame.setStroke(dp(3), Color.WHITE)
            active && !selecting -> frame.setStroke(kotlin.math.max(1, Math.round(dpf(1.5f))), Color.WHITE)
            else -> frame.setStroke(dp(1), T.stroke)
        }
        contentDescription = "Pane $number" + (if (pane.locked) ", locked" else "") +
            (if (selected) ", selected" else "") + (if (active) ", active" else "")
    }

    /** Visible, physical feedback when a locked pane refuses an action. */
    fun shakeLock() {
        performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        ObjectAnimator.ofFloat(lockIcon, "translationX", 0f, -6f, 6f, -5f, 5f, -2f, 0f).setDuration(380).start()
        ObjectAnimator.ofFloat(lockIcon, "scaleX", 1f, 1.35f, 1f).setDuration(380).start()
        ObjectAnimator.ofFloat(lockIcon, "scaleY", 1f, 1.35f, 1f).setDuration(380).start()
    }
}
