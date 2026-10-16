package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.ConsoleRow
import com.muvusoft.agentfarm.core.contract.NeedRow
import com.muvusoft.agentfarm.core.contract.NeedsList
import com.muvusoft.agentfarm.core.contract.RecentPage
import com.muvusoft.agentfarm.core.contract.RecentRow
import com.muvusoft.agentfarm.core.live.Live
import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.Asks
import com.muvusoft.agentfarm.core.state.Link
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Now out of the farm's own answers: console.sessions (view summary) for the cards, needs.list for what waits,
 * sessions.recent (view page) for the history. Nothing is judged here that the farm already said: a card asks
 * when its `asking` says so, runs when its status is the Console's "working", and a need is the farm's own row.
 */
object NowBuild {
    private const val PERMISSION = "permission"
    private const val CONSOLE_QUESTION = "console-question"

    fun of(farm: String, link: Link, updatedAt: Long, cards: List<ConsoleRow>, page: RecentPage?, needs: NeedsList?): NowState {
        val titles = page?.rows.orEmpty().associate { it.id to it.title }
        val left = needs?.items.orEmpty().toMutableList()
        val waiting = mutableListOf<Waiting>()
        val running = mutableListOf<Running>()
        for (c in cards) {
            val title = c.sessionId?.let(titles::get).orEmpty()
            val ask = Asks.read(c.asking)
            if (ask != null) {
                val mine = left.filter { n -> n.ref.str("consoleId") == c.id || (c.sessionId != null && n.ref.str("sessionId") == c.sessionId) }
                left.removeAll(mine)
                waiting += Waiting(c.id, c.agent, title, ask, mine.firstOrNull { it.kind == PERMISSION }?.key)
            } else if (c.status == Live.WORKING) {
                running += Running(c.id, c.agent, title, null, null, c.cost, c.sessionId)
            }
        }
        for (n in left) waiting += Waiting(n.ref.str("sessionId") ?: n.key, n.agentName, n.title, askOf(n), n.key)
        return NowState(
            farm = farm, link = link, updatedAt = updatedAt,
            waiting = waiting, running = running,
            recent = page?.rows.orEmpty().map(::recentOf),
            moreRecent = page?.next != null, cursor = page?.next,
            cards = cards.mapNotNull { c -> c.sessionId?.let { c.id to it } }.toMap(),
        )
    }

    /** The next history page, after what is on screen; a row already there is not drawn twice. */
    fun more(s: NowState, page: RecentPage): NowState {
        val have = s.recent.map { it.session }.toSet()
        return s.copy(recent = s.recent + page.rows.filter { it.id !in have }.map(::recentOf), cursor = page.next, moreRecent = page.next != null)
    }

    /**
     * The first page read again (a refresh, the link back): it takes the top, a row it carries moves to its new
     * place, and the rows already read below it stay with their cursor, so nothing scrolled to is read again.
     */
    fun head(s: NowState, page: RecentPage): NowState {
        val top = page.rows.map(::recentOf)
        val oldest = top.lastOrNull()?.endedAt ?: return s.copy(recent = emptyList(), cursor = null, moreRecent = false)
        val ids = top.map { it.session }.toSet()
        val below = s.recent.filter { it.session !in ids && it.endedAt < oldest }
        val cursor = if (below.isEmpty()) page.next else s.cursor
        return s.copy(recent = top + below, cursor = cursor, moreRecent = cursor != null)
    }

    /**
     * A fresh answer laid over what the live channel already told: console.sessions does not carry the step
     * in flight or when the turn began, so a card still running keeps the ones its beats gave.
     */
    fun carry(old: NowState, fresh: NowState): NowState {
        val told = old.running.associateBy { it.session }
        return fresh.copy(
            running = fresh.running.map { r ->
                told[r.session]?.let { o -> r.copy(step = r.step ?: o.step, turnSince = r.turnSince ?: o.turnSince) } ?: r
            },
        )
    }

    /** A card's turn ended on this beat: the history's head is read again for its last line. */
    fun turnEnded(s: NowState, session: String, working: Boolean): Boolean = !working && s.running.any { it.session == session }

    private fun recentOf(r: RecentRow) = Recent(r.id, r.agent, r.title, r.lastLine.orEmpty(), r.lastTs, failed = r.status == "error")

    /** A need as the row draws it: a permission, a Console question no clock answers, anything else to approve. */
    private fun askOf(n: NeedRow): Ask = Ask(
        kind = when (n.kind) {
            PERMISSION -> AskKind.PERMISSION
            CONSOLE_QUESTION -> AskKind.QUESTION
            else -> AskKind.APPROVAL
        },
        detail = n.detail,
        since = n.ts,
    )

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
