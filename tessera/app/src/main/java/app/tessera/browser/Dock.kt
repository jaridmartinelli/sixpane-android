package app.tessera.browser

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs

/** Bottom command bar: acts on the ACTIVE pane. Back = Android back gesture. */
class CommandBar(private val a: MainActivity) : FrameLayout(a) {
    private val progress = View(a)
    private val chip = FrameLayout(a)
    private val chipNum = a.label("", 14f, T.text, bold = true)
    private val chipGlyph = ImageView(a)
    private val pill = LinearLayout(a)
    private val pillIcon = ImageView(a)
    private val pillText = a.label("", 14.5f, T.text)
    private val pillZoom = a.label("", 11f, T.text2, bold = true)
    private val forward = a.iconButton(Icon.FORWARD, "Forward") { a.goForward() }
    private val reload = a.iconButton(Icon.RELOAD, "Reload") { a.reloadOrStop() }
    private val more = a.iconButton(Icon.MORE, "Pane and app menu") { a.openMainMenu() }
    private var loading = false

    init {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(8), a.dp(6), a.dp(4), a.dp(6))
        }
        chipNum.gravity = Gravity.CENTER
        chip.addView(chipNum, LayoutParams(-1, -1))
        chipGlyph.alpha = 0.85f
        chip.addView(chipGlyph, LayoutParams(a.dp(11), a.dp(11), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, a.dp(3), a.dp(3)) })
        chip.setOnClickListener { a.toggleExpandActive() }
        chip.setOnLongClickListener { a.openPaneMenu(a.activePane()); true }
        row.addView(chip, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))

        pill.orientation = LinearLayout.HORIZONTAL; pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(a.dp(14), 0, a.dp(12), 0)
        pill.background = ripple(rounded(T.surface2, a.dpf(22f), T.stroke, 1), a.dpf(22f))
        pill.addView(pillIcon, LinearLayout.LayoutParams(a.dp(16), a.dp(16)))
        pillText.setSingleLine(); pillText.ellipsize = TextUtils.TruncateAt.END
        pill.addView(pillText, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(10) })
        pillZoom.background = rounded(T.surface3, a.dpf(8f)); pillZoom.setPadding(a.dp(6), a.dp(3), a.dp(6), a.dp(3))
        pill.addView(pillZoom)
        pill.contentDescription = "Address. Tap to search or type a URL"
        pill.setOnClickListener { a.openAddress(a.activePane()) }
        row.addView(pill, LinearLayout.LayoutParams(0, a.dp(44), 1f).apply { leftMargin = a.dp(8); rightMargin = a.dp(4) })
        row.addView(forward); row.addView(reload); row.addView(more)
        addView(row, LayoutParams(-1, -2))
        progress.setBackgroundColor(T.accent)
        addView(progress, LayoutParams(0, a.dp(2), Gravity.TOP))
        setBackgroundColor(T.surface)
    }

    fun bind(p: Pane, rt: PaneRuntime, number: Int, expanded: Boolean) {
        val c = T.color(p.color)
        chipNum.text = number.toString()
        chipNum.setTextColor(if (c != null) 0xFF0B0E13.toInt() else T.text)
        chip.background = ripple(rounded(c ?: T.surface3, a.dpf(12f), T.accent, a.dp(2)), a.dpf(12f))
        chipGlyph.setImageDrawable(IconDrawable(if (expanded) Icon.COLLAPSE else Icon.EXPAND, if (c != null) 0xCC0B0E13.toInt() else T.text2))
        chip.contentDescription = "Pane $number. Tap to " + (if (expanded) "return to grid" else "expand") + ". Hold for pane menu"
        pillIcon.setImageDrawable(IconDrawable(if (p.locked) Icon.LOCK else Icon.SEARCH, if (p.locked) T.lock else T.text3))
        pillText.text = if (p.url.isBlank()) "Search or type URL" else AddressPolicy.label(p.url)
        pillText.setTextColor(if (p.url.isBlank()) T.text3 else T.text)
        pillZoom.visibility = if (p.zoom != 100) VISIBLE else GONE
        pillZoom.text = "${p.zoom}%"
        forward.isEnabled = rt.canForward
        forward.alpha = if (rt.canForward) 1f else 0.3f
        loading = rt.loading
        reload.setImageDrawable(IconDrawable(if (loading) Icon.CLOSE else Icon.RELOAD, T.text2))
        reload.contentDescription = if (loading) "Stop loading" else "Reload"
        reload.tooltipText = reload.contentDescription
        val show = rt.loading && rt.progress in 1..99
        progress.visibility = if (show) VISIBLE else GONE
        if (show) post { progress.layoutParams = (progress.layoutParams as LayoutParams).apply { width = this@CommandBar.width * rt.progress / 100 } }
    }
}

