package com.muvusoft.agentfarm.ui.page

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.ViewClose
import com.muvusoft.agentfarm.core.contract.ViewMsg
import com.muvusoft.agentfarm.core.contract.ViewOpen
import com.muvusoft.agentfarm.core.contract.ViewPost
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.core.view.PageStack
import com.muvusoft.agentfarm.core.view.ShellRequest
import com.muvusoft.agentfarm.net.ConnectionManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One of the farm's own pages, carried by a WebView. While the farm is not online the wait is shown instead. */
@Composable
fun FarmPage(farmId: String, page: String, manager: ConnectionManager, onBack: () -> Unit) {
    // A page's own links open the next page here, with what it asked to be shown; Back walks back
    // through them, then leaves the farm.
    var stack by remember(page) { mutableStateOf(listOf(ShellRequest.Open(page))) }
    BackHandler { PageStack.back(stack)?.let { stack = it } ?: onBack() }
    val current = stack.last()
    // A dead renderer bumps the generation: the old WebView is dropped and a new one opens a new view.
    var generation by remember { mutableIntStateOf(0) }
    val viewId = remember(generation, current) { "v_" + UUID.randomUUID().toString().take(8) }
    val state by manager.state.collectAsState()
    val link = state.farm(farmId)?.link
    Surface(Modifier.fillMaxSize()) {
        if (link is Link.Online) {
            key(generation, current) {
                PageView(
                    farmId = farmId, page = current.page, args = current.args, viewId = viewId, manager = manager,
                    onRendererGone = { generation++ },
                    onOpen = { stack = PageStack.open(stack, it) },
                )
            }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                val text = link?.let { LinkText.of(it, System.currentTimeMillis()).long } ?: stringResource(R.string.farm_unpaired)
                Text(text, Modifier.testTag("page-wait"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PageView(
    farmId: String,
    page: String,
    args: JsonObject?,
    viewId: String,
    manager: ConnectionManager,
    onRendererGone: () -> Unit,
    onOpen: (ShellRequest.Open) -> Unit,
) {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val speaker = AgentFarmApp.of(LocalContext.current).speaker
    val clipboardRefused = stringResource(R.string.clipboard_refused)
    val main = remember { Handler(Looper.getMainLooper()) }
    var web by remember { mutableStateOf<WebView?>(null) }
    // Host messages that arrive before the page has loaded wait here; all of this runs on the main thread.
    val pending = remember { mutableListOf<String>() }
    var loaded by remember { mutableStateOf(false) }
    val chrome = rememberPageChrome()
    val deliver = { json: String ->
        val w = web
        if (loaded && w != null) w.evaluateJavascript(PageRoute.deliverScript(json), null) else pending += json
    }
    val dictation = rememberDictation(deliver)

    LaunchedEffect(viewId) {
        manager.send(farmId, ViewOpen(viewId, page, args))
        manager.frames.collect { f ->
            val post = f.frame as? ViewPost ?: return@collect
            if (f.farm != farmId || post.view != viewId) return@collect
            val json = Codec.json.encodeToString(JsonElement.serializer(), post.message)
            // Frames are emitted on the socket's thread; a WebView only accepts calls on the main thread.
            withContext(Dispatchers.Main.immediate) { deliver(json) }
        }
    }
    DisposableEffect(viewId) {
        // A page's reading is that page's: leaving it ends the reading.
        onDispose {
            speaker.stopReading(viewId)
            manager.send(farmId, ViewClose(viewId))
        }
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
                webChromeClient = chrome
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
                    PageBridge(canDictate = dictation.available) { json ->
                        val msg = runCatching { Codec.json.parseToJsonElement(json) }.getOrNull() ?: return@PageBridge
                        // The bridge calls on its own thread; the WebView and Compose state live on the main one.
                        when (val r = ShellRequest.of(msg)) {
                            null -> manager.send(farmId, ViewMsg(viewId, msg))
                            is ShellRequest.Open -> main.post { onOpen(r) }
                            is ShellRequest.Copy -> main.post {
                                val ok = runCatching { checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText("Agent Farm", r.text)) }.isSuccess
                                deliver(ShellRequest.copyDone(r.token, ok, if (ok) null else clipboardRefused).toString())
                            }
                            is ShellRequest.Voice -> main.post { dictation.start(r.token) }
                            is ShellRequest.Speak -> speaker.read(viewId, r.ask) { state -> main.post { deliver(state.toString()) } }
                            is ShellRequest.SpeakControl -> speaker.control(viewId, r.control)
                            ShellRequest.Refused -> Unit
                        }
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
