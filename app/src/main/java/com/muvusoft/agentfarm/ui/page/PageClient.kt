package com.muvusoft.agentfarm.ui.page

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.net.PageAccess
import com.muvusoft.agentfarm.net.PageLoader
import java.io.ByteArrayInputStream

/**
 * The WebView never reaches the network: requests to the farm origin are fetched natively over the
 * pinned client with the socket's session; everything else is refused. A tapped outside link opens
 * in the phone's browser, never inside the shell.
 */
class PageClient(private val access: () -> PageAccess?, private val onLoaded: () -> Unit) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) = onLoaded()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val path = PageRoute.hostPath(request.url.toString()) ?: return text(403, "Bu adres kabuktan açılmaz.")
        val a = access() ?: return text(503, "Çiftlik şu an bağlı değil.")
        val page = PageLoader.fetch(a, path)
        return WebResourceResponse(page.mime, page.charset, page.status, reason(page.status), emptyMap(), ByteArrayInputStream(page.bytes))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (PageRoute.hostPath(url.toString()) != null) return false
        if (url.scheme == "https" || url.scheme == "http") {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }

    private fun text(status: Int, body: String) =
        WebResourceResponse("text/plain", "utf-8", status, reason(status), emptyMap(), ByteArrayInputStream(body.toByteArray()))

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Status $status"
    }
}

/** What a page's afRemote.js calls as `afShell.post(json)`: one message from the page to its host view. */
class PageBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun post(json: String) = onMessage(json)
}
