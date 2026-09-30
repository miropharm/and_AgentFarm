package com.muvusoft.agentfarm.core.speech

import com.muvusoft.agentfarm.core.notify.Alert
import com.muvusoft.agentfarm.core.notify.Channel

/** What the phone reads aloud: exactly one of three, stored by `key`. */
enum class SpeakMode(val key: String) {
    OFF("off"),
    ASKS("asks"),
    ASKS_AND_TURNS("asksAndTurns");

    companion object {
        /** An unknown stored value reads nothing aloud rather than guessing louder. */
        fun of(key: String?): SpeakMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** One thing to say. `interrupt`: a question cuts off what is being read; a turn summary waits its turn. */
data class Utterance(val id: String, val text: String, val interrupt: Boolean)

object Speech {
    /** Past this the sentence is cut at a word and the rest is left to the screen. */
    const val MAX_CHARS = 320

    /**
     * What an alert says aloud under `mode`, or null when it stays silent. `summary` (a finished turn's
     * own summary) is read instead of the notification's one-line text when there is one.
     */
    fun of(alert: Alert, mode: SpeakMode, summary: String? = null): Utterance? {
        val speaks = when (alert.channel) {
            Channel.ASK -> mode != SpeakMode.OFF
            Channel.TURN -> mode == SpeakMode.ASKS_AND_TURNS
            Channel.NOTICE, Channel.LINK -> false
        }
        if (!speaks) return null
        val body = spoken(summary?.takeIf { it.isNotBlank() } ?: alert.text)
        val head = spoken(alert.title.replace(" · ", ", "))
        val text = if (body.isEmpty()) head else "$head. $body"
        return Utterance(alert.tag, cut(text), interrupt = alert.channel == Channel.ASK)
    }

    /** Text as a voice should read it: no markup, a link is "bağlantı", runs of space are one. */
    fun spoken(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " kod bloğu ")
        .replace(Regex("https?://\\S+"), "bağlantı")
        .replace(Regex("[`*_#>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex(" ([,.;:!?])"), "$1")
        .trim()

    fun cut(text: String, max: Int = MAX_CHARS): String {
        if (text.length <= max) return text
        val at = text.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
        return text.substring(0, at).trimEnd(',', '.', ';', ':', ' ') + "…"
    }
}

/**
 * The alerts already read aloud, newest kept: a replayed event (the socket resumes from its last
 * number, a reconnect repeats a pending question) is not read twice.
 */
class SpokenLog(private val cap: Int = 200) {
    private val seen = LinkedHashSet<String>()

    /** True the first time `id` is seen. */
    fun first(id: String): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > cap) seen.remove(seen.first())
        return true
    }
}
