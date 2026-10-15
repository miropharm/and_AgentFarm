package com.muvusoft.agentfarm.core.share

import com.muvusoft.agentfarm.core.contract.CallResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** A Console session a shared text can be said into (one row of `console.sessions`). */
data class ShareTarget(val id: String, val agent: String, val status: String, val asking: Boolean)

object Share {
    /** Past this the preview stops; the whole text is in its sheet and is what gets sent. */
    const val PREVIEW_CHARS = 280

    /** The farm's own limit for one picture on a message (console.send attach). */
    const val IMAGE_MAX_BYTES = 5L * 1024 * 1024

    private const val NEW = "new:"

    /** A share can open a new session with an agent instead of going into a running one. */
    fun newTarget(agent: String): String = NEW + agent

    fun newAgent(target: String?): String? = target?.takeIf { it.startsWith(NEW) }?.removePrefix(NEW)

    /** The agents a new session can be opened with, from `console.new` called bare. */
    fun agents(result: JsonElement?): List<String> =
        ((result as? JsonObject)?.get("agents") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }

    /**
     * The call a share makes: the words into a session (console.send), or a new session with an agent and the
     * words as its first message (console.new). A share queued offline makes the same call, with no picture.
     */
    fun call(to: String, text: String, attach: String?, gate: String?): Pair<String, JsonObject> {
        val agent = newAgent(to)
        val args = buildJsonObject {
            if (agent != null) {
                put("agent", JsonPrimitive(agent))
                if (text.isNotBlank()) put("prompt", JsonPrimitive(text))
            } else {
                put("id", JsonPrimitive(to))
                put("text", JsonPrimitive(text))
            }
            if (attach != null) put("attach", JsonArray(listOf(JsonPrimitive(attach))))
            if (gate != null) put("confirm", JsonPrimitive(gate))
        }
        return (if (agent != null) "console.new" else "console.send") to args
    }

    /** Where `console.stage` put a picture on the PC, to hang on the message. */
    fun stagedPath(result: JsonElement?): String? = (result as? JsonObject)?.str("path")?.takeIf { it.isNotBlank() }

    /** The words a share carries: its subject leads unless the text already starts with it. Null when empty. */
    fun text(subject: String?, body: String?): String? {
        val s = subject?.trim().orEmpty()
        val b = body?.trim().orEmpty()
        val all = when {
            s.isEmpty() -> b
            b.isEmpty() -> s
            b.startsWith(s) -> b
            else -> "$s\n$b"
        }
        return all.ifEmpty { null }
    }

    /**
     * The sessions a share can go to, from `console.sessions`: an ended one is dropped; one waiting on
     * you comes first, then a running one, then the rest in the host's own order. Anything that is not
     * a row with an id is skipped.
     */
    fun targets(result: JsonElement?): List<ShareTarget> {
        val rows = (result as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        return rows.mapNotNull { r ->
            val id = r.str("id") ?: return@mapNotNull null
            ShareTarget(id, r.str("agent") ?: id, r.str("status").orEmpty(), r["asking"] is JsonObject)
        }.filterNot { it.status in GONE }
            .sortedBy { if (it.asking) 0 else if (it.status == "running") 1 else 2 }
    }

    /** A session's state in the row's words. An unknown status is shown as itself. */
    fun statusWord(t: ShareTarget): String = when {
        t.asking -> "waiting for you"
        t.status == "running" -> "running"
        t.status == "idle" -> "idle"
        else -> t.status
    }

    /** What a finished send says: the host's own sentence when it gave one. */
    fun outcome(ok: Boolean, result: JsonElement?, error: String?): String {
        if (!ok) return "Not sent: ${error ?: "the farm did not answer"}"
        val o = result as? JsonObject
        val said = SENTENCE_KEYS.firstNotNullOfOrNull { o?.str(it) }
        val held = (o?.get("held") as? JsonPrimitive)?.booleanOrNull == true
        return said ?: if (held) "Queued; it is said when the turn ends." else "Sent."
    }

    /**
     * The gate the farm stopped a send at (`sent:false` + `confirm`), answered by sending the same text
     * again with `confirm` set to it. The farm names its gates and checks the answer; the phone only
     * echoes the name back, so a gate added on the farm needs no new app. Null when nothing waits.
     */
    fun asks(ok: Boolean, result: JsonElement?): String? {
        val o = (result as? JsonObject)?.takeIf { ok } ?: return null
        val stopped = (o["sent"] as? JsonPrimitive)?.booleanOrNull == false
        return o.str("confirm")?.takeIf { stopped && it.isNotBlank() }
    }

    /** A farm offered for a share: one that answers, or one that does not but whose sessions are known (the text then waits on the phone). */
    fun farmLabel(name: String, online: Boolean): String = if (online) name else "$name · not connected"

    /**
     * The farm a share goes to: the one picked here, else the focused farm when it answers, else the first
     * that answers, else an offered one that does not (the focused first). Null when nothing is offered.
     */
    fun farmFor(picked: String?, focused: String?, online: List<String>, offered: List<String>): String? =
        picked?.takeIf { it in offered }
            ?: focused?.takeIf { it in online }
            ?: online.firstOrNull()
            ?: focused?.takeIf { it in offered }
            ?: offered.firstOrNull()

    /** What a share queued on the phone says at once. */
    fun queued(to: String, farmName: String, hours: Long): String =
        "Waiting on this phone: it goes to $to when $farmName answers, within $hours h. A notification says how it went."

    /**
     * A queued share's fate, told in a notification: the title says whether it arrived, the text gives the
     * farm's own sentence. `result` null = it waited too long and was dropped.
     */
    fun receipt(to: String, farmName: String, result: CallResult?, hours: Long): Receipt {
        val missed = "Not sent to $to · $farmName"
        return when {
            result == null -> Receipt(false, missed, "$farmName did not answer within $hours h; the text was dropped.")
            !result.ok -> Receipt(false, missed, outcome(false, result.result, result.error))
            asks(true, result.result) != null -> Receipt(false, missed, "${outcome(true, result.result, null)} Share it again to answer.")
            else -> Receipt(true, "Delivered to $to · $farmName", outcome(true, result.result, null))
        }
    }

    data class Receipt(val delivered: Boolean, val title: String, val text: String)

    /** A file's size the way a person reads it: KB under a megabyte, MB with one decimal above. */
    fun size(bytes: Long): String =
        if (bytes < 1024 * 1024) "${(bytes + 1023) / 1024} KB" else String.format("%.1f MB", bytes / 1_048_576.0)

    fun preview(text: String): String = if (text.length <= PREVIEW_CHARS) text else text.take(PREVIEW_CHARS).trimEnd() + "…"

    private val GONE = setOf("ended", "closed", "exited", "stopped")
    private val SENTENCE_KEYS = listOf("sentence", "note", "message")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
