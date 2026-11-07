package app.tessera.browser

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Lays out every pane of every workspace. Panes of the current workspace sit in
 * their slots; panes of other workspaces keep the same size but are moved off
 * screen while staying attached and VISIBLE, so their pages keep running.
 * Filtered-out panes keep their slot (no enlarging of the others) and are hidden.
 */
class PaneGrid(context: Context) : ViewGroup(context) {
    /** paneId -> (workspaceId, slot) */
    var locate: (String) -> Pair<String, Int>? = { null }
    var shown: (String) -> Boolean = { true }
    var currentWs = ""
    var columns = 2
        private set
    var columnsOverride: Int? = null
    var expandedSlot = -1
        private set
    val gap get() = dp(4)

    private val cards = LinkedHashMap<String, PaneView>()
    private val last = HashMap<String, Box>()
    private var from: Map<Int, Box> = emptyMap()
    private var frac = 1f
    private var anim: ValueAnimator? = null
    /** Horizontal offset new-workspace panes slide in from (set per switch; 0 = fade only). */
    var enterDx = 0f
    var emptyView: View? = null
        set(v) { field?.let { removeView(it) }; field = v; v?.let { addView(it) } }

    fun cards(): Collection<PaneView> = cards.values
    fun card(id: String) = cards[id]

    fun add(card: PaneView) {
        if (cards.containsKey(card.paneId)) return
        cards[card.paneId] = card
        addView(card, 0)
    }

    fun remove(id: String) {
        cards.remove(id)?.let { removeView(it) }
        last.remove(id)
    }

    /** Recompute layout; animate slot geometry changes (expand/collapse, grid swap). */
    fun update(expanded: Int, animate: Boolean) {
        // A plain refresh must not cut short a running expand/collapse animation.
        if (!animate && expanded == expandedSlot && anim?.isRunning == true) { requestLayout(); return }
        val currentBoxes = (0 until GridGeometry.SLOTS).associateWith { slot -> boxFor(slot) }
        expandedSlot = expanded
        anim?.cancel()
        if (animate && width > 0) {
            from = currentBoxes
            frac = 0f
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = Motion.EXPAND_MS
                interpolator = Motion.standard
                addUpdateListener { frac = it.animatedValue as Float; requestLayout() }
                start()
            }
        } else frac = 1f
        requestLayout()
    }

    private fun areaW() = width - paddingLeft - paddingRight
    private fun areaH() = height - paddingTop - paddingBottom

    private fun targets(w: Int, h: Int): List<Box> {
        columns = columnsOverride ?: GridGeometry.autoColumns(w, h)
        return if (expandedSlot >= 0) GridGeometry.expanded(w, h, expandedSlot, gap, dp(78))
        else GridGeometry.grid(w, h, columns, gap)
    }

    private fun boxFor(slot: Int): Box {
        val w = areaW(); val h = areaH()
        if (w <= 0 || h <= 0) return Box(0, 0, 0, 0)
        val to = targets(w, h)[slot]
        val f = from[slot]
        return if (frac < 1f && f != null) f.lerp(to, frac) else to
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val aw = w - paddingLeft - paddingRight; val ah = h - paddingTop - paddingBottom
        if (aw <= 0 || ah <= 0) return
        val tg = targets(aw, ah)
        cards.values.forEach { c ->
            val (ws, slot) = locate(c.paneId) ?: return@forEach
            val b = if (ws == currentWs) {
                val f = from[slot]; if (frac < 1f && f != null) f.lerp(tg[slot], frac) else tg[slot]
            } else GridGeometry.grid(aw, ah, columns, gap)[slot]
            c.measure(MeasureSpec.makeMeasureSpec(b.w.coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b.h.coerceAtLeast(1), MeasureSpec.EXACTLY))
            last[c.paneId] = b
        }
        emptyView?.measure(MeasureSpec.makeMeasureSpec(aw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ah, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val offX = (r - l) * 3f
        cards.values.forEach { c ->
            val (ws, _) = locate(c.paneId) ?: return@forEach
            val box = last[c.paneId] ?: return@forEach
            c.layout(paddingLeft + box.l, paddingTop + box.t, paddingLeft + box.r, paddingTop + box.b)
            val here = ws == currentWs
            val visible = here && shown(c.paneId)
            // Never GONE/INVISIBLE: hidden panes stay VISIBLE so their sessions keep running.
            c.setPresence(here, visible, offX, enterDx)
            c.importantForAccessibility = if (visible) IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        emptyView?.layout(paddingLeft, paddingTop, r - l - paddingRight, b - t - paddingBottom)
    }
}
