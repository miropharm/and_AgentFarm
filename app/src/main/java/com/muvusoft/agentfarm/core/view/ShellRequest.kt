package com.muvusoft.agentfarm.core.view

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * What a farm page asks for that only the phone can answer (X-443). Inside VS Code these go to
 * the PC: `afnav` opens another tab, `afClipboard` writes the PC's clipboard. On the phone the
 * shell answers them itself and they never reach the farm; everything else a page posts does.
 */
sealed interface ShellRequest {
    /** Open another of the farm's pages in this shell. */
    data class Open(val page: String) : ShellRequest

    /** Put text on the phone's clipboard; the page waits for `afClipboardDone` with its token. */
    data class Copy(val token: JsonElement, val text: String) : ShellRequest

    /** Dictate into the page's focused text box; the page waits for `afVoiceDone` with its token. */
    data class Voice(val token: JsonElement) : ShellRequest

    /** A shell request the shell refuses (a malformed page id): it goes nowhere. */
    data object Refused : ShellRequest

    companion object {
        /** The shell's request in a page message, or null when the message is the farm's. */
        fun of(message: JsonElement): ShellRequest? {
            val o = message as? JsonObject ?: return null
            return when (o.str("type")) {
                "afnav" -> o.str("to")?.takeIf(PageRoute::isPage)?.let(::Open) ?: Refused
                "afClipboard" -> Copy(o["token"] ?: JsonNull, o.str("text").orEmpty())
                "afVoice" -> Voice(o["token"] ?: JsonNull)
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
    }
}