/** Replaces the command bar in multi-select mode. Acts on the selection only. */
class SelectionBar(private val a: MainActivity) : LinearLayout(a) {
    private val count = a.label("", 15f, T.text, bold = true)
    private val scope = a.label("", 11.5f, T.text2)
    private val lock = a.iconButton(Icon.LOCK, "Lock selected") { a.bulkLock(true) }
    private val unlock = a.iconButton(Icon.UNLOCK, "Unlock selected") { a.bulkLock(false) }
    private val color = a.iconButton(Icon.TAG, "Colour selected") { a.bulkColor() }
    private val all = a.label("All", 14f, T.accent, bold = true)

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(a.dp(4), a.dp(6), a.dp(4), a.dp(6))
        setBackgroundColor(0xFF12201A.toInt())
        addView(a.iconButton(Icon.CLOSE, "Exit selection") { a.exitSelection() })
        val texts = LinearLayout(a).apply { orientation = VERTICAL }
        texts.addView(count); texts.addView(scope)
        addView(texts, LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(4) })
        all.gravity = Gravity.CENTER; all.minWidth = a.dp(48); all.minHeight = a.dp(44)
        all.background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(10f))
        all.setOnClickListener { a.selectAllVisible() }
        all.contentDescription = "Select all visible panes in this workspace"
        addView(all)
        addView(lock); addView(unlock); addView(color)
        addView(a.iconButton(Icon.MORE, "More actions for selected panes") { a.openSelectionMenu() })
    }

    fun bind(selected: List<Pane>, workspaces: Int) {
        count.text = if (selected.isEmpty()) "Tap panes to select" else "${selected.size} selected"
        scope.text = when {
            selected.isEmpty() -> "Nothing will change until you select"
            workspaces > 1 -> "Across $workspaces workspaces"
            else -> "In this workspace"
        }
        val any = selected.isNotEmpty()
        listOf(lock to (any && selected.any { !it.locked }), unlock to (any && selected.any { it.locked }), color to any).forEach { (v, on) ->
            v.alpha = if (on) 1f else 0.3f
        }
        lock.isEnabled = any && selected.any { !it.locked }
        unlock.isEnabled = any && selected.any { it.locked }
        color.isEnabled = any
    }
}

