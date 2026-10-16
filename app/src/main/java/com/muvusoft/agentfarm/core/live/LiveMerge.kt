package com.muvusoft.agentfarm.core.live

import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.Role
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.Running
import com.muvusoft.agentfarm.core.now.Waiting

/**
 * A live beat laid onto what a screen already draws. The farm's card says the state; nothing is derived here
 * but where a row goes. Rows a beat tells are provisional: they follow the last row sessions.read gave and
 * the next read (`confirm`) puts the record in their place.
 */
object LiveMerge {
    private val ALL_ROLES = Role.entries.toSet()

    /** Now after a summary beat of `session`: under waiting while it asks, under running while its turn runs, else in neither. */
    fun now(s: NowState, session: String, d: LiveCard, now: Long): NowState {
        val oldRun = s.running.firstOrNull { it.session == session }
        val oldWait = s.waiting.firstOrNull { it.session == session }
        val title = oldWait?.title ?: oldRun?.title ?: s.recent.firstOrNull { it.session == session }?.title.orEmpty()
        val agent = d.agent.ifEmpty { oldWait?.agent ?: oldRun?.agent.orEmpty() }
        val ask = d.asking
        val waiting = if (ask != null) put(s.waiting, Waiting(session, agent, title, ask)) { it.session == session } else s.waiting.filterNot { it.session == session }
        val running = if (ask == null && d.working) {
            val since = d.turnSince ?: oldRun?.turnSince ?: d.step?.since ?: now
            put(s.running, Running(session, agent, title, d.step, since, d.cost ?: oldRun?.cost)) { it.session == session }
        } else {
            s.running.filterNot { it.session == session }
        }
        return s.copy(waiting = waiting, running = running, updatedAt = now)
    }

    /**
     * The Conversation after a full beat. A row whose key a read already carries updates that row in place (a
     * streamed answer grows); any other is provisional, after the last read row. `roles` = what the screen reads.
     */
    fun conversation(s: ConversationState, d: LiveCard, now: Long, roles: Set<Role> = ALL_ROLES): ConversationState {
        var confirmed = s.messages.filterNot { it.provisional }
        var provisional = s.messages.filter { it.provisional }
        for (row in d.messages.orEmpty().filter { it.role in roles }) {
            val read = confirmed.indexOfFirst { it.key != null && it.key == row.key }
            if (read >= 0) {
                confirmed = confirmed.toMutableList().also { it[read] = row.copy(i = it[read].i, turn = it[read].turn, provisional = false) }
                continue
            }
            val told = provisional.indexOfFirst { it.key == row.key }
            provisional = if (told >= 0) provisional.toMutableList().also { it[told] = row } else provisional + row
        }
        return s.copy(
            agent = d.agent.ifEmpty { s.agent },
            updatedAt = now,
            messages = confirmed + numbered(confirmed, provisional),
            running = d.working,
            turnSince = if (d.working) d.turnSince ?: s.turnSince ?: d.step?.since ?: now else null,
            cost = d.cost ?: s.cost,
            live = d.step,
            ask = d.asking,
            steerable = d.steerable,
        )
    }

    /** Where the next sessions.read starts: after the last row a read gave. */
    fun readFrom(s: ConversationState): Int = (s.messages.lastOrNull { !it.provisional }?.i ?: 0) + 1

    /**
     * When a read is due: the beat that ends the turn (what it wrote is now in the transcript). After a
     * reconnect (LiveEvent.Resumed) a read is due as well.
     */
    fun turnEnded(s: ConversationState, d: LiveCard): Boolean = s.running && !d.working

    /**
     * A sessions.read page asked from `readFrom`: its rows join the record, and a provisional row goes when the
     * page carries its key or reaches past it in time. A newer one stays, after the page.
     */
    fun confirm(s: ConversationState, page: List<Message>): ConversationState {
        val confirmed = s.messages.filterNot { it.provisional }
        val last = confirmed.lastOrNull()?.i ?: 0
        val record = confirmed + page.filter { it.i > last }.map { it.copy(provisional = false) }
        val keys = page.mapNotNull { it.key }.toSet()
        val upTo = page.maxOfOrNull { it.ts } ?: Long.MIN_VALUE
        val kept = s.messages.filter { it.provisional && it.key !in keys && it.ts > upTo }
        return s.copy(messages = record + numbered(record, kept))
    }

    /** Provisional rows numbered on from the record, so every row keeps a place of its own on screen. */
    private fun numbered(record: List<Message>, provisional: List<Message>): List<Message> {
        val last = record.lastOrNull()?.i ?: 0
        return provisional.mapIndexed { k, m -> m.copy(i = last + k + 1) }
    }

    private fun <T> put(list: List<T>, row: T, same: (T) -> Boolean): List<T> {
        val at = list.indexOfFirst(same)
        return if (at >= 0) list.toMutableList().also { it[at] = row } else list + row
    }
}
