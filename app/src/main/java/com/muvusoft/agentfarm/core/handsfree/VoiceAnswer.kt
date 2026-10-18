package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.session.AnswerCall
import com.muvusoft.agentfarm.core.session.Answers
import java.text.Normalizer
import java.util.Locale

/** A question answered by voice: what the words become, read back first, and gone unless they are called off. */
data class Heard(val call: AnswerCall, val words: String)

/**
 * The headset mode's answer to a question read aloud: the option the words name, else the words themselves as the
 * answer. Words are compared folded (lower case, no accents, no punctuation), so a recognizer's spelling of
 * "Yeni dal" or "yeni dal." names the same option.
 */
object VoiceAnswer {
    /** How long the phone listens after the tone. */
    const val LISTEN_MS = 8_000L

    /** How long an answer read back waits to be called off before it goes. */
    const val CANCEL_MS = 3_000L

    // Folded words that call an answer off, in the voice languages the app has (Turkish "vazgec", "iptal").
    private val CANCEL = listOf("vazgec", "iptal", "cancel", "never mind", "stop")

    // Folded words that name an option by its place: "the second", "ikinci", "2".
    private val PLACES = listOf(
        setOf("1", "first", "birinci", "ilk", "ilki", "birincisi"),
        setOf("2", "second", "ikinci", "ikincisi"),
        setOf("3", "third", "ucuncu", "ucuncusu"),
        setOf("4", "fourth", "dorduncu", "dorduncusu"),
    )

    fun fold(text: String): String =
        Normalizer.normalize(text.replace('ı', 'i').lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("[\\p{Mn}]+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Whether the words call the answer off: a short utterance carrying one of the cancel words. */
    fun cancels(said: String): Boolean {
        val w = fold(said)
        return w.isNotEmpty() && w.split(' ').size <= 3 && CANCEL.any { " $w ".contains(" $it ") }
    }

    /**
     * The option the words name: one said as it is written, else the only one the words carry whole or begin
     * ("yes" for "Yes, proceed"), else one named by its place. Two that fit equally name none.
     */
    fun option(said: String, options: List<String>): String? {
        val w = fold(said)
        if (w.isEmpty()) return null
        val folded = options.map(::fold)
        folded.indexOf(w).takeIf { it >= 0 }?.let { return options[it] }
        val carried = folded.indices.filter { folded[it].isNotEmpty() && " $w ".contains(" ${folded[it]} ") }
        if (carried.size == 1) return options[carried.single()]
        val begun = folded.indices.filter { folded[it].startsWith("$w ") }
        if (carried.isEmpty() && begun.size == 1) return options[begun.single()]
        val words = w.split(' ')
        if (words.size > 3) return null
        val place = PLACES.indexOfFirst { p -> words.any { it in p } }
        return if (place >= 0) options.getOrNull(place) else null
    }

    /** What the words of an answer to [question] on [card] become; blank words become nothing. */
    fun heard(card: String, question: String, options: List<String>, said: String): Heard? {
        val words = said.trim().takeIf { it.isNotEmpty() } ?: return null
        val picked = option(words, options)
        return if (picked != null) Heard(Answers.question(card, question, picked), picked) else Heard(Answers.free(card, words), words)
    }

    /** Read back before the answer goes, in the voice's language [lang]. */
    fun readBack(lang: String, words: String): String = Words.tIn(lang, "{0}. Sending.", words)

    fun cancelled(lang: String): String = Words.tIn(lang, "Called off.")

    /** The answer went into the outbox while no farm answers. */
    fun waits(lang: String): String = Words.tIn(lang, "No link: it waits on this phone.")
}