/** Compact workspace tabs. Swipe the strip sideways to switch workspaces. */
@SuppressLint("ClickableViewAccessibility")
class TabStrip(private val a: MainActivity) : FrameLayout(a) {
    private val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val swipe = GestureDetector(a, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > abs(vy) * 1.5f && abs(vx) > 600) { a.switchWorkspaceBy(if (vx < 0) 1 else -1); return true }
            return false
        }
    })

    init {
        setBackgroundColor(T.bg)
        setPadding(a.dp(8), a.dp(4), a.dp(8), a.dp(6))
        addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LayoutParams(-1, -2))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = swipe.onTouchEvent(ev)
    override fun onTouchEvent(ev: MotionEvent): Boolean { swipe.onTouchEvent(ev); return true }

    fun bind(state: AppState, visible: List<Workspace>, filtering: Boolean, runtime: (Pane) -> Boolean) {
        row.removeAllViews()
        visible.forEach { w ->
            val index = state.workspaces.indexOf(w) + 1
            val active = w.id == state.currentId
            val tab = FrameLayout(a)
            val num = a.label(index.toString(), 14f, if (active) T.accent else T.text, bold = true).apply { gravity = Gravity.CENTER }
            tab.addView(num, LayoutParams(-1, -1).apply { bottomMargin = a.dp(6) })
            val dots = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            w.panes.forEach { p ->
                val on = p.url.isNotBlank()
                dots.addView(View(a).apply {
                    background = rounded(if (p.locked) T.lock else if (on) (if (active) T.accent else T.text2) else T.text3, a.dpf(2f))
                    alpha = if (on || p.locked) 1f else 0.5f
                }, LinearLayout.LayoutParams(a.dp(3), a.dp(3)).apply { leftMargin = a.dp(1); rightMargin = a.dp(1) })
            }
            tab.addView(dots, LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = a.dp(6) })
            if (w.allLocked) tab.addView(ImageView(a).apply { setImageDrawable(IconDrawable(Icon.LOCK, T.lock)) },
                LayoutParams(a.dp(12), a.dp(12), Gravity.TOP or Gravity.END).apply { setMargins(0, a.dp(3), a.dp(3), 0) })
            tab.background = ripple(
                if (active) rounded(T.accentSoft, a.dpf(10f), T.accent, a.dp(1)) else rounded(T.surface2, a.dpf(10f)),
                a.dpf(10f))
            tab.contentDescription = "Workspace $index" + (if (active) ", current" else "") +
                (if (w.allLocked) ", all panes locked" else if (w.lockedCount > 0) ", ${w.lockedCount} locked" else "") +
                ". Hold for workspace menu"
            tab.setOnClickListener { a.switchWorkspace(w.id) }
            tab.setOnLongClickListener { a.openWorkspaceMenu(w); true }
            row.addView(tab, LinearLayout.LayoutParams(a.dp(48), a.dp(42)).apply { rightMargin = a.dp(6) })
        }
        val full = state.workspaces.size >= AppState.MAX_WORKSPACES
        val plus = a.iconButton(Icon.ADD, "New workspace (added to the right of the current one)", 42) { a.addWorkspace() }
        plus.background = ripple(rounded(T.surface2, a.dpf(10f), T.stroke, 1), a.dpf(10f))
        plus.alpha = if (full || filtering) 0.35f else 1f
        row.addView(plus, LinearLayout.LayoutParams(a.dp(42), a.dp(42)))
        if (full) row.addView(a.label("7/7", 11f, T.text3).apply { setPadding(a.dp(8), 0, 0, 0) })
    }
}

/** Always-visible summary of active filters; each chip removes itself. */
class FilterChips(private val a: MainActivity) : HorizontalScrollView(a) {
    private val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(2)) }

    init { isHorizontalScrollBarEnabled = false; addView(row); setBackgroundColor(T.surface) }

    private fun chip(lead: View, text: String, desc: String, onRemove: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ripple(rounded(T.surface2, a.dpf(16f), T.stroke, 1), a.dpf(16f))
            setPadding(a.dp(10), 0, a.dp(2), 0)
            addView(lead)
            addView(a.label(text, 13f), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = a.dp(6) })
            addView(a.iconButton(Icon.CLOSE, "Remove $desc", 34) { onRemove() })
            setOnClickListener { a.openFilterSheet() }
            layoutParams = LinearLayout.LayoutParams(-2, a.dp(34)).apply { rightMargin = a.dp(6) }
        }

    fun bind(f: PaneFilter, matches: Int) {
        row.removeAllViews()
        row.addView(a.iconButton(Icon.FILTER, "Edit filters", 34, T.accent) { a.openFilterSheet() })
        f.colors.sorted().forEach { c ->
            val dot = View(a).apply { background = rounded(T.palette[c], a.dpf(6f)); layoutParams = LinearLayout.LayoutParams(a.dp(12), a.dp(12)) }
            row.addView(chip(dot, T.colorName(c), "colour ${T.colorName(c)}") { a.removeFilterColor(c) })
        }
        if (f.urlText.isNotBlank()) {
            val icon = ImageView(a).apply { setImageDrawable(IconDrawable(Icon.LINK, T.accent)); layoutParams = LinearLayout.LayoutParams(a.dp(16), a.dp(16)) }
            row.addView(chip(icon, "URL has “${f.urlText.trim()}”", "URL filter") { a.clearUrlFilter() })
        }
        row.addView(a.label("$matches match" + (if (matches == 1) "" else "es"), 12f, T.text3).apply { setPadding(a.dp(4), 0, a.dp(10), 0) })
        row.addView(a.label("Clear all", 13f, T.accent, bold = true).apply {
            setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
            background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(10f))
            setOnClickListener { a.clearFilters() }
        })
    }
}
