package com.muvusoft.agentfarm.ui.page

import android.content.Intent
import android.net.Uri
import android.util.Log
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
class PageClient(
    private val access: () -> PageAccess?,
    private val onLoaded: () -> Unit,
    /** The renderer died (crash or memory); the caller drops this WebView and builds a new one. */
    private val onRendererGone: () -> Unit,
) : WebViewClient() {
    // Unhandled, a renderer death kills the whole app process - the link service with it.
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "renderer gone (crash=${detail.didCrash()})")
        onRendererGone()
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { Log.i(TAG, "started $url") }

    override fun onPageFinished(view: WebView, url: String) {
        Log.i(TAG, "finished $url")
        onLoaded()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
        Log.w(TAG, "error ${error.errorCode} ${error.description} ${request.url}")
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val path = PageRoute.hostPath(request.url.toString())
        if (path == null) {
            Log.i(TAG, "blocked ${request.url}")
            return text(403, "Bu adres kabuktan açılmaz.")
        }
        val a = access() ?: return text(503, "Çiftlik şu an bağlı değil.").also { Log.i(TAG, "offline $path") }
        val page = PageLoader.fetch(a, path)
        Log.i(TAG, "${page.status} ${page.mime} ${page.bytes.size}B $path")
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

const val TAG = "AFPage"

/** What a page's afRemote.js calls as `afShell.post(json)`: one message from the page to its host view. */
class PageBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun post(json: String) = onMessage(json)
}
