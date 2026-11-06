package app.tessera.browser

import java.util.Locale

/** Active pane filter. Colors are OR-ed together; the URL text must also match (AND). */
data class PaneFilter(val colors: Set<Int> = emptySet(), val urlText: String = "") {
    val active: Boolean get() = colors.isNotEmpty() || urlText.isNotBlank()
}

object FilterPolicy {
    fun matches(url: String, color: Int, filter: PaneFilter): Boolean {
        if (!filter.active) return true
        if (filter.colors.isNotEmpty() && color !in filter.colors) return false
        val q = filter.urlText.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return true
        if (!AddressPolicy.isWeb(url)) return false
        val u = url.lowercase(Locale.ROOT).substringAfter("://")
        val needle = q.substringAfter("://")
        return u.contains(needle) || u.removePrefix("www.").contains(needle)
    }
}
