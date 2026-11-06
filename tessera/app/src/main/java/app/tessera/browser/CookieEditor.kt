package app.tessera.browser

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.graphics.Typeface

/**
 * Full-screen cookie editor for ONE pane's session. Locked panes are view-only.
 * Android exposes cookie names/values for a URL but not their path, expiry or
 * HttpOnly flags; edits are written for the site root and verified after.
 */
class CookieEditor(private val a: MainActivity, private val pane: Pane) {
    private val hub = a.hubRef()
    private val dialog = Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar)
    private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val storage = a.label("Reading site storage…", 12.5f, T.text2)
    private var query = ""
    private val url get() = pane.url
    private val cm get() = hub.cookieManager(pane)

    fun show() {
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(T.bg) }
        val top = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(4), a.dp(8), a.dp(12), a.dp(8)); setBackgroundColor(T.surface)
        }
        top.addView(a.iconButton(Icon.BACK, "Close cookie editor") { dialog.dismiss() })
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(a.label("Cookies & site data", 17f, T.text, bold = true))
        titles.addView(a.label("Pane ${a.slotNumber(pane)} · Workspace ${a.workspaceNumber(pane)} · " +
            (AddressPolicy.label(url).ifBlank { "no site open" }), 12f, T.text2).apply { setPadding(0, a.dp(3), 0, 0) })
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(6) })
        root.addView(top)

        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(24)) }
        body.addView(a.label("These belong only to this pane's session (profile …${pane.profile.takeLast(6)}). " +
            "No other pane can see or change them.", 13f, T.text2).apply { setLineSpacing(0f, 1.2f) })
        if (pane.locked) body.addView(banner("Locked — view only. Unlock this pane to edit cookies."), lp(top = 12))

        if (!AddressPolicy.isWeb(url)) {
            body.addView(a.label("Open a website in this pane to see its cookies.", 14f, T.text).apply { setPadding(0, a.dp(24), 0, 0) })
        } else {
            val search = EditText(a).apply {
                hint = "Search cookie names"; setHintTextColor(T.text3); setTextColor(T.text); setSingleLine(); textSize = 14f
                background = rounded(T.surface2, a.dpf(12f), T.stroke, 1); setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, x: Int, y: Int, z: Int) = Unit
                    override fun afterTextChanged(e: Editable?) { query = e?.toString().orEmpty().trim(); render() }
                })
            }
            body.addView(search, lp(top = 14))
            body.addView(list, lp(top = 8))
            val actions = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(a.pillButton("Add cookie", filled = true) { if (guard()) edit(null, "") }.apply { alpha = if (pane.locked) .45f else 1f })
            actions.addView(a.pillButton("Delete all for this site", filled = false, danger = true) { if (guard()) deleteAll() }.apply { alpha = if (pane.locked) .45f else 1f },
                LinearLayout.LayoutParams(-2, -2).apply { leftMargin = a.dp(10) })
            body.addView(actions, lp(top = 14))
            body.addView(a.label("STORAGE ON THIS PAGE", 11f, T.text3, bold = true).apply { letterSpacing = .08f }, lp(top = 22))
            body.addView(storage, lp(top = 6))
            body.addView(a.label("Android doesn't reveal each cookie's path, expiry or HttpOnly flag. Edits are saved for the whole site and checked afterwards.",
                11.5f, T.text3).apply { setLineSpacing(0f, 1.2f) }, lp(top = 18))
            render()
            StorageProbe.probe(hub.view(pane.id)) { info ->
                storage.text = if (info == null) "Couldn't read storage (page still loading or blocked)."
                    else "Local storage: ${count(info.localKeys)} · Session storage: ${count(info.sessionKeys)}\nIndexedDB: ${info.indexedDb}\nOrigin: ${info.origin}"
            }
        }
        root.addView(ScrollView(a).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(T.bg))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
    }

    private fun count(n: Int) = if (n < 0) "blocked" else "$n key${if (n == 1) "" else "s"}"

    private fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(top) }

    private fun banner(text: String) = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(T.lockSoft, a.dpf(12f), T.lock, 1); setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))
        addView(ImageView(a).apply { setImageDrawable(IconDrawable(Icon.LOCK, T.lock)) }, LinearLayout.LayoutParams(a.dp(18), a.dp(18)))
        addView(a.label(text, 13f, T.text), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(10) })
    }

    private fun guard(): Boolean {
        if (!pane.locked) return true
        a.blocked(pane)
        a.toast("Locked — unlock this pane to edit cookies")
        return false
    }

    private fun cookies(): List<Pair<String, String>> =
        (cm.getCookie(url) ?: "").split(';').map { it.trim() }.filter { it.isNotEmpty() }.map {
            val i = it.indexOf('='); if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
        }

    private fun render() {
        list.removeAllViews()
        val all = cookies()
        val shown = all.filter { query.isEmpty() || it.first.contains(query, ignoreCase = true) }
        list.addView(a.label(if (all.isEmpty()) "No cookies for this site yet." else "${all.size} cookie${if (all.size == 1) "" else "s"} for ${AddressPolicy.label(url)}" +
            if (shown.size != all.size) " · ${shown.size} shown" else "", 12.5f, T.text3), lp(top = 2))
        shown.forEach { (name, value) ->
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                background = ripple(rounded(T.surface, a.dpf(12f), T.stroke, 1), a.dpf(12f))
                setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
                addView(a.label(name, 14f, T.text, bold = true))
                addView(a.label(value.ifEmpty { "(empty)" }, 12.5f, T.text2).apply {
                    typeface = Typeface.MONOSPACE; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, a.dp(4), 0, 0)
                })
                setOnClickListener { if (guard()) edit(name, value) }
                contentDescription = "Cookie $name" + if (pane.locked) ", locked" else ", tap to edit"
            }
            list.addView(row, lp(top = 8))
        }
    }

    private fun edit(name: String?, value: String) {
        val s = Sheet(a, if (name == null) "Add cookie" else "Edit cookie", AddressPolicy.label(url))
        fun field(text: String, hint: String, mono: Boolean) = EditText(a).apply {
            setText(text); this.hint = hint; setHintTextColor(T.text3); setTextColor(T.text); textSize = 14f
            if (mono) typeface = Typeface.MONOSPACE
            background = rounded(T.surface2, a.dpf(12f), T.stroke, 1); setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
        }
        val nameField = field(name ?: "", "Name", false).apply { setSingleLine(); isEnabled = name == null }
        val valueField = field(value, "Value", true).apply { maxLines = 6 }
        s.custom(nameField); s.custom(valueField)
        val buttons = mutableListOf(a.pillButton("Cancel", filled = false) { s.dismiss() })
        if (name != null) buttons += a.pillButton("Delete", filled = false, danger = true) { s.dismiss(); confirmDelete(name) }
        buttons += a.pillButton("Save", filled = true) {
            val n = nameField.text.toString().trim(); val v = valueField.text.toString()
            if (n.isEmpty() || n.any { it == '=' || it == ';' || it.isWhitespace() }) { nameField.error = "Use a name without spaces, = or ;"; return@pillButton }
            if (v.any { it == ';' || it == '\n' || it == '\r' }) { valueField.error = "Values can't contain ; or line breaks"; return@pillButton }
            s.dismiss()
            val secure = if (url.startsWith("https", true)) "; Secure" else ""
            cm.setCookie(url, "$n=$v; Path=/$secure") { ok ->
                cm.flush()
                val now = cookies().filter { it.first == n }.map { it.second }
                a.toast(when {
                    !ok -> "Android refused this cookie — nothing changed"
                    now.size > 1 -> "Saved, but the site also has another “$n” cookie with a different path"
                    now.firstOrNull() == v -> "Saved “$n”"
                    else -> "Saved, but the page still reports a different value"
                })
                render()
            }
        }
        s.buttons(*buttons.toTypedArray())
        s.show()
    }

    private fun confirmDelete(name: String) {
        a.confirm("Delete “$name”?", "This pane may be signed out of ${AddressPolicy.label(url)} if this is a login cookie. This can't be undone.", "Delete") {
            remove(name) { gone ->
                a.toast(if (gone) "Deleted “$name”" else "Couldn't delete “$name” — it may be set on a sub-path. Use “End session” for a full reset.")
                render()
            }
        }
    }

    private fun deleteAll() {
        val names = cookies().map { it.first }
        if (names.isEmpty()) { a.toast("No cookies to delete"); return }
        a.confirm("Delete all ${names.size} cookies?", "Only for ${AddressPolicy.label(url)} in this pane. This pane will likely be signed out of that site. This can't be undone.", "Delete all") {
            var left = names.size; var failed = 0
            names.forEach { n -> remove(n) { gone -> if (!gone) failed++; if (--left == 0) {
                a.toast(if (failed == 0) "Deleted ${names.size} cookies" else "Deleted ${names.size - failed}; $failed couldn't be removed"); render()
            } } }
        }
    }

    /** Expire the cookie at the site root, then for the parent domain; verify by re-reading. */
    private fun remove(name: String, done: (Boolean) -> Unit) {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""
        cm.setCookie(url, "$name=; Path=/; Max-Age=0") {
            if (cookies().none { it.first == name }) { cm.flush(); done(true); return@setCookie }
            cm.setCookie(url, "$name=; Path=/; Domain=$host; Max-Age=0") {
                val parent = host.substringAfter('.', "")
                val finish = { cm.flush(); done(cookies().none { it.first == name }) }
                if (cookies().none { it.first == name } || !parent.contains('.')) finish()
                else cm.setCookie(url, "$name=; Path=/; Domain=.$parent; Max-Age=0") { finish() }
            }
        }
    }
}
