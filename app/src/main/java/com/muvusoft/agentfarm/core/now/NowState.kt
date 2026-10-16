package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link

/**
 * A session whose turn waits for the user. `session` is the Console card's id when a card asks, else the
 * session the farm's need names; `need` is the Needs You key that settles it with needs.act.
 */
data class Waiting(val session: String, val agent: String, val title: String, val ask: Ask, val need: String? = null)

/** A Console card whose turn is running now (`session` is the card's id, `transcript` its session's). */
data class Running(
    val session: String,
    val agent: String,
    val title: String,
    /** The step in flight; null between steps. */
    val step: Step?,
    /** When the turn started; null until the live channel says it. */
    val turnSince: Long?,
    val cost: Double?,
    val transcript: String? = null,
)

/** A session from the history, with the first line of its last answer. */
data class Recent(
    val session: String,
    val agent: String,
    val title: String,
    val lastLine: String,
    val endedAt: Long,
    val failed: Boolean = false,
)

/** What the Now screen draws: first the phone's copy, then the fresh one, then pages on scroll. */
data class NowState(
    val farm: String,
    val link: Link,
    /** When the rows on screen were last fresh from the farm; 0 = nothing saved yet. */
    val updatedAt: Long,
    val waiting: List<Waiting> = emptyList(),
    val running: List<Running> = emptyList(),
    /** The history as far as it was read, newest first. */
    val recent: List<Recent> = emptyList(),
    /** Another page of recent sessions exists. */
    val moreRecent: Boolean = false,
    /** Nothing saved and the first answer is on its way: skeleton rows. */
    val loading: Boolean = false,
    /** The farm's own sentence for why the fresh answer failed. */
    val error: String? = null,
    /** The next history page's cursor (sessions.recent `next`). */
    val cursor: String? = null,
    /** Console card id to the id of the session it hosts. */
    val cards: Map<String, String> = emptyMap(),
) {
    val empty: Boolean get() = !loading && error == null && waiting.isEmpty() && running.isEmpty() && recent.isEmpty()

    /** The history without the sessions already drawn above it, waiting or running. */
    val finished: List<Recent>
        get() {
            val above = running.map { it.transcript ?: cards[it.session] ?: it.session } + waiting.map { cards[it.session] ?: it.session }
            return if (above.isEmpty()) recent else recent.filterNot { it.session in above }
        }
}
