package com.muvusoft.agentfarm.core.session

import com.muvusoft.agentfarm.core.Time.ago
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.state.Link
import java.util.Locale

/** The step a running turn is on: its tool, what it runs, and since when. */
data class Step(val tool: String, val detail: String? = null, val since: Long)

/** The words a session row and the conversation print about a turn, once. */
object SessionText {
    /** "Bash: npm test · 12 s" */
    fun step(step: Step, now: Long): String =
        (if (step.detail.isNullOrBlank()) step.tool else "${step.tool}: ${step.detail.lineSequence().first().trim()}") +
            " · " + ago(now - step.since)

    /** "3 min · $0.42": how long the turn has run and what the session has cost; either is left out when unknown. */
    fun turn(turnSince: Long?, cost: Double?, now: Long): String =
        listOfNotNull(turnSince?.let { ago(now - it) }, cost?.let(::usd)).joinToString(" · ")

    fun usd(cost: Double): String = if (cost < 0.01 && cost > 0) "<$0.01" else String.format(Locale.ROOT, "$%.2f", cost)

    /**
     * The copy's age when the screen is not live: null while connected. Every wait says what clears it,
     * and here that is the link coming back by itself.
     */
    fun stale(link: Link, updatedAt: Long, now: Long): String? {
        val state = when (link) {
            is Link.Online -> return null
            is Link.Connecting -> Words.t("Connecting")
            is Link.Resting -> Words.t("Resting")
            is Link.Offline, is Link.Refused -> Words.t("Offline")
        }
        return if (updatedAt <= 0) Words.t("{0} · nothing saved yet", state) else Words.t("{0} · updated {1} ago", state, ago(now - updatedAt))
    }
}
