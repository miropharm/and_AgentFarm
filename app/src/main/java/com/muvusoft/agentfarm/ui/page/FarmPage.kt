package com.muvusoft.agentfarm.ui.page

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.ViewClose
import com.muvusoft.agentfarm.core.contract.ViewMsg
import com.muvusoft.agentfarm.core.contract.ViewOpen
import com.muvusoft.agentfarm.core.contract.ViewPost
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.net.ConnectionManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/** One of the farm's own pages, carried by a WebView. While the farm is not online the wait is shown instead. */
@Composable
fun FarmPage(farmId: String, page: String, manager: ConnectionManager, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // A dead renderer bumps the generation: the old WebView is dropped and a new one opens a new view.
    var generation by remember { mutableIntStateOf(0) }
    val viewId = remember(generation) { "v_" + UUID.randomUUID().toString().take(8) }
    val state by manager.state.collectAsState()
    val link = state.farm(farmId)?.link
    Surface(Modifier.fillMaxSize()) {
        if (link is Link.Online) {
            key(generation) { PageView(farmId, page, viewId, manager, onRendererGone = { generation++ }) }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                val text = link?.let { LinkText.of(it, System.currentTimeMillis()).long } ?: "Bu çiftlik artık eşli değil."
                Text(text, Modifier.testTag("page-wait"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PageView(farmId: String, page: String, viewId: String, manager: ConnectionManager, onRendererGone: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    // Host messages that arrive before the page has loaded wait here; all of this runs on the main thread.
    val pending = remember { mutableListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    val deliver = { json: String ->
        val w = web
        if (loaded && w != null) w.evaluateJavascript(PageRoute.deliverScript(json), null) else pending += json
    }

    LaunchedEffect(viewId) {
        manager.send(farmId, ViewOpen(viewId, page))
        manager.frames.collect { f ->
            val post = f.frame as? ViewPost ?: return@collect
            if (f.farm != farmId || post.view != viewId) return@collect
            val json = Codec.json.encodeToString(JsonElement.serializer(), post.message)
            // Frames are emitted on the socket's thread; a WebView only accepts calls on the main thread.
            withContext(Dispatchers.Main.immediate) { deliver(json) }
        }
    }
    DisposableEffect(viewId) {
        onDispose { manager.send(farmId, ViewClose(viewId)) }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().testTag("farm-page"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = PageClient(
                    access = { manager.pageAccess(farmId) },
                    onLoaded = {
                        loaded = true
                        pending.forEach { evaluateJavascript(PageRoute.deliverScript(it), null) }
                        pending.clear()
                    },
                    onRendererGone = onRendererGone,
                )
                addJavascriptInterface(
                    PageBridge { json ->
                        val msg = runCatching { Codec.json.parseToJsonElement(json) }.getOrNull() ?: return@PageBridge
                        manager.send(farmId, ViewMsg(viewId, msg))
                    },
                    "afShell",
                )
                loadUrl(PageRoute.pageUrl(page, viewId))
                web = this
            }
        },
        onRelease = { it.clearCache(true); it.destroy() },
    )
}
