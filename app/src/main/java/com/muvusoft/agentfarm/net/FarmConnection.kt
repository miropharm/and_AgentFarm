package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.contract.Challenge
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.Frame
import com.muvusoft.agentfarm.core.contract.Hello
import com.muvusoft.agentfarm.core.contract.Refuse
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.link.Reconnect
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What a connection reports upward; the manager turns it into state with ShellReducer. */
class FarmConnectionEvents(
    val onLink: (Link) -> Unit,
    val onFrame: (frame: Frame, address: String) -> Unit,
    /** The resumeAfter the next hello carries (null on a first connection). */
    val resumeAfter: () -> Long?,
)

/**
 * One paired farm's socket: tries its addresses (last good first), answers the challenge with the
 * Keystore signature, reconnects with backoff, and stops on a refusal no retry can fix.
 */
class FarmConnection(
    private val farm: PairedFarm,
    private val events: FarmConnectionEvents,
    private val clock: () -> Long = System::currentTimeMillis,
    private val client: OkHttpClient = PinnedTls.client(farm.fp),
) {
    @Volatile private var socket: WebSocket? = null
    @Volatile private var welcomed = false
    private var lastGood: String? = null
    @Volatile private var current: String? = null
    private var job: Job? = null

    val online: Boolean get() = welcomed

    /** The page credential of the open socket (welcome.session); memory only, gone when the socket closes. */
    @Volatile var session: String? = null
        private set

    /** Pages of this farm can be fetched while its socket is welcomed. */
    fun pageAccess(): PageAccess? {
        val s = session ?: return null
        val a = current ?: return null
        return if (welcomed) PageAccess(a, s, client) else null
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        socket?.close(1000, "stopped")
        socket = null
        welcomed = false
        session = null
        events.onLink(Link.Offline("stopped", clock()))
    }

    /** Sends a frame when the farm has welcomed this device; false otherwise (the caller keeps it queued). */
    fun send(frame: Frame): Boolean = welcomed && socket?.send(Codec.encode(frame)) == true

    private suspend fun loop() {
        var attempt = 0
        while (true) {
            for (address in Reconnect.order(farm.addresses, lastGood)) {
                attempt++
                events.onLink(Link.Connecting(address, attempt, clock()))
                val end = session(address)
                if (end.welcomed) { lastGood = address; attempt = 0 }
                val refuse = end.refuse
                if (refuse != null && Reconnect.isFinal(refuse.reason)) return
                if (end.welcomed) break
            }
            if (attempt > 0) events.onLink(Link.Offline("unreachable", clock()))
            delay(Reconnect.delayMs(attempt.coerceAtLeast(1), Math.random()))
        }
    }

    private class End(val welcomed: Boolean, val refuse: Refuse?)

    private suspend fun session(address: String): End {
        current = address
        val done = CompletableDeferred<End>()
        var refused: Refuse? = null
        var wasWelcomed = false
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = Codec.decode(text) ?: return
                when (frame) {
                    is Challenge -> webSocket.send(Codec.encode(hello(frame)))
                    is Welcome -> { session = frame.session; welcomed = true; wasWelcomed = true }
                    is Refuse -> refused = frame
                    else -> Unit
                }
                if (frame !is Challenge) events.onFrame(frame, address)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish() }

            private fun finish() {
                welcomed = false
                session = null
                if (!done.isCompleted && wasWelcomed && refused == null) events.onLink(Link.Offline("closed", clock()))
                done.complete(End(wasWelcomed, refused))
            }
        }
        socket = client.newWebSocket(Request.Builder().url("wss://$address/ws").build(), listener)
        return try {
            done.await()
        } finally {
            socket?.cancel()
            socket = null
        }
    }

    private fun hello(ch: Challenge): Hello {
        val signature = DeviceKeys.sign(farm.key, Codec.signedText(farm.id, farm.device, ch.nonce))
        return Hello(farm.device, CONTRACT_VERSION, signature, events.resumeAfter())
    }
}
