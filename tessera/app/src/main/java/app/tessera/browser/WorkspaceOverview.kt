package app.tessera.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Full-screen workspace switcher (like a browser's tab overview): one card per
 * workspace with live previews of its six panes. Tap a card to switch; ✕ closes
 * a workspace (with confirmation); "New workspace" sits in thumb reach.
 */
class WorkspaceOverview(private val a: MainActivity) : FrameLayout(a) {
    private val header = LinearLayout(a)
    private val count = a.label("", 13f, T.text2)
    private val scroll = ScrollView(a)
    private val cards = GridLayout(a)
    private val bottom = LinearLayout(a)
    private val add = a.pillButton("+  New workspace", filled = true) { a.addWorkspaceFromOverview() }
    private val full = a.label("", 12.5f, T.text3)

    init {
        setBackgroundColor(T.bg)
        isClickable = true // swallow touches meant for the grid underneath
        visibility = GONE

        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(a.dp(20), a.dp(8), a.dp(8), a.dp(8))
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(a.label("Workspaces", 22f, T.text, bold = true))
        titles.addView(count, LinearLayout.LayoutParams(-2, -2).apply { topMargin = a.dp(2) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(a.iconButton(Icon.CLOSE, "Close workspaces", 48) { hide() })

        cards.useDefaultMargins = false
        cards.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(16))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(cards)

        bottom.orientation = LinearLayout.VERTICAL
        bottom.gravity = Gravity.CENTER_HORIZONTAL
        bottom.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(12))
        bottom.setBackgroundColor(T.bg)
        bottom.addView(add, LinearLayout.LayoutParams(-1, a.dp(52)))
        bottom.addView(full, LinearLayout.LayoutParams(-2, -2).apply { topMargin = a.dp(6) })

        val column = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        column.addView(header, LinearLayout.LayoutParams(-1, -2))
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        column.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        addView(column, LayoutParams(-1, -1))

        setOnApplyWindowInsetsListener { _, ins ->
            val t: Int; val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                t = i.top; b = i.bottom
            } else @Suppress("DEPRECATION") { t = ins.systemWindowInsetTop; b = ins.systemWindowInsetBottom }
            setPadding(0, t, 0, b); ins
        }
    }

    val showing get() = visibility == VISIBLE

    fun show() {
        render()
        visibility = VISIBLE
        alpha = 0f; translationY = a.dpf(24f)
        animate().alpha(1f).translationY(0f).setDuration(180).start()
        requestApplyInsets()
    }

    fun hide() {
        if (!showing) return
        animate().alpha(0f).translationY(a.dpf(24f)).setDuration(140).withEndAction { visibility = GONE }.start()
    }

    fun render() {
        val st = a.stateRef()
        val vis = a.visibleWorkspacesRef()
        count.text = "${st.workspaces.size} of ${AppState.MAX_WORKSPACES}" + if (a.isFiltering()) " · filtered" else ""
        val cols = if (a.resources.configuration.screenWidthDp >= 600) 3 else 2
        cards.removeAllViews()
        cards.columnCount = cols
        vis.forEach { w -> cards.addView(card(w), GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(a.dp(6), a.dp(6), a.dp(6), a.dp(6))
        }) }
        val isFull = st.workspaces.size >= AppState.MAX_WORKSPACES
        add.alpha = if (isFull || a.isFiltering()) 0.4f else 1f
        full.text = when {
            isFull -> "7 is the maximum — close one to add another"
            a.isFiltering() -> "Clear filters to add a workspace"
            else -> "Added next to the current one, with 6 fresh sessions"
        }
    }

    private fun card(w: Workspace): View {
        val st = a.stateRef()
        val idx = st.workspaces.indexOf(w) + 1
        val current = w.id == st.currentId
        val open = w.panes.count { it.url.isNotBlank() }

        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = ripple(rounded(T.surface, a.dpf(16f), if (current) Color.WHITE else T.stroke,
                if (current) a.dp(2) else 1), a.dpf(16f))
            setPadding(a.dp(8), a.dp(4), a.dp(4), a.dp(8))
            setOnClickListener { a.switchWorkspace(w.id); hide() }
            setOnLongClickListener { a.openWorkspaceMenu(w); true }
            contentDescription = "Workspace $idx, $open of 6 open" + (if (current) ", current" else "") +
                (if (w.lockedCount > 0) ", ${w.lockedCount} locked" else "")
        }
        val top = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(a.label(idx.toString(), 17f, T.text, bold = true).apply { setPadding(a.dp(6), 0, a.dp(8), 0) })
        if (w.lockedCount > 0) top.addView(ImageView(a).apply { setImageDrawable(IconDrawable(Icon.LOCK, T.lock)) },
            LinearLayout.LayoutParams(a.dp(14), a.dp(14)).apply { rightMargin = a.dp(6) })
        top.addView(a.label("$open open", 12.5f, T.text3), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(a.iconButton(if (w.allLocked) Icon.UNLOCK else Icon.LOCK,
            if (w.allLocked) "Unlock all panes in workspace $idx" else "Lock all panes in workspace $idx", 40,
            if (w.allLocked) T.lock else T.text2) {
            a.setLocked(if (w.allLocked) w.panes.toList() else w.panes.filter { !it.locked }, !w.allLocked)
        })
        top.addView(a.iconButton(Icon.CLOSE, "Close workspace $idx", 40, T.text2) { a.requestCloseWorkspace(w) })
        card.addView(top)

        // 2 x 3 live previews in the same order as the grid.
        // Plain rows of two (a nested weighted GridLayout collapses to one column).
        val minis = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        w.panes.forEachIndexed { i, p ->
            if (i % 2 == 0) row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }.also { minis.addView(it) }
            val tile = FrameLayout(a).apply {
                background = rounded(T.surface2, a.dpf(6f))
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            val shot = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            tile.addView(shot, LayoutParams(-1, -1))
            val stripe = T.color(p.color)
            if (stripe != null) tile.addView(View(a).apply { setBackgroundColor(stripe) }, LayoutParams(-1, a.dp(4), Gravity.TOP))
            if (p.url.isBlank()) tile.addView(a.label("New", 10f, T.text3).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -1))
            else post { snapshot(a.hubRef().view(p.id))?.let { shot.setImageBitmap(it) } ?: run {
                tile.addView(a.label(AddressPolicy.label(p.url), 9.5f, T.text2).apply { gravity = Gravity.CENTER; setPadding(4, 0, 4, 0) }, LayoutParams(-1, -1))
            } }
            if (p.locked) tile.addView(ImageView(a).apply {
                setImageDrawable(IconDrawable(Icon.LOCK, T.lock)); background = rounded(0xE60B0E13.toInt(), a.dpf(4f))
                setPadding(a.dp(2), a.dp(2), a.dp(2), a.dp(2))
            }, LayoutParams(a.dp(14), a.dp(14), Gravity.TOP or Gravity.END).apply { setMargins(0, a.dp(6), a.dp(4), 0) })
            row!!.addView(tile, LinearLayout.LayoutParams(0, a.dp(if (cols() == 3) 64 else 72), 1f).apply {
                setMargins(a.dp(2), a.dp(2), a.dp(2), a.dp(2))
            })
        }
        card.addView(minis, LinearLayout.LayoutParams(-1, -2).apply { rightMargin = a.dp(4) })
        return card
    }

    private fun cols() = cards.columnCount

    /** Small picture of what a pane's page currently shows; null if it can't be drawn. */
    private fun snapshot(wv: WebView?): Bitmap? {
        if (wv == null || wv.width <= 0 || wv.height <= 0) return null
        return runCatching {
            val scale = 0.3f
            val bmp = Bitmap.createBitmap((wv.width * scale).toInt().coerceAtLeast(1), (wv.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.drawColor(T.surface2)
            c.scale(scale, scale)
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            // Some WebView builds draw nothing into a software canvas: fall back to the site name.
            val first = bmp.getPixel(0, 0)
            val blank = (1..24).all { k -> bmp.getPixel((bmp.width * k / 25), (bmp.height * ((k * 7) % 25) / 25).coerceAtMost(bmp.height - 1)) == first }
            if (blank) null else bmp
        }.getOrNull()
    }

}
