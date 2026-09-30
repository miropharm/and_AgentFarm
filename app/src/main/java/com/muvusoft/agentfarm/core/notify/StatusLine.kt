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
        val title = when {
            total == 0 -> "Eşli çiftlik yok"
            online == total -> if (total == 1) "Bağlı · ${s.farms[0].farm.name}" else "$total çiftlik bağlı"
            online == 0 -> if (refused > 0) "Bağlantı reddedildi" else "Bağlı değil · yeniden deneniyor"
            else -> "$online/$total çiftlik bağlı"
        }
        val parts = mutableListOf<String>()
        parts += if (needs > 0) "$needs bekleyen" else "bekleyen yok"
        // Only a connected farm's count is current; an offline one's is last night's news.
        val known = s.farms.filter { it.link is Link.Online }.mapNotNull { it.running }
        if (known.isNotEmpty()) parts += if (known.sum() > 0) "${known.sum()} çalışıyor" else "çalışan yok"
        s.farms.mapNotNull { it.lastTurnAt }.maxOrNull()?.let { parts += "son tur ${Time.ago(now - it)} önce" }
        if (s.outbox.isNotEmpty()) parts += "${s.outbox.size} gönderilmemiş"
        return Text(title, parts.joinToString(" · "))
    }
}
