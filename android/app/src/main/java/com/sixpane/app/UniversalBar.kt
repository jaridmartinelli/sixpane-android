package com.sixpane.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import kotlin.math.roundToInt

/** The compact top row shows a grid filter or the focused pane's address and star. */
internal class UniversalBar(
    private val activity: Activity,
    initialFilter: String,
    onFilterChanged: (String) -> Unit,
    onAddressSubmitted: (String) -> Unit,
    onBookmarkClick: () -> Unit,
    onBookmarkLongClick: () -> Unit,
    onClearFilters: () -> Unit,
    onMenuClick: () -> Unit,
) {
    private var updatingFilter = false
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), dp(3), dp(5), dp(3))
        setBackgroundColor(Color.rgb(19, 26, 37))
    }
    private val filter = field("Filter URL or color").apply {
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!updatingFilter) onFilterChanged(s?.toString().orEmpty())
            }
        })
    }
    private val address = field("Google Search").apply {
        setHintTextColor(Color.argb(130,255,255,255))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions = EditorInfo.IME_ACTION_GO
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
            onAddressSubmitted(text.toString())
            true
        }
    }
    private val star = activity.chromeButton("☆", dp(40), onBookmarkClick).apply {
        setOnLongClickListener {
            onBookmarkLongClick()
            true
        }
    }
    private val clear = activity.chromeButton("×", dp(34), onClearFilters)
    private val dots = activity.chromeButton("⋮", dp(42), onMenuClick).apply {
        contentDescription = "Window selection and batch actions"
    }

    init {
        view.addView(filter, LinearLayout.LayoutParams(0, -1, 1f))
        view.addView(address, LinearLayout.LayoutParams(0, -1, 1f))
        view.addView(star, LinearLayout.LayoutParams(dp(40), -1))
        view.addView(clear, LinearLayout.LayoutParams(dp(34), -1))
        view.addView(dots, LinearLayout.LayoutParams(dp(42), -1))
        setFilterText(initialFilter)
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).roundToInt()

    private fun field(hintText: String): EditText = EditText(activity).apply {
        hint = hintText
        textSize = 13f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.rgb(146, 159, 179))
        setPadding(dp(10), 0, dp(7), 0)
        inputType = InputType.TYPE_CLASS_TEXT
        background = GradientDrawable().apply {
            setColor(Color.rgb(39, 49, 64))
            cornerRadius = dp(8).toFloat()
        }
    }

    fun applyChromeTheme(light:Boolean){
        val chrome=if(light)Color.rgb(237,242,248) else Color.rgb(19,26,37)
        val fieldColor=if(light)Color.WHITE else Color.rgb(39,49,64)
        val foreground=if(light)Color.rgb(26,38,54) else Color.WHITE
        val hint=if(light)Color.rgb(97,113,131) else Color.rgb(146,159,179)
        view.setBackgroundColor(chrome)
        listOf(filter,address).forEach{
            it.setTextColor(foreground)
            it.setHintTextColor(if(it===address && !light)
                Color.argb(130,255,255,255) else hint)
            it.background=GradientDrawable().apply{setColor(fieldColor);cornerRadius=dp(8).toFloat()}
        }
        listOf(star,clear,dots).forEach{it.setTextColor(foreground)}
    }
    fun setFilterText(query: String) {
        updatingFilter = true
        filter.setText(query)
        updatingFilter = false
    }

    fun render(
        filterQuery: String,
        selectedColors: Set<String>,
        focusedUrl: String?,
        bookmarked: Boolean,
        selectionCount: Int?,
    ) {
        val inFocus = focusedUrl != null
        filter.visibility = if (inFocus) View.GONE else View.VISIBLE
        address.visibility = if (inFocus) View.VISIBLE else View.GONE
        star.visibility = if (inFocus) View.VISIBLE else View.GONE
        clear.visibility = if (!inFocus && (filterQuery.isNotBlank() || selectedColors.isNotEmpty()))
            View.VISIBLE else View.GONE
        if (focusedUrl == null) {
            if (!filter.hasFocus() && filter.text.toString() != filterQuery) setFilterText(filterQuery)
            filter.hint = if (selectedColors.isEmpty()) "Filter URL or color"
                else "Colors: " + FramePalette.names.filter { it in selectedColors }.joinToString(", ")
        } else {
            val shownUrl = if (focusedUrl == "about:blank") "" else focusedUrl
            if (!address.hasFocus() && address.text.toString() != shownUrl) address.setText(shownUrl)
            star.text = if (bookmarked) "★" else "☆"
            star.contentDescription = if (bookmarked) "Remove bookmark from this browser"
                else "Bookmark this page in this browser"
        }
        dots.contentDescription = if (selectionCount == null) "Window selection and batch actions"
            else "Window batch actions: $selectionCount selected"
    }

    fun finishAddressSubmission(success: Boolean) {
        if (!success) {
            address.error = "Enter a URL or search"
            return
        }
        address.clearFocus()
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken, 0)
    }

    fun showMenu(
        selecting: Boolean,
        selectionCount: Int,
        anyLocked: Boolean,
        anyUnlocked: Boolean,
        colorCount: Int,
        hasFilters: Boolean,
        hasFocusedPane: Boolean,
        workspaceToolsEnabled: Boolean,
        columns: Int,
        onAction: (UniversalMenuAction) -> Unit,
    ) {
        val popup = PopupMenu(activity, dots)
        fun add(action: UniversalMenuAction, label: String, enabled: Boolean = true) {
            popup.menu.add(label).apply {
                isEnabled = enabled
                setOnMenuItemClickListener {
                    onAction(action)
                    true
                }
            }
        }
        val hasSelection = selectionCount > 0
        if (selecting) add(UniversalMenuAction.EXIT_SELECTION, "Exit selection")
        else add(UniversalMenuAction.SELECT_MULTIPLE, "Select multiple")
        add(UniversalMenuAction.SELECT_VISIBLE, "Select all visible")
        add(UniversalMenuAction.DESELECT_ALL, "Deselect all", hasSelection)
        add(UniversalMenuAction.LOCK_SELECTED, "Lock selected", anyUnlocked)
        add(UniversalMenuAction.UNLOCK_SELECTED, "Unlock selected", anyLocked)
        add(UniversalMenuAction.CLOSE_SELECTED, "Close selected", hasSelection && !anyLocked)
        add(UniversalMenuAction.BOOKMARK_SELECTED, "Bookmark selected", hasSelection)
        add(UniversalMenuAction.SHARE_SELECTED, "Share selected", hasSelection)
        add(UniversalMenuAction.COLOR_SELECTED, "Set selected frame color", hasSelection)
        add(UniversalMenuAction.FILTER_COLORS, "Filter colors ($colorCount selected)")
        add(UniversalMenuAction.CLEAR_FILTERS, "Clear filters", hasFilters)
        // Choose a layout here; never require a special tap on the workspace tab.
        add(UniversalMenuAction.TOGGLE_GRID,if(columns==2)
            "Layout: 2 × 3 → 3 × 2" else "Layout: 3 × 2 → 2 × 3")
        if (hasFocusedPane) add(UniversalMenuAction.PANE_BOOKMARKS, "Bookmarks for this browser")
        add(UniversalMenuAction.WORKSPACE_TOOLS, "Workspace tools",workspaceToolsEnabled)
        add(UniversalMenuAction.SETTINGS, "Settings")
        popup.show()
    }
}

internal enum class UniversalMenuAction {
    SELECT_MULTIPLE, EXIT_SELECTION, SELECT_VISIBLE, DESELECT_ALL, LOCK_SELECTED,
    UNLOCK_SELECTED, CLOSE_SELECTED, BOOKMARK_SELECTED, SHARE_SELECTED, COLOR_SELECTED,
    FILTER_COLORS, CLEAR_FILTERS, TOGGLE_GRID, PANE_BOOKMARKS,
    WORKSPACE_TOOLS, SETTINGS,
}

internal fun Context.chromeButton(label: String, widthPx: Int, action: () -> Unit): TextView =
    TextView(this).apply {
        text = label
        textSize = 17f
        setTextColor(Color.argb(208, 255, 255, 255))
        gravity = Gravity.CENTER
        background=android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.argb(80,160,187,222)),
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),null)
        setOnClickListener { action() }
        minWidth = widthPx
    }
