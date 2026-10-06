package com.muvusoft.agentfarm.core.view

import com.muvusoft.agentfarm.core.speech.PageReading
import com.muvusoft.agentfarm.core.speech.ReadingAsk
import com.muvusoft.agentfarm.core.speech.ReadingControl
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * What a farm page asks for that only the phone can answer (X-443). Inside VS Code these go to
 * the PC: `afnav` opens another tab, `afClipboard` writes the PC's clipboard. On the phone the
 * shell answers them itself and they never reach the farm; everything else a page posts does.
 */
sealed interface ShellRequest {
    /**
     * Open another of the farm's pages in this shell. [args] go to the farm with the page's
     * `view.open` as they came (the `session` page is told which transcript to show); the farm
     * checks them, the shell only carries them. Also an entry of the shell's page stack.
     */
    data class Open(val page: String, val args: JsonObject? = null) : ShellRequest

    /** Put text on the phone's clipboard; the page waits for `afClipboardDone` with its token. */
    data class Copy(val token: JsonElement, val text: String) : ShellRequest

    /** Dictate into the page's focused text box; the page waits for `afVoiceDone` with its token. */
    data class Voice(val token: JsonElement) : ShellRequest

    /**
     * Read the page's text aloud with the phone's own voice (S-5): paragraphs the farm prepared, cutting
     * whatever this phone reads; the page hears `afSpeakState` with the ask's token after every change.
     */
    data class Speak(val ask: ReadingAsk) : ShellRequest

    /** Steer the page's reading: stop, pause, resume, toggle, next, prev, or a new rate. */
    data class SpeakControl(val control: ReadingControl) : ShellRequest

    /** A shell request the shell refuses (a malformed page id): it goes nowhere. */
    data object Refused : ShellRequest

    companion object {
        /** The shell's request in a page message, or null when the message is the farm's. */
        fun of(message: JsonElement): ShellRequest? {
            val o = message as? JsonObject ?: return null
            return when (o.str("type")) {
                "afnav" -> o.str("to")?.takeIf(PageRoute::isPage)?.let { Open(it, o["args"] as? JsonObject) } ?: Refused
                "afClipboard" -> Copy(o["token"] ?: JsonNull, o.str("text").orEmpty())
                "afVoice" -> Voice(o["token"] ?: JsonNull)
                "afSpeak" -> Speak(
                    ReadingAsk(
                        token = o["token"] ?: JsonNull,
                        paragraphs = (o["paragraphs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull },
                        lang = o.str("lang").orEmpty(),
                        rate = o.num("rate") ?: 0,
                    ),
                )
                "afSpeakControl" -> o.str("action")?.takeIf { it in PageReading.ACTIONS }?.let { SpeakControl(ReadingControl(it, o.num("rate"))) } ?: Refused
                else -> null
            }
        }

        /** The answer a page's copy waits for, the same shape the desktop host posts. */
        fun copyDone(token: JsonElement, ok: Boolean, error: String? = null): JsonObject = buildJsonObject {
            put("type", "afClipboardDone")
            put("token", token)
            put("ok", ok)
            if (error != null) put("error", error)
        }

        /** The recogniser's answer: its first non-blank result; nothing (a cancel) carries no text. */
        fun voiceDone(token: JsonElement, results: List<String>?, error: String? = null): JsonObject = buildJsonObject {
            put("type", "afVoiceDone")
            put("token", token)
            heard(results)?.let { put("text", it) }
            if (error != null) put("error", error)
        }

        /** What the recogniser heard: its first non-blank result, or null (a cancel). */
        fun heard(results: List<String>?): String? = results?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.num(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }?.roundToInt()
    }
}
