package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.view.ShellRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where a Now row leads: the farm's own session page for a session (its transcript, by the session's id), the
 * Needs page for something that waits on no session. The native Conversation takes the session's place.
 */
object NowOpen {
    const val SESSION_PAGE = "session"
    const val NEEDS_PAGE = "needs"

    fun of(s: NowState, id: String): ShellRequest.Open {
        val wait = s.waiting.firstOrNull { it.session == id }
        val transcript = s.cards[id] ?: s.running.firstOrNull { it.session == id }?.transcript
        return when {
            transcript != null -> session(transcript)
            wait != null && wait.session == wait.need -> ShellRequest.Open(NEEDS_PAGE)
            else -> session(id)
        }
    }

    private fun session(id: String) = ShellRequest.Open(SESSION_PAGE, JsonObject(mapOf("sessionId" to JsonPrimitive(id))))
}
