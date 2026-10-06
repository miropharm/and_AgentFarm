package com.muvusoft.agentfarm.core.speech

import java.util.Locale
import kotlin.math.pow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A farm page's text read aloud by this phone (S-5, vsc U-209). The farm prepares the paragraphs exactly as
 * the PC would say them (the text's language, code and links spoken, its inserted words); the phone only
 * plays them. [token] is the page's name for this reading: every state sent back carries it, so a page
 * never takes an older reading's end for its own. [lang] is the text's language, '' = the user's voice
 * language; [rate] is the PC's -10..10 scale.
 */
data class ReadingAsk(val token: JsonElement, val paragraphs: List<String>, val lang: String, val rate: Int)

/** Where a reading is: the paragraph being said (0-based of [count]), paused or not, and its speed. */
data class ReadingAt(val token: JsonElement, val count: Int, val paragraph: Int, val paused: Boolean, val rate: Int)

/** A press on the page's reading controls; [rate] travels only with "rate". */
data class ReadingControl(val action: String, val rate: Int? = null)

/**
 * One piece of a reading as the voice engine knows it: the [round] of playing it belongs to (every stop,
 * skip or pause starts a new one, so the engine's late word about an old round is ignored), the paragraph
 * and the piece of it. Alerts' utterances carry their own ids and never parse as one.
 */
data class ReadingPiece(val round: Int, val paragraph: Int, val piece: Int) {
    val id: String get() = "$PREFIX$round:$paragraph:$piece"

    companion object {
        const val PREFIX = "afread:"

        fun of(id: String?): ReadingPiece? {
            val parts = id?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.split(':') ?: return null
            val n = parts.mapNotNull { it.toIntOrNull() }
            return if (parts.size == 3 && n.size == 3) ReadingPiece(n[0], n[1], n[2]) else null
        }
    }
}

/** What a control does to the voice. */
sealed interface ReadingMove {
    /** The reading ends. */
    data object End : ReadingMove

    /** Nothing to do: already there, or nowhere to go. */
    data object Stay : ReadingMove

    /** The voice stops where it is and the reading waits, paused. */
    data class Halt(val at: ReadingAt) : ReadingMove

    /** The voice (re)starts at [at]'s paragraph. */
    data class Play(val at: ReadingAt) : ReadingMove
}

object PageReading {
    const val MIN_RATE = -10
    const val MAX_RATE = 10

    /** Every control a page may send; anything else is refused before it reaches the voice. */
    val ACTIONS: Set<String> = setOf("stop", "pause", "resume", "toggle", "next", "prev", "rate")

    fun clampRate(rate: Int): Int = rate.coerceIn(MIN_RATE, MAX_RATE)

    /** The PC's -10..10 as the phone's speech rate: 0 is the voice's own pace, every 10 doubles or halves it. */
    fun speechRate(rate: Int): Float = 2.0.pow(clampRate(rate) / 10.0).toFloat()

    /** The start of a reading, at its first paragraph; null when nothing in it can be said. */
    fun start(ask: ReadingAsk): ReadingAt? =
        if (ask.paragraphs.none { it.isNotBlank() }) null
        else ReadingAt(ask.token, ask.paragraphs.size, 0, paused = false, rate = clampRate(ask.rate))

    /**
     * Where a control takes the reading. Next on the last paragraph has nowhere to go; previous on the
     * first starts it again; a new speed restarts the paragraph being said (a voice cannot change pace
     * mid-sentence), and while paused it only waits at the new speed.
     */
    fun move(at: ReadingAt, c: ReadingControl): ReadingMove = when (c.action) {
        "stop" -> ReadingMove.End
        "pause" -> if (at.paused) ReadingMove.Stay else ReadingMove.Halt(at.copy(paused = true))
        "resume" -> if (at.paused) ReadingMove.Play(at.copy(paused = false)) else ReadingMove.Stay
        "toggle" -> if (at.paused) ReadingMove.Play(at.copy(paused = false)) else ReadingMove.Halt(at.copy(paused = true))
        "next" -> if (at.paragraph + 1 < at.count) ReadingMove.Play(at.copy(paragraph = at.paragraph + 1, paused = false)) else ReadingMove.Stay
        "prev" -> ReadingMove.Play(at.copy(paragraph = (at.paragraph - 1).coerceAtLeast(0), paused = false))
        "rate" -> c.rate?.let { r ->
            val faster = at.copy(rate = clampRate(r))
            if (at.paused) ReadingMove.Halt(faster) else ReadingMove.Play(faster)
        } ?: ReadingMove.Stay
        else -> ReadingMove.Stay
    }

    /**
     * A paragraph cut into pieces a voice accepts ([max] characters, the engine's own limit): at the last
     * sentence end inside the limit, else the last space, else hard. Blank pieces are dropped.
     */
    fun chunks(text: String, max: Int): List<String> {
        val limit = max.coerceAtLeast(1)
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > limit) {
            val window = rest.substring(0, limit)
            val sentence = SENTENCE_END.findAll(window).lastOrNull()?.range?.last?.plus(1)
            val cut = sentence ?: window.lastIndexOf(' ').takeIf { it > 0 } ?: limit
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotBlank() }
    }

    /** What the page hears after every change (`afSpeakState`); a null [at] is a reading that is over. */
    fun state(token: JsonElement, at: ReadingAt?, error: String? = null): JsonObject = buildJsonObject {
        put("type", "afSpeakState")
        put("token", token)
        put("state", if (at == null) "idle" else if (at.paused) "paused" else "reading")
        if (at != null) {
            put("paragraph", at.paragraph)
            put("count", at.count)
            put("rate", at.rate)
        }
        if (error != null) put("error", error)
    }

    /** Said on the page when neither the text's language nor the user's own has a voice on this phone. */
    fun noVoice(lang: String): String {
        val name = Locale.forLanguageTag(lang).getDisplayLanguage(Locale.ENGLISH).ifEmpty { "this language" }
        return "This phone has no voice for $name - add one in the phone's text-to-speech settings."
    }

    /** Said when the phone's voice refused or broke off a paragraph. */
    const val VOICE_FAILED = "The phone's voice could not read this."

    private val SENTENCE_END = Regex("[.!?…][\"')\\]]*(?=\\s)")
}
