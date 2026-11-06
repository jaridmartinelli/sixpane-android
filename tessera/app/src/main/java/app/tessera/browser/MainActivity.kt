package app.tessera.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Controller. Layout, top to bottom: pane grid · filter chips (when filtering)
 * · command bar (or selection bar). Everything the thumb needs
 * is in the bottom third.
 */
class MainActivity : Activity(), SessionHub.Listener, PaneView.Callbacks {
    private lateinit var app: TesseraApp
    private val state get() = app.store.state
    private val hub get() = app.hub
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var grid: PaneGrid
    private lateinit var chips: FilterChips
    private lateinit var cmd: CommandBar
    private lateinit var sel: SelectionBar
    private lateinit var suggest: LinearLayout
    private lateinit var overview: WorkspaceOverview
    private lateinit var snack: Snack
    private lateinit var dock: LinearLayout
    private lateinit var emptyState: LinearLayout
    private var uiReady = false

    private var selecting = false
    private val selection = linkedSetOf<String>()
    private val blankCache = HashMap<String, Boolean>()

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as TesseraApp
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else @Suppress("DEPRECATION") {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (!hub.supported) { showUnsupported(); return }
        hub.purgePending()
        hub.listener = this
        hub.attachActivity(this)
        buildUi()
        createSessions()
        KeepAliveService.sync(this)
        if (!state.onboarded) handler.postDelayed({ showOnboarding() }, 600)
    }

