package com.muvusoft.agentfarm.net

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/** One fetched page or resource, as the WebView interceptor hands it back. */
class PageBytes(val status: Int, val mime: String, val charset: String?, val bytes: ByteArray)

/** Where and with what a farm's pages are fetched right now; null while the farm is not online. */
class PageAccess(val address: String, val session: String, val client: OkHttpClient)

object PageLoader {
    /** GET https://<address><path> with the socket's session; a network failure is a 502 the page can show. */
    fun fetch(access: PageAccess, hostPath: String): PageBytes {
        val req = Request.Builder()
            .url("https://${access.address}$hostPath")
            .header("Authorization", "AF ${access.session}")
            .build()
        return try {
            access.client.newCall(req).execute().use { res ->
                val type = res.body?.contentType()
                val mime = type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream"
                PageBytes(res.code, mime, type?.charset()?.name(), res.body?.bytes() ?: ByteArray(0))
            }
        } catch (e: IOException) {
            PageBytes(502, "text/plain", "utf-8", "Could not reach the farm: ${e.message}".toByteArray())
        }
    }
}
