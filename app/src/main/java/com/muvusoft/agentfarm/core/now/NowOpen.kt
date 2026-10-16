package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.core.view.ShellRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Where a Now row leads. */
sealed interface NowTarget {
    /** A session's own Conversation. */
    data class Conversation(val key: ConversationKey) : NowTarget

    /** One of the farm's pages: Needs, for something that waits on no session. */
    data class Page(val open: ShellRequest.Open) : NowTarget
}

/** Where a link on one of the farm's pages leads: Now and a session are the phone's own screens. */
sealed interface PageLink {
    data object Now : PageLink

    data class Conversation(val key: ConversationKey) : PageLink

    data class Page(val open: ShellRequest.Open) : PageLink
}

/**
 * A row's id is a Console card's (waiting, running) or a session's (the history, a need). The Conversation
 * gets both where the farm told them: a card writes and streams, its transcript is what is read; a session no
 * card hosts is read only.
 */
object NowOpen {
    const val NEEDS_PAGE = "needs"
    const val SESSION_PAGE = "session"
    const val CONSOLE_PAGE = "console"

    /**
     * A page's link (afnav): the farm's Now and its plain Console are Now here (the Console's cards are its rows);
     * a session is its Conversation, unless the link points into one step of it (`ts`), which only the page
     * places; the Console on one agent and every other page are the next page.
     */
    fun link(s: NowState, open: ShellRequest.Open): PageLink {
        val args = open.args
        return when (open.page) {
            PageRoute.HOME_PAGE -> PageLink.Now
            CONSOLE_PAGE -> if (args.str("agentId").isNullOrEmpty()) PageLink.Now else PageLink.Page(open)
            SESSION_PAGE -> {
                val id = args.str("sessionId")
                val into = ((args?.get("ts") as? JsonPrimitive)?.doubleOrNull ?: 0.0) > 0
                val to = if (id.isNullOrEmpty() || into) null else of(s, id) as? NowTarget.Conversation
                to?.let { PageLink.Conversation(it.key) } ?: PageLink.Page(open)
            }
            else -> PageLink.Page(open)
        }
    }

    private fun JsonObject?.str(name: String): String? = (this?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

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