    private fun showUnsupported() {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32)); setBackgroundColor(T.bg)
            addView(ImageView(this@MainActivity).apply { setImageDrawable(IconDrawable(Icon.SHIELD, T.lock)) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(label("Update Android System WebView", 20f, T.text, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8)) })
            addView(label("Tessera only runs when every pane can have its own private cookie and storage jar. " +
                "This phone's WebView doesn't support separate profiles yet, so Tessera won't start rather than mix your sessions.\n\n" +
                "Update “Android System WebView” in the Play Store, then reopen Tessera.", 14f, T.text2).apply { gravity = Gravity.CENTER })
            addView(pillButton("Open Play Store", filled = true) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))) }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })
        }
        setContentView(v)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(T.bg) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        grid = PaneGrid(this)
        grid.locate = { id -> state.workspaceOf(id)?.let { w -> w.id to w.panes.indexOfFirst { it.id == id } } }
        grid.shown = { id -> state.pane(id)?.let { FilterPolicy.matches(it.url, it.color, state.filter) } ?: false }
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(ImageView(this@MainActivity).apply { setImageDrawable(IconDrawable(Icon.FILTER, T.text3)) }, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(label("No panes match these filters", 16f, T.text, bold = true).apply { setPadding(0, dp(12), 0, dp(4)) })
            addView(label("Your sessions are still running.", 13f, T.text2))
            addView(pillButton("Clear filters", filled = true) { clearFilters() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            visibility = View.GONE
        }
        grid.emptyView = emptyState
        column.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        dock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.surface) }
        chips = FilterChips(this)
        dock.addView(chips, LinearLayout.LayoutParams(-1, -2))
        val barHolder = FrameLayout(this)
        cmd = CommandBar(this); sel = SelectionBar(this)
        barHolder.addView(cmd, FrameLayout.LayoutParams(-1, -2))
        barHolder.addView(sel, FrameLayout.LayoutParams(-1, -2))
        dock.addView(barHolder, LinearLayout.LayoutParams(-1, -2))
        column.addView(dock, LinearLayout.LayoutParams(-1, -2))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        suggest = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedTop(T.surface, dpf(18f)); elevation = dpf(6f)
            setPadding(0, dp(8), 0, dp(4)); visibility = View.GONE
        }
        root.addView(suggest, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        snack = Snack(this)
        root.addView(snack, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(140)) })
        dock.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            (snack.layoutParams as FrameLayout.LayoutParams).let { lp ->
                val m = bottom - top + dp(10)
                if (lp.bottomMargin != m) { lp.bottomMargin = m; snack.layoutParams = lp }
            }
            (suggest.layoutParams as FrameLayout.LayoutParams).let { lp ->
                val m = bottom - top
                if (lp.bottomMargin != m) { lp.bottomMargin = m; suggest.layoutParams = lp }
            }
        }

        overview = WorkspaceOverview(this)
        root.addView(overview, FrameLayout.LayoutParams(-1, -1))

        root.setOnApplyWindowInsetsListener { _, ins ->
            val l: Int; val t: Int; val r: Int; val b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                l = i.left; t = i.top; r = i.right; b = i.bottom
            } else @Suppress("DEPRECATION") {
                l = ins.systemWindowInsetLeft; t = ins.systemWindowInsetTop; r = ins.systemWindowInsetRight; b = ins.systemWindowInsetBottom
            }
            grid.setPadding(l + dp(4), t + dp(4), r + dp(4), dp(4))
            dock.setPadding(l, 0, r, b)
            // Keyboard: lift the dock (and what sits on it) above the IME without resizing the panes.
            val lift = if (Build.VERSION.SDK_INT >= 30) (ins.getInsets(WindowInsets.Type.ime()).bottom - b).coerceAtLeast(0) else 0
            listOf<View>(dock, suggest, snack).forEach { it.translationY = -lift.toFloat() }
            overview.dispatchApplyWindowInsets(ins)
            ins
        }
        setContentView(root)
        uiReady = true
    }

    /** Creates a card for every pane; the current workspace's browsers start first. */
    private fun createSessions() {
        val ordered = state.current().panes + state.workspaces.filter { it.id != state.currentId }.flatMap { it.panes }
        ordered.forEachIndexed { i, p ->
            ensureCard(p)
            if (hub.view(p.id) != null) grid.card(p.id)?.attachWeb(hub.view(p.id)!!)
            else if (i < GridGeometry.SLOTS) startSession(p)
            else handler.postDelayed({ if (state.pane(p.id) != null) startSession(p) }, (i - 5) * 150L)
        }
        refreshAll()
    }

    private fun ensureCard(p: Pane): PaneView =
        grid.card(p.id) ?: PaneView(this, p.id, this).also { grid.add(it) }

    private fun startSession(p: Pane) {
        val card = ensureCard(p)
        runCatching { card.attachWeb(hub.obtain(p)) }
            .onFailure { toast("Pane couldn't start: ${it.message}") }
        bindCard(p.id)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!uiReady) return
        // Fold/unfold or density change: rebuild app chrome, re-parent the same live WebViews.
        Sheet.dismissAll()
        buildUi()
        state.allPanes().forEach { p -> ensureCard(p); hub.view(p.id)?.let { grid.card(p.id)?.attachWeb(it) } }
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        if (!uiReady) return
        hub.attachActivity(this)
        KeepAliveService.sync(this)
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        // Deliberately NOT pausing WebViews or timers: sessions must keep running.
        if (!uiReady) return
        app.store.saveNow()
        hub.flushCookies()
    }

    override fun onDestroy() {
        if (uiReady) { app.store.saveNow(); hub.detachActivity(this); hub.listener = null }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (!uiReady) { super.onBackPressed(); return }
        val p = activePane()
        val wv = hub.view(p.id)
        when {
            overview.showing -> overview.hide()
            cmd.editing -> cmd.clearAddress()
            selecting -> exitSelection()
            wv != null && wv.canGoBack() -> wv.goBack()
            state.expandedPaneId != null -> { state.expandedPaneId = null; refreshAll(animate = true) }
            else -> {
                // Never finish: leaving the app must not end or pause sessions.
                moveTaskToBack(true)
            }
        }
    }

    // ------------------------------------------------------------- rendering

    fun activePane(): Pane = state.pane(state.activePaneId) ?: state.current().panes.first()

    private fun shown(p: Pane) = FilterPolicy.matches(p.url, p.color, state.filter)
    private fun filtering() = state.filter.active
    private fun visibleWorkspaces() = if (filtering()) state.workspaces.filter { w -> w.panes.any { shown(it) } } else state.workspaces

    private fun normalize() {
        state.ensureValid()
        if (filtering()) {
            val vis = visibleWorkspaces()
            if (vis.isNotEmpty() && vis.none { it.id == state.currentId }) state.currentId = vis.first().id
        }
        val cur = state.current()
        val active = state.pane(state.activePaneId)
        if (active == null || state.workspaceOf(active.id)?.id != cur.id || !shown(active))
            state.activePaneId = (cur.panes.firstOrNull { shown(it) } ?: cur.panes.first()).id
        state.expandedPaneId?.let { id ->
            val p = state.pane(id)
            if (p == null || state.workspaceOf(id)?.id != cur.id || !shown(p) || selecting) state.expandedPaneId = null
        }
    }

    fun refreshAll(animate: Boolean = false) {
        if (!uiReady) return
        normalize()
        grid.currentWs = state.currentId
        grid.columnsOverride = when (state.layout) {
            LayoutMode.AUTO -> null; LayoutMode.TWO_BY_THREE -> 2; LayoutMode.THREE_BY_TWO -> 3
        }
        val exp = state.expandedPaneId?.let { state.slotOf(it) } ?: -1
        grid.update(exp, animate)
        state.allPanes().forEach { bindCard(it.id) }
        val anyMatch = visibleWorkspaces().isNotEmpty()
        emptyState.visibility = if (filtering() && !anyMatch) View.VISIBLE else View.GONE

        cmd.visibility = if (selecting) View.GONE else View.VISIBLE
        sel.visibility = if (selecting) View.VISIBLE else View.GONE
        bindBars()
        chips.visibility = if (filtering()) View.VISIBLE else View.GONE
        if (filtering()) chips.bind(state.filter, state.allPanes().count { shown(it) })
        if (overview.showing) overview.render()
        app.store.save()
    }

    private fun bindBars() {
        val p = activePane()
        cmd.bind(p, hub.runtime(p.id), state.slotOf(p.id) + 1, state.expandedPaneId == p.id,
            state.bookmarks.any { it.url == p.url }, state.workspaces.indexOf(state.current()) + 1, state.workspaces.size)
        val chosen = selectedPanes()
        val vis = state.current().panes.filter { shown(it) }
        sel.bind(chosen, chosen.mapNotNull { state.workspaceOf(it.id)?.id }.distinct().size,
            vis.isNotEmpty() && vis.all { it.id in selection })
    }

    private fun bindCard(id: String) {
        val p = state.pane(id) ?: return
        val card = grid.card(id) ?: return
        val ws = state.workspaceOf(id) ?: return
        val slot = ws.panes.indexOf(p)
        val expSlot = state.expandedPaneId?.let { state.slotOf(it) } ?: -1
        val isMini = ws.id == state.currentId && expSlot >= 0 && slot != expSlot
        card.bind(p, hub.runtime(id), slot + 1, id == state.activePaneId, selecting, id in selection, isMini,
            if (state.fitSmallPanes) dp(MIN_PAGE_WIDTH_DP) else 0)
    }

    // ------------------------------------------------------- hub callbacks

    override fun onPaneChanged(paneId: String) {
        if (!uiReady) return
        bindCard(paneId)
        if (paneId == state.activePaneId) bindBars()
        val p = state.pane(paneId) ?: return
        val blank = p.url.isBlank()
        if (blankCache.put(paneId, blank) != blank || (filtering() && state.filter.urlText.isNotBlank())) refreshAll()
    }

    override fun onViewReplaced(paneId: String) {
        if (!uiReady) return
        hub.view(paneId)?.let { grid.card(paneId)?.attachWeb(it) }
        bindCard(paneId)
    }

    // ----------------------------------------------------- pane callbacks

    override fun isSelecting() = selecting

    override fun onPaneTouched(id: String) {
        // Touching any page ends address editing, like a regular browser.
        if (cmd.editing) cmd.clearAddress()
        if (selecting || id == state.activePaneId) return
        val old = state.activePaneId
        state.activePaneId = id
        bindCard(old); bindCard(id); bindBars()
        app.store.save()
    }

    override fun onHeaderDoubleTap(id: String) = toggleExpand(id)

    override fun onHeaderLongPress(id: String) {
        if (selecting) { onSelectToggle(id); return }
        state.pane(id)?.let { onPaneTouched(id); openPaneMenu(it) }
    }

    override fun onSelectToggle(id: String) {
        if (!selecting) return
        if (!selection.remove(id)) selection += id
        bindCard(id); bindBars()
    }

    override fun onMiniTap(id: String) {
        state.activePaneId = id
        state.expandedPaneId = id
        refreshAll(animate = true)
    }

    override fun onOpenAddress(id: String) {
        state.pane(id)?.let { onPaneTouched(id); openAddress(it) }
    }

    override fun onRetry(id: String) {
        hub.runtime(id).error = null
        hub.view(id)?.reload()
        bindCard(id)
    }

    // ------------------------------------------------------ command bar

    fun toggleExpandActive() = toggleExpand(activePane().id)

    private fun toggleExpand(id: String) {
        if (selecting) { toast("Finish selecting first (tap ✕)"); return }
        state.activePaneId = id
        state.expandedPaneId = if (state.expandedPaneId == id) null else id
        refreshAll(animate = true)
    }

    fun goForward() {
        val wv = hub.view(activePane().id)
        if (wv != null && wv.canGoForward()) wv.goForward() else toast("Nothing to go forward to")
    }

    /** ☆ inside the address field; while a page loads it becomes Stop. */
    fun starOrStop() {
        val p = activePane()
        val rt = hub.runtime(p.id)
        when {
            rt.loading -> { hub.view(p.id)?.stopLoading(); rt.loading = false; bindCard(p.id); bindBars() }
            AddressPolicy.isWeb(p.url) -> { toggleBookmark(p); bindBars() }
            else -> toast("Open a page first, then bookmark it")
        }
    }

    override fun onPullRefresh(id: String) {
        val p = state.pane(id) ?: return
        hub.runtime(id).error = null
        if (p.url.isBlank()) { bindCard(id); return }
        hub.runtime(id).loading = true
        hub.view(id)?.reload()
    }

    /** Puts the cursor in the bottom address field for [p] (no separate address screen). */
    fun openAddress(p: Pane) {
        if (selecting) return
        if (state.activePaneId != p.id) { state.activePaneId = p.id; refreshAll() }
        cmd.focusAddress()
    }

    fun submitAddress(text: String) {
        val url = when (val r = AddressPolicy.resolve(text)) {
            is AddressPolicy.Result.Open -> r.url
            is AddressPolicy.Result.Search -> r.url
            AddressPolicy.Result.Empty -> { toast("Type an address or search words"); return }
        }
        val p = activePane()
        cmd.clearAddress()
        navigate(p, url)
    }

    /** While typing: paste & go and bookmarks appear just above the bar. */
    fun onAddressFocus(focused: Boolean) {
        suggest.removeAllViews()
        if (!focused) { suggest.visibility = View.GONE; return }
        // Only check THAT something is copied; the text is read when "Paste & go" is tapped,
        // so Android doesn't show a "pasted from clipboard" notice every time the bar is tapped.
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val hasText = cm.primaryClipDescription?.let { it.hasMimeType("text/*") } == true
        if (hasText) {
            suggest.addView(LinearLayout(this).apply {
                setPadding(dp(12), 0, dp(12), dp(4))
                addView(chipButton(Icon.PASTE, "Paste & go") {
                    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)
                        ?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { submitAddress(it) } ?: toast("Nothing to paste")
                })
            })
        }
        state.bookmarks.take(8).forEach { b ->
            suggest.addView(bookmarkRow(b, onOpen = { submitAddress(b.url) }, onRemove = { removeBookmark(b); onAddressFocus(true) }))
        }
        suggest.visibility = if (suggest.childCount > 0) View.VISIBLE else View.GONE
    }

    fun currentWorkspace(): Workspace = state.current()

    /** Full-screen workspace overview: switch, close, or add (up to 7). */
    fun openWorkspaces() {
        cmd.clearAddress()
        overview.show()
    }

    fun addWorkspaceFromOverview() {
        when {
            state.workspaces.size >= AppState.MAX_WORKSPACES -> toast("7 is the maximum. Close a workspace to add one.")
            filtering() -> toast("Clear filters to add a workspace", "Clear") { clearFilters() }
            else -> { addWorkspace(); overview.hide() }
        }
    }

    fun requestCloseWorkspace(w: Workspace) {
        if (w.lockedCount > 0) {
            w.panes.filter { it.locked }.forEach { grid.card(it.id)?.shakeLock() }
            toast("${w.lockedCount} locked pane${if (w.lockedCount == 1) "" else "s"} \u2014 unlock to close this workspace")
            return
        }
        closeWorkspace(w)
    }

    internal fun visibleWorkspacesRef() = visibleWorkspaces()
    internal fun isFiltering() = filtering()

    private fun paneTitle(p: Pane): String {
        val w = state.workspaces.indexOfFirst { it.id == state.workspaceOf(p.id)?.id } + 1
        return "Pane ${state.slotOf(p.id) + 1} · Workspace $w"
    }

    private fun lockedReason(p: Pane, what: String) = "Locked — unlock this pane to $what"

    fun openMainMenu() = openPaneMenu(activePane(), global = true)

    fun openPaneMenu(p: Pane, global: Boolean = false) {
        cmd.clearAddress()
        val subtitle = when {
            p.url.isBlank() -> "New session — nothing open yet"
            p.title.isNotBlank() -> p.title + "\n" + AddressPolicy.label(p.url)
            else -> AddressPolicy.label(p.url)
        }
        val s = Sheet(this, paneTitle(p), subtitle)
        s.section("Colour tag")
        s.colors(p.color) { recolor(listOf(p), it) }
        s.item(if (p.locked) Icon.UNLOCK else Icon.LOCK, if (p.locked) "Unlock pane" else "Lock pane",
            detail = if (p.locked) "Allows ending the session and switching desktop mode" else "Protects this session from being ended or switched to desktop mode") {
            setLocked(listOf(p), !p.locked)
        }
        s.toggle(Icon.DESKTOP, "Desktop site", p.desktop, enabled = !p.locked,
            detail = "Shows the full desktop layout. Reloads the page.",
            blockedReason = lockedReason(p, "change desktop mode"), onBlocked = { blocked(p) }) { on ->
            hub.setDesktop(p, on); bindCard(p.id); bindBars()
        }
        s.item(Icon.KEY, "Cookies & site data",
            detail = if (p.locked) "View only while locked" else "See and edit this session's cookies") {
            CookieEditor(this, p).show()
        }
        s.item(Icon.COPY, "Copy diagnostics", detail = "Session, storage and keep-alive status as text") {
            Diagnostics.copy(this, p)
        }
        s.item(Icon.SELECT, "Select multiple", detail = "Start selecting, beginning with this pane") { enterSelection(p.id) }
        s.item(Icon.POWER, "End session", detail = "Erase this pane's logins and data and start fresh",
            danger = true, enabled = !p.locked, blockedReason = lockedReason(p, "end its session"), onBlocked = { blocked(p) }) {
            endSessions(listOf(p))
        }
        if (global) {
            s.section("All panes")
            s.item(Icon.FILTER, "Filter panes", detail = "Show only panes with chosen colours or URLs",
                trailing = if (filtering()) "On" else null) { openFilterSheet() }
            s.item(Icon.SHIELD, "Check isolation & health", detail = "Prove every pane has its own cookie jar and is awake") {
                Diagnostics.isolationCheck(this)
            }
            s.item(Icon.SETTINGS, "Settings") { openSettings() }
        }
        s.show()
    }

    private fun chipButton(icon: Icon, text: String, enabled: Boolean = true, tint: Int = T.text2, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(14), 0); minimumHeight = dp(40)
            background = ripple(rounded(T.surface2, dpf(20f), T.stroke, 1), dpf(20f))
            alpha = if (enabled) 1f else 0.45f
            addView(ImageView(this@MainActivity).apply { setImageDrawable(IconDrawable(icon, tint)) }, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(label(text, 13.5f), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) }
        }

    private fun bookmarkRow(b: Bookmark, onOpen: () -> Unit, onRemove: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(2), dp(8), dp(2)); minimumHeight = dp(52)
            background = ripple(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), 0f)
            addView(ImageView(this@MainActivity).apply { setImageDrawable(IconDrawable(Icon.BOOKMARKS, T.text2)) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            val texts = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(b.title.ifBlank { AddressPolicy.label(b.url) }, 14.5f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            texts.addView(label(AddressPolicy.label(b.url), 12f, T.text3).apply { maxLines = 1 })
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
            addView(iconButton(Icon.CLOSE, "Remove bookmark") { onRemove() })
            setOnClickListener { onOpen() }
        }

    private fun navigate(p: Pane, url: String) {
        state.activePaneId = p.id
        hub.runtime(p.id).error = null
        hub.navigate(p, url)
        grid.card(p.id)?.let { if (!it.hasWeb(hub.view(p.id))) it.attachWeb(hub.view(p.id)!!) }
        refreshAll()
    }

    private fun toggleBookmark(p: Pane) {
        val existing = state.bookmarks.firstOrNull { it.url == p.url }
        if (existing != null) removeBookmark(existing)
        else {
            val b = Bookmark(p.url, p.title)
            state.bookmarks.add(0, b); app.store.save()
            toast("Bookmarked ${AddressPolicy.label(p.url)}", "Undo") { state.bookmarks.remove(b); app.store.save() }
        }
    }

    private fun removeBookmark(b: Bookmark) {
        val i = state.bookmarks.indexOf(b)
        if (i < 0) return
        state.bookmarks.removeAt(i); app.store.save()
        toast("Bookmark removed", "Undo") { state.bookmarks.add(i.coerceAtMost(state.bookmarks.size), b); app.store.save() }
    }

    // --------------------------------------------------------- workspaces

    fun switchWorkspace(id: String) {
        if (id == state.currentId) return
        state.currentId = id
        state.expandedPaneId = null
        state.activePaneId = ""
        refreshAll()
    }

    fun switchWorkspaceBy(delta: Int) {
        val vis = visibleWorkspaces()
        val i = vis.indexOfFirst { it.id == state.currentId }
        vis.getOrNull(i + delta)?.let { switchWorkspace(it.id) }
    }

    fun addWorkspace(after: Workspace = state.current()) {
        if (filtering()) { toast("Clear filters to add a workspace", "Clear") { clearFilters() }; return }
        if (state.workspaces.size >= AppState.MAX_WORKSPACES) {
            toast("7 workspaces is the maximum. Close one to add another."); return
        }
        val w = state.newWorkspace()
        val at = state.workspaces.indexOf(after).let { if (it < 0) state.workspaces.size else it + 1 }
        state.workspaces.add(at, w)
        state.currentId = w.id; state.activePaneId = w.panes.first().id; state.expandedPaneId = null
        w.panes.forEach { ensureCard(it); startSession(it) }
        refreshAll()
        toast("Workspace ${at + 1} added with 6 fresh sessions")
    }

    fun openWorkspaceMenu(w: Workspace) {
        val idx = state.workspaces.indexOf(w) + 1
        val open = w.panes.count { it.url.isNotBlank() }
        val s = Sheet(this, "Workspace $idx", "$open of 6 panes open · ${w.lockedCount} locked")
        if (!w.allLocked) s.item(Icon.LOCK, "Lock all 6 panes", detail = "Only this workspace") { setLocked(w.panes.toList(), true) }
        if (w.lockedCount > 0) s.item(Icon.UNLOCK, "Unlock all panes", detail = "${w.lockedCount} locked in this workspace") {
            setLocked(w.panes.filter { it.locked }, false)
        }
        val full = state.workspaces.size >= AppState.MAX_WORKSPACES
        s.item(Icon.ADD, "New workspace to the right", enabled = !full && !filtering(),
            blockedReason = if (full) "7 of 7 workspaces — close one first" else "Clear filters to add a workspace") { addWorkspace(w) }
        s.item(Icon.POWER, "Close workspace", detail = "Ends all 6 sessions. Reopening starts fresh.", danger = true,
            enabled = w.lockedCount == 0,
            blockedReason = "${w.lockedCount} locked pane${if (w.lockedCount == 1) "" else "s"} — unlock to close",
            onBlocked = { w.panes.filter { it.locked }.forEach { grid.card(it.id)?.shakeLock() } }) { closeWorkspace(w) }
        s.show()
    }

    private fun closeWorkspace(w: Workspace) {
        val idx = state.workspaces.indexOf(w) + 1
        confirm("Close workspace $idx?",
            "Signs out of everything in its 6 panes and erases their data. Can't be undone.",
            "Close workspace") {
            if (w.lockedCount > 0) { toast("A pane was locked meanwhile — nothing was closed"); return@confirm }
            val pos = state.workspaces.indexOf(w)
            if (pos < 0) return@confirm
            w.panes.forEach { retire(it) }
            state.workspaces.removeAt(pos)
            selection.removeAll(w.panes.map { it.id }.toSet())
            if (state.workspaces.isEmpty()) {
                val fresh = state.newWorkspace(); state.workspaces += fresh
                fresh.panes.forEach { startSession(it) }
            }
            if (state.currentId == w.id) state.currentId = state.workspaces[(pos - 1).coerceAtLeast(0)].id
            refreshAll()
            toast("Workspace $idx closed")
        }
    }

    private fun retire(p: Pane) {
        grid.remove(p.id)
        hub.destroy(p.id)
        hub.retireProfile(p.profile)
    }

    // ---------------------------------------------------------------- bulk

    private fun selectedPanes(): List<Pane> = selection.mapNotNull { state.pane(it) }

    fun enterSelection(initial: String? = null) {
        cmd.clearAddress()
        selecting = true
        selection.clear()
        initial?.let { selection += it }
        state.expandedPaneId = null
        refreshAll(animate = true)
    }

    fun exitSelection() {
        selecting = false
        selection.clear()
        refreshAll()
    }

    fun selectAllVisible() {
        val vis = state.current().panes.filter { shown(it) }
        if (vis.all { it.id in selection }) selection.removeAll(vis.map { it.id }.toSet())
        else selection.addAll(vis.map { it.id })
        refreshAll()
    }

    fun bulkLock(lock: Boolean) {
        val targets = selectedPanes().filter { it.locked != lock }
        if (targets.isEmpty()) { toast(if (lock) "Selected panes are already locked" else "No selected pane is locked"); return }
        setLocked(targets, lock)
    }

    fun bulkColor() {
        val targets = selectedPanes()
        if (targets.isEmpty()) { toast("Select panes first"); return }
        Sheet(this, "Colour ${targets.size} pane${if (targets.size == 1) "" else "s"}", "You can undo right after")
            .colors(null) { recolor(targets, it) }.show()
    }

    fun openSelectionMenu() {
        val chosen = selectedPanes()
        val n = chosen.size
        val locked = chosen.count { it.locked }
        val s = Sheet(this, if (n == 0) "Nothing selected" else "$n selected",
            if (locked > 0) "$locked locked — locked panes are skipped by protected actions" else null)
        val none = n == 0
        s.item(Icon.RELOAD, "Reload selected", enabled = !none, blockedReason = "Select panes first") {
            chosen.forEach { hub.runtime(it.id).error = null; hub.view(it.id)?.reload() }
            toast("Reloading $n pane${if (n == 1) "" else "s"}")
        }
        s.item(Icon.SEARCH, "Open an address in selected", detail = "Each pane opens it in its own session",
            enabled = !none, blockedReason = "Select panes first") { bulkOpen(chosen) }
        s.item(Icon.DESKTOP, "Desktop site on", enabled = !none, blockedReason = "Select panes first",
            detail = if (locked > 0) "Skips $locked locked" else null) { bulkDesktop(chosen, true) }
        s.item(Icon.DESKTOP, "Desktop site off", enabled = !none, blockedReason = "Select panes first",
            detail = if (locked > 0) "Skips $locked locked" else null) { bulkDesktop(chosen, false) }
        s.item(Icon.CLOSE, "Clear selection", enabled = !none, blockedReason = "Nothing to clear") { selection.clear(); refreshAll() }
        s.item(Icon.POWER, "End sessions…", danger = true, enabled = n - locked > 0,
            blockedReason = if (none) "Select panes first" else "All selected panes are locked",
            detail = "You'll see exactly which panes before anything happens") { endSessions(chosen) }
        s.show()
    }

    private fun bulkOpen(targets: List<Pane>) {
        val s = Sheet(this, "Open in ${targets.size} panes", "Each pane loads it independently, in its own session.")
        val field = EditText(this).apply {
            hint = "Search Google or type a URL"; setHintTextColor(T.text3); setTextColor(T.text); setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; imeOptions = EditorInfo.IME_ACTION_GO
            background = rounded(T.surface2, dpf(14f), T.accent, dp(1)); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun go() {
            val url = when (val r = AddressPolicy.resolve(field.text.toString())) {
                is AddressPolicy.Result.Open -> r.url; is AddressPolicy.Result.Search -> r.url
                AddressPolicy.Result.Empty -> { field.error = "Type an address or search"; return }
            }
            s.dismiss()
            val open = targets.filter { !it.locked }
            val skipped = targets.size - open.size
            open.forEach { p -> hub.runtime(p.id).error = null; hub.navigate(p, url) }
            targets.filter { it.locked }.forEach { grid.card(it.id)?.shakeLock() }
            refreshAll(); toast("Opening in ${open.size} pane${if (open.size == 1) "" else "s"}" + if (skipped > 0) " \u00B7 $skipped locked skipped" else "")
        }
        field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_GO) { go(); true } else false }
        s.custom(field)
        s.buttons(pillButton("Cancel", filled = false) { s.dismiss() }, pillButton("Open", filled = true) { go() })
        s.show()
        s.dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.requestFocus()
    }

    private fun bulkDesktop(targets: List<Pane>, on: Boolean) {
        val ok = targets.filter { !it.locked && it.desktop != on }
        val skipped = targets.count { it.locked }
        ok.forEach { hub.setDesktop(it, on) }
        targets.filter { it.locked }.forEach { grid.card(it.id)?.shakeLock() }
        refreshAll()
        toast("Desktop site ${if (on) "on" else "off"} for ${ok.size}" + if (skipped > 0) " · $skipped locked skipped" else "")
    }

    /** Plain words: "Pane 3", or "Workspace 1: panes 2, 5 · Workspace 3: pane 1". */
    private fun describe(panes: List<Pane>): String {
        val byWs = panes.groupBy { state.workspaces.indexOfFirst { w -> w.id == state.workspaceOf(it.id)?.id } + 1 }.toSortedMap()
        val single = byWs.size == 1 && byWs.keys.first() == state.workspaces.indexOf(state.current()) + 1
        return byWs.entries.joinToString(" \u00B7 ") { (w, ps) ->
            val nums = ps.map { state.slotOf(it.id) + 1 }.sorted()
            val list = (if (nums.size == 1) "pane " else "panes ") + nums.joinToString(", ")
            if (single) list.replaceFirstChar { it.uppercase() } else "Workspace $w: $list"
        }
    }

    private fun endSessions(panes: List<Pane>) {
        val targets = panes.filter { !it.locked }
        val skipped = panes.filter { it.locked }
        if (targets.isEmpty()) { skipped.forEach { blocked(it) }; return }
        val n = targets.size
        val msg = buildString {
            if (n > 1) append(describe(targets)).append(".\n")
            append("Signs out of everything and erases ${if (n == 1) "its" else "their"} data. Can't be undone.")
            if (skipped.isNotEmpty()) append("\nLocked, staying open: ").append(describe(skipped)).append('.')
        }
        val title = if (n == 1) "End ${describe(targets)}?" else "End $n sessions?"
        confirm(title, msg, if (n == 1) "End session" else "End $n") {
            var ended = 0
            targets.forEach { old ->
                if (old.locked) return@forEach
                val ws = state.workspaceOf(old.id) ?: return@forEach
                val slot = ws.panes.indexOf(old)
                val fresh = state.newPane()
                ws.panes[slot] = fresh
                if (selection.remove(old.id)) selection += fresh.id
                if (state.activePaneId == old.id) state.activePaneId = fresh.id
                if (state.expandedPaneId == old.id) state.expandedPaneId = fresh.id
                retire(old)
                startSession(fresh)
                ended++
            }
            refreshAll()
            toast("Ended $ended session${if (ended == 1) "" else "s"} — fresh panes are ready")
        }
    }

    fun setLocked(panes: List<Pane>, value: Boolean) {
        val before = panes.associateWith { it.locked }
        panes.forEach { it.locked = value }
        refreshAll()
        val n = panes.size
        toast("${if (value) "Locked" else "Unlocked"} $n pane${if (n == 1) "" else "s"}", "Undo") {
            before.forEach { (p, v) -> p.locked = v }; refreshAll()
        }
    }

    private fun recolor(panes: List<Pane>, color: Int) {
        val before = panes.associateWith { it.color }
        panes.forEach { it.color = color }
        refreshAll()
        val n = panes.size
        val what = if (color < 0) "Removed colour from" else "Coloured ${T.colorName(color)}:"
        toast("$what $n pane${if (n == 1) "" else "s"}", "Undo") {
            before.forEach { (p, c) -> p.color = c }; refreshAll()
        }
    }

    /** Feedback when a lock refuses an action. */
    fun blocked(p: Pane) {
        grid.card(p.id)?.shakeLock()
    }

    // ------------------------------------------------------------- filters

    fun openFilterSheet() {
        val s = Sheet(this, "Filter panes", "Only matching panes are shown, in their usual places. Sessions keep running.")
        s.section("Colour — any of these")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(4), dp(14), dp(4)) }
        fun renderSwatches() {
            row.removeAllViews()
            T.palette.forEachIndexed { i, c ->
                val on = i in state.filter.colors
                val count = state.allPanes().count { it.color == i }
                row.addView(FrameLayout(this).apply {
                    background = rounded(c, dpf(17f), if (on) Color.WHITE else 0, if (on) dp(3) else 0)
                    alpha = if (count == 0 && !on) 0.35f else 1f
                    if (on) addView(ImageView(this@MainActivity).apply { setImageDrawable(IconDrawable(Icon.CHECK, 0xFF0B0E13.toInt())) },
                        FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
                    contentDescription = "${T.colorName(i)}, $count pane${if (count == 1) "" else "s"}" + if (on) ", filtering" else ""
                    setOnClickListener {
                        state.filter = state.filter.copy(colors = if (on) state.filter.colors - i else state.filter.colors + i)
                        renderSwatches(); refreshAll()
                    }
                }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(6); rightMargin = dp(6) })
            }
        }
        renderSwatches()
        s.custom(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, padH = 0)
        s.section("URL contains")
        val field = EditText(this).apply {
            setText(state.filter.urlText); hint = "e.g. google.com"; setHintTextColor(T.text3); setTextColor(T.text); setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; imeOptions = EditorInfo.IME_ACTION_DONE
            background = rounded(T.surface2, dpf(14f), T.stroke, 1); setPadding(dp(16), dp(12), dp(16), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(e: Editable?) { state.filter = state.filter.copy(urlText = e?.toString().orEmpty()); refreshAll() }
            })
        }
        s.custom(field)
        s.note("Colours match any selected colour; the URL text must also match. Filters stay on until you clear them.")
        s.buttons(pillButton("Clear all", filled = false) { s.dismiss(); clearFilters() }, pillButton("Done", filled = true) { s.dismiss() })
        s.show()
    }

    fun removeFilterColor(c: Int) { state.filter = state.filter.copy(colors = state.filter.colors - c); refreshAll() }
    fun clearUrlFilter() { state.filter = state.filter.copy(urlText = ""); refreshAll() }
    /** Clearing only changes what is shown; it never reloads or resets a session. */
    fun clearFilters() { state.filter = PaneFilter(); refreshAll(); toast("Filters cleared — all panes shown") }

    // ------------------------------------------------------------ settings

    private fun batteryUnrestricted() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun notificationsAllowed() =
        Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }.onFailure { openAppDetails() }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    /** "Display over other apps" lets Tessera hold pages in an invisible window when its screen closes. */
    private fun requestOverlay() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            .onFailure { openAppDetails() }
    }

    private fun openAppDetails() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    fun openSettings() {
        val s = Sheet(this, "Settings", "Tessera ${appVersion()}")
        s.section("Keep sessions alive")
        s.toggle(Icon.PULSE, "Keep CPU awake", state.keepCpuAwake, detail = "Pages keep running with the screen off. Uses more battery.") {
            state.keepCpuAwake = it; app.store.saveNow(); KeepAliveService.sync(this)
        }
        s.toggle(Icon.PULSE, "Keep Wi-Fi awake", state.keepWifiAwake,
            detail = if (Build.VERSION.SDK_INT >= 34) "Asks Wi-Fi not to doze. Android 14+ may ignore this request." else "Stops Wi-Fi from dozing so connections don't drop.") {
            state.keepWifiAwake = it; app.store.saveNow(); KeepAliveService.sync(this)
        }
        val bat = batteryUnrestricted()
        s.item(Icon.SHIELD, "Battery optimisation", detail = if (bat) "Unrestricted — Android won't put Tessera to sleep" else "Restricted — tap to allow running in background",
            trailing = if (bat) "✓" else "Fix") { if (!bat) requestBatteryExemption() else openAppDetails() }
        val notif = notificationsAllowed()
        s.item(Icon.SETTINGS, "Keep-alive notification", detail = if (notif) "Allowed" else "Hidden — tap to allow (recommended)",
            trailing = if (notif) "✓" else "Allow") { if (!notif) requestNotifications() else openAppDetails() }
        val park = hub.canPark
        s.item(Icon.SHIELD, "Full speed after closing from Recents",
            detail = if (park) "Allowed \u2014 pages keep running if you swipe Tessera away" else "Off \u2014 tap and allow \u201CDisplay over other apps\u201D",
            trailing = if (park) "\u2713" else "Allow") { requestOverlay() }
        s.item(Icon.SETTINGS, "App info (OnePlus background settings)", detail = "Set Battery to “Unrestricted” / allow background activity") { openAppDetails() }
        s.section("Layout")
        s.toggle(Icon.GRID, "Full-size pages in small panes", state.fitSmallPanes,
            detail = "Shows sites at normal phone width, scaled down, instead of their narrowest layout") {
            state.fitSmallPanes = it; refreshAll()
        }
        LayoutMode.values().forEach { m ->
            val label = when (m) { LayoutMode.AUTO -> "Automatic (3×2 unfolded, 2×3 folded)"; LayoutMode.TWO_BY_THREE -> "Always 2 columns × 3 rows"; LayoutMode.THREE_BY_TWO -> "Always 3 columns × 2 rows" }
            s.item(Icon.GRID, label, trailing = if (state.layout == m) "✓" else null) { state.layout = m; refreshAll(animate = true) }
        }
        s.section("About")
        s.item(Icon.KEY, "How sessions work") { explainSessions() }
        if (state.pendingDeletes.isNotEmpty()) s.item(Icon.DELETE, "Finish erasing ended sessions",
            detail = "${state.pendingDeletes.size} waiting — Android finishes this on next app start") { hub.purgePending(); toast("Erase retried") }
        s.show()
    }

    private fun appVersion(): String = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    private fun explainSessions() {
        Sheet(this, "How sessions work")
            .note("Every pane is its own private browser. It has its own cookies, logins, saved site data and history.")
            .note("Logging in on Pane 1 does not log you in on Pane 2 — so you can be signed in to the same site as different people in different panes.")
            .note("Ending a session or closing a workspace erases that browser for good. A new pane always starts empty and is never reused.")
            .note("Bookmarks are just addresses. Opening one uses the session of the pane you open it in.")
            .note("To stop Android from sleeping your sessions, Tessera shows a small notification and keeps the CPU and Wi-Fi awake. Pages in other workspaces keep running too.")
            .note("Use “Check isolation & health” in the menu to prove this on your phone.")
            .show()
    }

    private fun showOnboarding() {
        if (isFinishing || state.onboarded) return
        val s = Sheet(this, "Welcome to Tessera", "42 private browsers — 7 workspaces of 6 panes.")
        s.note("Tap a page to make its pane active. The bar at the bottom always controls the active pane. Use Android's back gesture to go back.")
        s.note("Each pane's thin top strip is Tessera's: double-tap to enlarge, hold for the pane menu. Long-presses on websites stay with the website.")
        s.section("So sessions never drop")
        if (!notificationsAllowed()) s.item(Icon.SETTINGS, "Allow the keep-alive notification", keepOpen = false) { requestNotifications() }
        if (!batteryUnrestricted()) s.item(Icon.SHIELD, "Allow running in the background", detail = "Turns off battery optimisation for Tessera") { requestBatteryExemption() }
        if (!hub.canPark) s.item(Icon.SHIELD, "Keep full speed after closing from Recents", detail = "Allow \u201CDisplay over other apps\u201D") { requestOverlay() }
        s.buttons(pillButton("Got it", filled = true) { s.dismiss() })
        s.onDismiss { state.onboarded = true; app.store.save() }
        s.show()
    }

    // ------------------------------------------------- pop-ups & uploads

    private var popupDialog: android.app.Dialog? = null
    private var popupView: android.webkit.WebView? = null
    private var popupTitle: android.widget.TextView? = null
    private var popupPane: Pane? = null
    internal val popupShowing get() = popupDialog?.isShowing == true

    /** New-tab links and sign-in pop-ups open here, over the pane, in the pane's own session. */
    fun showPopup(p: Pane, wv: android.webkit.WebView) {
        closePopup()
        popupPane = p; popupView = wv
        val d = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4)); setBackgroundColor(T.surface)
        }
        bar.addView(iconButton(Icon.CLOSE, "Close pop-up") { closePopup() })
        val title = label("Pane ${slotNumber(p)} \u00B7 pop-up", 14f, T.text).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        popupTitle = title
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        bar.addView(label("Open in pane", 13.5f, T.text, bold = true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = ripple(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), dpf(10f))
            contentDescription = "Replace the pane's page with this one"
            setOnClickListener {
                val url = popupView?.url
                if (url != null && AddressPolicy.isWeb(url)) { closePopup(); navigate(p, url) } else toast("Nothing to open yet")
            }
        })
        root.addView(bar)
        root.addView(wv, LinearLayout.LayoutParams(-1, 0, 1f))
        root.setOnApplyWindowInsetsListener { v, ins ->
            @Suppress("DEPRECATION") v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom); ins
        }
        d.setContentView(root)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(T.bg))
            setLayout(-1, -1)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        d.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                val v = popupView
                if (v != null && v.canGoBack()) v.goBack() else closePopup(); true
            } else false
        }
        d.setOnDismissListener { if (popupDialog === d) releasePopup() }
        popupDialog = d
        d.show()
    }

    fun onPopupPage(url: String) {
        popupTitle?.text = "Pane ${popupPane?.let { slotNumber(it) } ?: ""} \u00B7 " + (AddressPolicy.label(url).ifBlank { "pop-up" })
    }

    fun closePopup() {
        val d = popupDialog ?: return
        popupDialog = null
        d.dismiss()
        releasePopup()
    }

    private fun releasePopup() {
        popupView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
        popupView = null; popupPane = null; popupTitle = null
    }

    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null

    /** Website file-upload buttons open Android's file picker. */
    fun pickFiles(cb: android.webkit.ValueCallback<Array<Uri>>, params: android.webkit.WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = cb
        return try {
            @Suppress("DEPRECATION") startActivityForResult(params.createIntent(), REQ_FILES); true
        } catch (e: Exception) {
            fileCallback = null; toast("No app available to pick files"); false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILES) {
            fileCallback?.onReceiveValue(android.webkit.WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // ---------------------------------------------------------------- misc

    fun toast(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        if (uiReady) snack.show(msg, action, onAction)
    }

    internal fun allLivePanes(): List<Pane> = state.allPanes().filter { hub.view(it.id) != null }
    internal fun workspaceNumber(p: Pane) = state.workspaces.indexOfFirst { it.id == state.workspaceOf(p.id)?.id } + 1
    internal fun slotNumber(p: Pane) = state.slotOf(p.id) + 1
    internal fun hubRef() = hub
    internal fun stateRef() = state
    internal fun cardRef(id: String): View? = grid.card(id)

    companion object {
        /** Width (dp) websites see in small panes when "Full-size pages" is on. */
        const val MIN_PAGE_WIDTH_DP = 320
        private const val REQ_FILES = 41
    }
}
