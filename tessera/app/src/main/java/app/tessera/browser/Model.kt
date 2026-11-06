package app.tessera.browser

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One pane = one independent browser session bound to one never-reused WebView profile. */
class Pane(
    val id: String,
    val profile: String,
    var url: String = "",
    var title: String = "",
    var color: Int = -1,
    var locked: Boolean = false,
    var desktop: Boolean = false,
    var zoom: Int = 100,
) {
    companion object {
        fun fresh() = Pane(id = "p" + uid(), profile = "tp_" + uid())
        fun uid() = UUID.randomUUID().toString().replace("-", "")
    }
}

class Workspace(val id: String, val panes: MutableList<Pane>) {
    val allLocked get() = panes.all { it.locked }
    val lockedCount get() = panes.count { it.locked }
    companion object {
        fun fresh() = Workspace("w" + Pane.uid(), MutableList(GridGeometry.SLOTS) { Pane.fresh() })
    }
}

data class Bookmark(val url: String, val title: String)

enum class LayoutMode { AUTO, TWO_BY_THREE, THREE_BY_TWO }

class AppState {
    val workspaces = mutableListOf<Workspace>()
    var currentId = ""
    var activePaneId = ""
    var expandedPaneId: String? = null
    var layout = LayoutMode.AUTO
    var filter = PaneFilter()
    val bookmarks = mutableListOf<Bookmark>()
    /** Every profile name ever issued; guarantees a name is never reused. */
    val issuedProfiles = linkedSetOf<String>()
    /** Retired profiles whose on-disk data still has to be deleted. */
    val pendingDeletes = linkedSetOf<String>()
    var keepCpuAwake = true
    var keepWifiAwake = true
    var onboarded = false
    /** Small panes render a normal phone-width page, scaled down to fit. */
    var fitSmallPanes = true

    fun current(): Workspace = workspaces.firstOrNull { it.id == currentId } ?: workspaces.first()
    fun allPanes(): List<Pane> = workspaces.flatMap { it.panes }
    fun pane(id: String?): Pane? = allPanes().firstOrNull { it.id == id }
    fun workspaceOf(paneId: String): Workspace? = workspaces.firstOrNull { w -> w.panes.any { it.id == paneId } }
    fun slotOf(paneId: String): Int = workspaceOf(paneId)?.panes?.indexOfFirst { it.id == paneId } ?: -1

    /** New pane whose profile name has never been issued before. */
    fun newPane(): Pane {
        var p = Pane.fresh()
        while (p.profile in issuedProfiles) p = Pane.fresh()
        issuedProfiles += p.profile
        return p
    }

    fun newWorkspace(): Workspace =
        Workspace("w" + Pane.uid(), MutableList(GridGeometry.SLOTS) { newPane() })

    fun ensureValid() {
        if (workspaces.isEmpty()) workspaces += newWorkspace()
        while (workspaces.size > MAX_WORKSPACES) workspaces.removeAt(workspaces.size - 1)
        if (workspaces.none { it.id == currentId }) currentId = workspaces.first().id
        if (pane(activePaneId) == null || workspaceOf(activePaneId)?.id != currentId)
            activePaneId = current().panes.first().id
        if (expandedPaneId != null && workspaceOf(expandedPaneId!!)?.id != currentId) expandedPaneId = null
        allPanes().forEach { issuedProfiles += it.profile }
    }

    companion object {
        const val MAX_WORKSPACES = 7
    }
}

/** JSON encoding of [AppState]. Pure (org.json only); round-trip unit tested. */
object StateCodec {
    fun encode(s: AppState): String = JSONObject().apply {
        put("v", 1)
        put("current", s.currentId)
        put("active", s.activePaneId)
        put("expanded", s.expandedPaneId ?: "")
        put("layout", s.layout.name)
        put("filterColors", JSONArray(s.filter.colors.toList()))
        put("filterUrl", s.filter.urlText)
        put("cpu", s.keepCpuAwake)
        put("wifi", s.keepWifiAwake)
        put("onboarded", s.onboarded)
        put("fitSmall", s.fitSmallPanes)
        put("issued", JSONArray(s.issuedProfiles.toList()))
        put("pendingDeletes", JSONArray(s.pendingDeletes.toList()))
        put("bookmarks", JSONArray().apply {
            s.bookmarks.forEach { put(JSONObject().put("url", it.url).put("title", it.title)) }
        })
        put("workspaces", JSONArray().apply {
            s.workspaces.forEach { w ->
                put(JSONObject().put("id", w.id).put("panes", JSONArray().apply {
                    w.panes.forEach { p ->
                        put(JSONObject().put("id", p.id).put("profile", p.profile).put("url", p.url)
                            .put("title", p.title).put("color", p.color).put("locked", p.locked)
                            .put("desktop", p.desktop).put("zoom", p.zoom))
                    }
                }))
            }
        })
    }.toString()

    fun decode(json: String): AppState {
        val o = JSONObject(json)
        val s = AppState()
        s.currentId = o.optString("current")
        s.activePaneId = o.optString("active")
        s.expandedPaneId = o.optString("expanded").ifBlank { null }
        s.layout = runCatching { LayoutMode.valueOf(o.optString("layout")) }.getOrDefault(LayoutMode.AUTO)
        val fc = o.optJSONArray("filterColors") ?: JSONArray()
        s.filter = PaneFilter((0 until fc.length()).map { fc.getInt(it) }.toSet(), o.optString("filterUrl"))
        s.keepCpuAwake = o.optBoolean("cpu", true)
        s.keepWifiAwake = o.optBoolean("wifi", true)
        s.onboarded = o.optBoolean("onboarded", false)
        s.fitSmallPanes = o.optBoolean("fitSmall", true)
        o.optJSONArray("issued")?.let { a -> for (i in 0 until a.length()) s.issuedProfiles += a.getString(i) }
        o.optJSONArray("pendingDeletes")?.let { a -> for (i in 0 until a.length()) s.pendingDeletes += a.getString(i) }
        o.optJSONArray("bookmarks")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { s.bookmarks += Bookmark(it.getString("url"), it.optString("title")) }
        }
        val ws = o.optJSONArray("workspaces") ?: JSONArray()
        for (i in 0 until ws.length()) {
            val w = ws.getJSONObject(i)
            val pa = w.getJSONArray("panes")
            val panes = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                Pane(p.getString("id"), p.getString("profile"), p.optString("url"), p.optString("title"),
                    p.optInt("color", -1), p.optBoolean("locked"), p.optBoolean("desktop"), p.optInt("zoom", 100))
            }.toMutableList()
            if (panes.size == GridGeometry.SLOTS) s.workspaces += Workspace(w.getString("id"), panes)
        }
        s.ensureValid()
        return s
    }
}
