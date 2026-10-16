package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.view.ShellRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where a Now row leads. */
sealed interface NowTarget {
    /** A session's own Conversation. */
    data class Conversation(val key: ConversationKey) : NowTarget

    /** One of the farm's pages: Needs, for something that waits on no session. */
    data class Page(val open: ShellRequest.Open) : NowTarget
}

/**
 * A row's id is a Console card's (waiting, running) or a session's (the history, a need). The Conversation
 * gets both where the farm told them: a card writes and streams, its transcript is what is read; a session no
 * card hosts is read only.
 */
object NowOpen {
    const val NEEDS_PAGE = "needs"
    const val SESSION_PAGE = "session"

    /** The farm's own page of a Conversation's session, for what the native screen does not draw. */
    fun page(key: ConversationKey): ShellRequest.Open =
        ShellRequest.Open(SESSION_PAGE, JsonObject(mapOf("sessionId" to JsonPrimitive(key.transcript ?: key.card.orEmpty()))))

    fun of(s: NowState, id: String): NowTarget {
        val wait = s.waiting.firstOrNull { it.session == id }
        if (wait != null && wait.session == wait.need) return NowTarget.Page(ShellRequest.Open(NEEDS_PAGE))
        val run = s.running.firstOrNull { it.session == id }
        val recent = s.recent.firstOrNull { it.session == id }
        val card = if (id in s.cards || run != null) id else s.cards.entries.firstOrNull { it.value == id }?.key
        val transcript = if (card == id) s.cards[id] ?: run?.transcript else id
        val agent = wait?.agent ?: run?.agent ?: recent?.agent.orEmpty()
        val title = (wait?.title ?: run?.title ?: recent?.title).orEmpty()
        return NowTarget.Conversation(ConversationKey(card, transcript, agent, title))
    }
}
