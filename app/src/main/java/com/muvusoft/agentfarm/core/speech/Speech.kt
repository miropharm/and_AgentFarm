package com.muvusoft.agentfarm.core.speech

import com.muvusoft.agentfarm.core.contract.AskAnswersSoon
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.GateOpened
import com.muvusoft.agentfarm.core.contract.MissionEnded
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.Spoken
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.notify.Alert
import com.muvusoft.agentfarm.core.notify.Channel
import java.util.Locale

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

/**
 * One thing to say. `interrupt`: a question cuts off what is being read; a turn summary waits its turn.
 * `lang`: the text's own language (BCP 47) when the farm named it; "" = the user's voice language.
 */
data class Utterance(val id: String, val text: String, val interrupt: Boolean, val lang: String = "")

object Speech {
    /** Past this the sentence is cut at a word and the rest is left to the screen. */
    const val MAX_CHARS = 320

    /**
     * What an alert says aloud under `mode`, or null when it stays silent. The farm's own `spoken` text is
     * read as the farm wrote it, in its language, so the phone and the desk say the same words; without it
     * (an older farm) `summary` (a finished turn's own summary) or the notification's text is read.
     */
    fun of(alert: Alert, mode: SpeakMode, summary: String? = null, spoken: Spoken? = null): Utterance? {
        val speaks = !alert.silent && when (alert.channel) {
            Channel.ASK -> mode != SpeakMode.OFF
            Channel.TURN -> mode == SpeakMode.ASKS_AND_TURNS
            Channel.IMPORTANT, Channel.NOTICE, Channel.ROUTINE, Channel.LINK -> false
        }
        if (!speaks) return null
        val interrupt = alert.channel == Channel.ASK
        spoken?.takeIf { it.text.isNotBlank() }?.let { return Utterance(alert.tag, it.text.trim(), interrupt, it.lang.trim()) }
        val body = spoken(summary?.takeIf { it.isNotBlank() } ?: alert.text)
        val head = spoken(alert.title.replace(" · ", ", "))
        val text = if (body.isEmpty()) head else "$head. $body"
        return Utterance(alert.tag, cut(text), interrupt)
    }

    /** The farm's words for an event, when it sent them. */
    fun spokenOf(e: FarmEventData): Spoken? = when (e) {
        is AskOpened -> e.spoken
        is AskAnswersSoon -> e.spoken
        is PermissionOpened -> e.spoken
        is TurnFinished -> e.spoken
        is NoticePosted -> e.spoken
        is GateOpened -> e.spoken
        is MissionEnded -> e.spoken
        else -> null
    }

    /**
     * The voices to try for a text, best first: its own language when one is named, then the user's voice
     * language. The first one this phone has a voice for is used; none = silence, never a mangling voice.
     */
    fun voices(textLang: String, user: Locale): List<Locale> {
        val own = textLang.trim().takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() }
        return listOfNotNull(own, user).distinct()
    }

    /** Text as a voice should read it: no markup, a link is "link", runs of space are one. */
    fun spoken(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " code block ")
        .replace(Regex("https?://\\S+"), "link")
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
