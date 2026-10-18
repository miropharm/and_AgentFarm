package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.conversation.ConversationKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What the new-session sheet offers; the session itself is opened by the farm's console.new. */
object NewSession {
    /**
     * The agents a new session can open with (the farm's `console.new` list), the ones used last first: Now's own
     * rows say it, the cards waiting and running now, then the history in the farm's activity order. An agent
     * the farm no longer lists is never offered.
     */
    fun agents(farm: List<String>, s: NowState): List<String> {
        val used = (s.waiting.map { it.agent } + s.running.map { it.agent } + s.recent.map { it.agent }).distinct()
        return used.filter { it in farm } + farm.filter { it !in used }
    }

    /**
     * The Conversation of the session `console.new` opened: its Console card, under the agent the farm named (the
     * one picked when it named none). The engine names the transcript as it starts and the farm titles it; the
     * Conversation asks for both. Null when the answer carries no card.
     */
    fun opened(picked: String, result: JsonElement?): ConversationKey? {
        val o = result as? JsonObject ?: return null
        val card = o.str("id") ?: return null
        return ConversationKey(card = card, transcript = null, agent = o.str("agent") ?: picked, title = "")
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
}
