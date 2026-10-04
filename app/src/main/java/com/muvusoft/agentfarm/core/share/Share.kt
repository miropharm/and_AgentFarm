package com.muvusoft.agentfarm.core.share

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A Console session a shared text can be said into (one row of `console.sessions`). */
data class ShareTarget(val id: String, val agent: String, val status: String, val asking: Boolean)

object Share {
    /** Past this the preview stops; the whole text is in its sheet and is what gets sent. */
    const val PREVIEW_CHARS = 280

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

    fun preview(text: String): String = if (text.length <= PREVIEW_CHARS) text else text.take(PREVIEW_CHARS).trimEnd() + "…"

    private val GONE = setOf("ended", "closed", "exited", "stopped")
    private val SENTENCE_KEYS = listOf("sentence", "note", "message")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
