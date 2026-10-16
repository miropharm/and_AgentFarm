package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link

/** A session whose turn waits for the user. */
data class Waiting(val session: String, val agent: String, val title: String, val ask: Ask)

/** A session whose turn is running now. */
data class Running(
    val session: String,
    val agent: String,
    val title: String,
    /** The step in flight; null between steps. */
    val step: Step?,
    val turnSince: Long,
    val cost: Double?,
)

/** A session that has finished, with the first line of its last answer. */
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
    val recent: List<Recent> = emptyList(),
    /** Another page of recent sessions exists. */
    val moreRecent: Boolean = false,
    /** Nothing saved and the first answer is on its way: skeleton rows. */
    val loading: Boolean = false,
    /** The farm's own sentence for why the fresh answer failed. */
    val error: String? = null,
) {
    val empty: Boolean get() = !loading && error == null && waiting.isEmpty() && running.isEmpty() && recent.isEmpty()
}
