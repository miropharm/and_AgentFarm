package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.Time
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.ShellState

/** The always-on notification's two lines: what is connected, and what waits for the user. */
object StatusLine {
    data class Text(val title: String, val body: String)

    fun of(s: ShellState, now: Long): Text {
        val online = s.farms.count { it.link is Link.Online }
        val total = s.farms.size
        val needs = s.farms.sumOf { it.needs }
        val refused = s.farms.count { it.link is Link.Refused }
        // A resting farm was reached and closed its socket to spare the battery: it wakes the phone, it is not lost.
        val resting = s.farms.count { it.link is Link.Resting }
        // A farm whose VS Code is closed answers through its service: connected, but Agent Farm is not running.
        val away = s.farms.count { it.link is Link.Online && it.away != null }
        val title = when {
            total == 0 -> Words.t("No paired farm")
            total == 1 && away == 1 -> Words.t("VS Code closed · {0}", s.farms[0].farm.name)
            total == 1 && resting == 1 -> Words.t("Resting · {0}", s.farms[0].farm.name)
            online == total -> if (total == 1) Words.t("Connected · {0}", s.farms[0].farm.name) else Words.t("{0} farms connected", total)
            resting > 0 && online + resting == total -> if (online == 0) Words.t("{0} farms resting", total) else Words.t("{0} connected · {1} resting", online, resting)
            online == 0 && resting > 0 -> Words.t("{0} resting · {1} not connected", resting, total - resting)
            online == 0 -> if (refused > 0) Words.t("Link refused") else Words.t("Not connected · retrying")
            else -> Words.t("{0}/{1} farms connected", online, total)
        } + if (total > 1 && away > 0) " · " + Words.t("{0} with VS Code closed", away) else ""
        val parts = mutableListOf<String>()
        parts += if (needs > 0) Words.t("{0} waiting", needs) else Words.t("nothing waiting")
        // Only a connected farm's count is current; an offline one's is last night's news.
        val known = s.farms.filter { it.link is Link.Online }.mapNotNull { it.running }
        if (known.isNotEmpty()) parts += if (known.sum() > 0) Words.t("{0} running", known.sum()) else Words.t("nothing running")
        s.farms.mapNotNull { it.lastTurnAt }.maxOrNull()?.let { parts += Words.t("last turn {0} ago", Time.ago(now - it)) }
        if (s.outbox.isNotEmpty()) parts += Words.t("{0} unsent", s.outbox.size)
        return Text(title, parts.joinToString(" · "))
    }
}
