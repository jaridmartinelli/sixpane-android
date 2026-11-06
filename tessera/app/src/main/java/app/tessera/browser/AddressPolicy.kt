package app.tessera.browser

import java.net.URLEncoder
import java.util.Locale

/** Decides whether typed text is a web address or a Google search. Pure; unit tested. */
object AddressPolicy {
    sealed class Result {
        data class Open(val url: String) : Result()
        data class Search(val url: String, val query: String) : Result()
        object Empty : Result()
    }

    private val schemeUrl = Regex("^(https?)://[^\\s/?#]+.*$", RegexOption.IGNORE_CASE)
    private val hostLike = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63})(:\\d{1,5})?([/?#].*)?$",
        RegexOption.IGNORE_CASE
    )
    private val localHost = Regex("^(localhost|(\\d{1,3}\\.){3}\\d{1,3})", RegexOption.IGNORE_CASE)

    fun resolve(raw: String): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Empty
        if (text.equals("about:blank", ignoreCase = true)) return Result.Open("about:blank")
        if (schemeUrl.matches(text)) return Result.Open(text)
        if (!text.contains(' ') && hostLike.matches(text)) {
            val scheme = if (localHost.containsMatchIn(text)) "http" else "https"
            return Result.Open("$scheme://$text")
        }
        return Result.Search(searchUrl(text), text)
    }

    fun searchUrl(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")

    /** Short label for a URL: host without "www.", or "New tab" for an empty page. */
    fun label(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        val noScheme = url.substringAfter("://", url)
        val host = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').lowercase(Locale.ROOT)
        return host.removePrefix("www.")
    }

    fun isWeb(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true)
}
