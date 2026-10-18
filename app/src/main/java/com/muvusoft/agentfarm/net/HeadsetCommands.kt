package com.muvusoft.agentfarm.net

import android.util.Log
import com.muvusoft.agentfarm.core.handsfree.StatusCall
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.state.Link
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the headset button's presses do (S-10, S-11), against the farm a press asks (StatusCall.farmOf), its link
 * woken first when it rests (core/power/LinkRest): one press, the farm says how it is (now.summary) and its first
 * question is answered by voice; two presses, a new session by voice (console.new). The loop speaks and listens.
 */
class HeadsetCommands(
    private val scope: CoroutineScope,
    private val manager: ConnectionManager,
    private val loop: HandsFreeLoop,
    private val voice: () -> Locale,
) {
    fun status() {
        scope.launch {
            val farm = reach() ?: return@launch
            val r = manager.read(farm, StatusCall.OP, StatusCall.args(voice().toLanguageTag()))
            Log.i(TAG, "status ${if (r == null) "unanswered" else if (r.ok) "said" else "refused"}")
            loop.status(farm, StatusCall.of(r, voice().language))
        }
    }

    fun newSession() {
        scope.launch {
            val farm = reach() ?: return@launch
            val listed = manager.read(farm, OP_NEW)?.takeIf { it.ok }?.let { Share.agents(it.result) }
            loop.newSession(farm, listed ?: manager.state.value.farm(farm)?.agents.orEmpty()) { call ->
                manager.request(farm, call.op, call.args, OPEN_MS)
            }
        }
    }

    private suspend fun reach(): String? {
        val farm = StatusCall.farmOf(manager.state.value) ?: return null
        if (manager.state.value.farm(farm)?.link !is Link.Online) {
            manager.wake(farm)
            withTimeoutOrNull(WAKE_MS) { manager.state.first { it.farm(farm)?.link is Link.Online } }
        }
        return farm
    }

    private companion object {
        const val TAG = "AFHeadset"
        const val OP_NEW = "console.new"

        /** How long a resting link is given to open again; past it a press says the farm does not answer. */
        const val WAKE_MS = 8_000L

        /** How long the farm is given to open the session; past it the call waits in the outbox, and the phone says so. */
        const val OPEN_MS = 15_000L
    }
}
