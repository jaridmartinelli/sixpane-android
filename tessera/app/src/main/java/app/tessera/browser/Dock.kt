package app.tessera.browser

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * Bottom command bar for the ACTIVE pane: [pane chip] [address field with ☆] [→] [workspaces] [⋮].
 * Back = Android back gesture. Reload = pull down on the page.
 */
class CommandBar(private val a: MainActivity) : FrameLayout(a) {
    private val progress = View(a)
    private val chip = FrameLayout(a)
    private val chipNum = a.label("", 14f, T.text, bold = true)
    private val chipGlyph = ImageView(a)
    private val pill = LinearLayout(a)
    private val pillIcon = ImageView(a)
    val field = EditText(a)
    private val star = ImageView(a)
    private val forward = a.iconButton(Icon.FORWARD, "Forward") { a.goForward() }
    private val spaces = FrameLayout(a)
    private val spacesNum = a.label("", 12.5f, T.text, bold = true)
    private val more = a.iconButton(Icon.MORE, "Pane and app menu") { a.openMainMenu() }
    private var url = ""
    private var loading = false
    private var prevBookmarked: Boolean? = null

    val editing get() = field.hasFocus()

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
        Motion.pressable(chip, 0.92f)
        row.addView(chip, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))

        pill.orientation = LinearLayout.HORIZONTAL; pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(a.dp(14), 0, a.dp(2), 0)
        pill.background = rounded(T.surface2, a.dpf(22f), T.stroke, 1)
        pill.addView(pillIcon, LinearLayout.LayoutParams(a.dp(16), a.dp(16)))
        field.apply {
            background = null; setPadding(a.dp(10), 0, a.dp(4), 0)
            textSize = 15f; setTextColor(T.text); setHintTextColor(T.text3); hint = "Search or type URL"
            setSingleLine(); isHorizontalFadingEdgeEnabled = true
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelectAllOnFocus(true)
            setOnFocusChangeListener { _, has ->
                setText(if (has) url.takeIf { it != "about:blank" }.orEmpty() else AddressPolicy.label(url))
                if (has) selectAll()
                a.onAddressFocus(has)
            }
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                    a.submitAddress(text.toString()); true
                } else false
            }
        }
        pill.addView(field, LinearLayout.LayoutParams(0, -1, 1f))
        star.scaleType = ImageView.ScaleType.CENTER_INSIDE
        star.setPadding(a.dp(10), a.dp(10), a.dp(10), a.dp(10))
        star.background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(20f))
        star.setOnClickListener { a.starOrStop() }
        Motion.pressable(star, 0.88f)
        pill.addView(star, LinearLayout.LayoutParams(a.dp(40), a.dp(40)))
        row.addView(pill, LinearLayout.LayoutParams(0, a.dp(44), 1f).apply { leftMargin = a.dp(8); rightMargin = a.dp(4) })

        row.addView(forward)
        spacesNum.gravity = Gravity.CENTER
        spacesNum.background = rounded(Color.TRANSPARENT, a.dpf(6f), T.text2, a.dp(2))
        spaces.addView(spacesNum, LayoutParams(a.dp(24), a.dp(24), Gravity.CENTER))
        spaces.background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(10f))
        spaces.setOnClickListener { a.openWorkspaces() }
        spaces.setOnLongClickListener { a.openWorkspaceMenu(a.currentWorkspace()); true }
        Motion.pressable(spaces, 0.9f)
        row.addView(spaces, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))
        row.addView(more)
        addView(row, LayoutParams(-1, -2))
        progress.setBackgroundColor(T.text)
        progress.pivotX = 0f
        addView(progress, LayoutParams(-1, a.dp(2), Gravity.TOP))
        setBackgroundColor(T.surface)
    }

    fun bind(p: Pane, rt: PaneRuntime, number: Int, expanded: Boolean, bookmarked: Boolean, workspace: Int, workspaces: Int) {
        val c = T.color(p.color)
        chipNum.text = number.toString()
        chipNum.setTextColor(if (c != null) 0xFF0B0E13.toInt() else T.text)
        chip.background = ripple(rounded(c ?: T.surface3, a.dpf(12f), Color.WHITE, a.dp(1)), a.dpf(12f))
        chipGlyph.setImageDrawable(IconDrawable(if (expanded) Icon.COLLAPSE else Icon.EXPAND, if (c != null) 0xCC0B0E13.toInt() else T.text2))
        chip.contentDescription = "Pane $number. Tap to " + (if (expanded) "return to grid" else "expand") + ". Hold for pane menu"
        pillIcon.setImageDrawable(IconDrawable(if (p.locked) Icon.LOCK else Icon.SEARCH, if (p.locked) T.lock else T.text3))
        val changed = url != p.url
        url = p.url
        if (!field.hasFocus() && (changed || field.text.isEmpty())) field.setText(AddressPolicy.label(url))
        if (bookmarked && prevBookmarked == false && url == p.url) Motion.pop(star)
        prevBookmarked = bookmarked
        loading = rt.loading
        val canStar = AddressPolicy.isWeb(url)
        star.setImageDrawable(IconDrawable(
            if (loading) Icon.CLOSE else if (bookmarked) Icon.STAR else Icon.STAR_OUTLINE,
            if (loading) T.text2 else if (bookmarked) T.text else if (canStar) T.text2 else T.text3))
        star.contentDescription = if (loading) "Stop loading" else if (bookmarked) "Remove bookmark" else "Bookmark this page"
        star.tooltipText = star.contentDescription
        forward.isEnabled = rt.canForward
        forward.alpha = if (rt.canForward) 1f else 0.3f
        spacesNum.text = workspace.toString()
        spaces.contentDescription = "Workspace $workspace of $workspaces. Tap to switch or add workspaces"
        spaces.tooltipText = spaces.contentDescription
        val show = rt.loading && rt.progress in 1..99
        if (show) {
            if (progress.visibility != VISIBLE) { progress.scaleX = 0f; progress.visibility = VISIBLE }
            progress.animate().scaleX(rt.progress / 100f).setDuration(Motion.PROGRESS_MS).start()
        } else progress.visibility = GONE
    }

    fun focusAddress() {
        field.requestFocus()
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    fun clearAddress() {
        (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
    }
}

/** Replaces the command bar in multi-select mode. Acts on the selection only. */
class SelectionBar(private val a: MainActivity) : LinearLayout(a) {
    private val count = a.label("", 15f, T.text, bold = true)
    private val scope = a.label("", 11.5f, T.text2)
    private val lock = a.iconButton(Icon.LOCK, "Lock selected") { a.bulkLock(true) }
    private val unlock = a.iconButton(Icon.UNLOCK, "Unlock selected") { a.bulkLock(false) }
    private val color = a.iconButton(Icon.TAG, "Colour selected") { a.bulkColor() }
    private val all = a.label("All", 14f, T.text, bold = true)

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(a.dp(4), a.dp(6), a.dp(4), a.dp(6))
        setBackgroundColor(T.surface2)
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

    fun bind(selected: List<Pane>, workspaces: Int, allVisibleSelected: Boolean = false) {
        all.text = if (allVisibleSelected) "None" else "All"
        all.contentDescription = if (allVisibleSelected) "Deselect all panes in this workspace" else "Select all visible panes in this workspace"
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
            Motion.pressable(this, 0.96f)
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
        row.addView(a.label("Clear all", 13f, T.text, bold = true).apply {
            setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
            background = ripple(ColorDrawable(Color.TRANSPARENT), a.dpf(10f))
            setOnClickListener { a.clearFilters() }
        })
    }
}
