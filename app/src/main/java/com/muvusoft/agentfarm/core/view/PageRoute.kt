package com.muvusoft.agentfarm.core.view

import java.net.URI

/**
 * The WebView only ever sees one made-up origin; every request to it is answered by native code from
 * the paired farm, and every request anywhere else is blocked. This decides which is which.
 */
object PageRoute {
    const val ORIGIN_HOST = "farm.agentfarm"
    const val ORIGIN = "https://$ORIGIN_HOST"

    /** The page a farm opens on: Agent Farm's "Now" page. */
    const val HOME_PAGE = "now"

    private val PAGE = Regex("^[A-Za-z0-9._-]+$")

    /** Whether [id] can name a page (or a view): no path, no query, nothing to escape. */
    fun isPage(id: String): Boolean = PAGE.matches(id)

    /** The URL the WebView opens for a page and view. */
    fun pageUrl(page: String, view: String): String {
        require(PAGE.matches(page) && PAGE.matches(view)) { "bad page or view id" }
        return "$ORIGIN/view/$page?view=$view"
    }

    /** The host path to fetch for a WebView request, or null when the request must not leave the phone. */
    fun hostPath(url: String): String? {
        val u = try { URI(url) } catch (_: Exception) { return null }
        if (u.scheme != "https" || u.host != ORIGIN_HOST || u.port != -1) return null
        val path = u.rawPath ?: return null
        if (path.contains("..") || path.contains("%2e", ignoreCase = true) || path.contains('\\')) return null
        return when {
            path.startsWith("/view/") && PAGE.matches(path.removePrefix("/view/")) ->
                path + (u.rawQuery?.let { "?$it" } ?: "")
            path.startsWith("/res/") && path.length > "/res/".length -> path
            else -> null
        }
    }

    /** Script that hands a host message to the page the way VS Code does: a `message` event on window. */
    fun deliverScript(messageJson: String): String =
        "window.dispatchEvent(new MessageEvent('message',{data:$messageJson}));"
}
