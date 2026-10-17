package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.Time
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
            total == 0 -> "No paired farm"
            total == 1 && away == 1 -> "VS Code closed · ${s.farms[0].farm.name}"
            total == 1 && resting == 1 -> "Resting · ${s.farms[0].farm.name}"
            online == total -> if (total == 1) "Connected · ${s.farms[0].farm.name}" else "$total farms connected"
            resting > 0 && online + resting == total -> if (online == 0) "$total farms resting" else "$online connected · $resting resting"
            online == 0 -> if (refused > 0) "Link refused" else "Not connected · retrying"
            else -> "$online/$total farms connected"
        } + if (total > 1 && away > 0) " · $away with VS Code closed" else ""
        val parts = mutableListOf<String>()
        parts += if (needs > 0) "$needs waiting" else "nothing waiting"
        // Only a connected farm's count is current; an offline one's is last night's news.
        val known = s.farms.filter { it.link is Link.Online }.mapNotNull { it.running }
        if (known.isNotEmpty()) parts += if (known.sum() > 0) "${known.sum()} running" else "nothing running"
        s.farms.mapNotNull { it.lastTurnAt }.maxOrNull()?.let { parts += "last turn ${Time.ago(now - it)} ago" }
        if (s.outbox.isNotEmpty()) parts += "${s.outbox.size} unsent"
        return Text(title, parts.joinToString(" · "))
    }
}
