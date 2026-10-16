package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.contract.ReadPage
import com.muvusoft.agentfarm.core.contract.ReadStep
import com.muvusoft.agentfarm.core.live.LiveMerge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * What a Conversation is about: the Console card (its live stream, what the phone may write into) and the
 * session's transcript (what sessions.read pages). A card the engine has not named yet has no transcript; a
 * session from the history that no card hosts has no card and is read only.
 */
@Serializable
data class ConversationKey(val card: String?, val transcript: String?, val agent: String, val title: String) {
    val id: String get() = card ?: transcript.orEmpty()
}

/**
 * The Conversation out of the farm's own sessions.read pages: the last page first, older pages above it as the
 * list reaches its top, one step whole when it is asked for. No read asks for more than [PAGE] steps.
 */
object ConversationBuild {
    const val PAGE = 30

    /** Every role: the screen folds the tool steps into one row (Blocks), the live stream tells them all. */
    private const val ROLES = "all"

    fun step(r: ReadStep) = Message(r.i, r.turn ?: 0, Role.of(r.role), r.ts, r.text, r.tool, r.input, r.isError == true, r.clipped, r.key)

    /**
     * The last page laid over what is drawn. The rows above it stay when the page reaches them with the same
     * steps in the same places; a page past a gap (more was said than a page while the screen was away) starts
     * the list again from itself, and the older rows come back on scroll. A live row the page carries goes.
     */
    fun tail(s: ConversationState, p: ReadPage): ConversationState {
        val rows = p.messages.map(::step)
        val confirmed = s.messages.filterNot { it.provisional }
        val first = rows.firstOrNull()
        val at = confirmed.associateBy { it.i }
        val joined = first != null && confirmed.isNotEmpty() && first.i <= confirmed.last().i + 1 &&
            rows.all { r -> at[r.i]?.let { it.key == r.key } ?: true }
        val above = if (joined) confirmed.filter { it.i < first!!.i } else emptyList()
        val merged = LiveMerge.confirm(s.copy(messages = above + s.messages.filter { it.provisional }), rows)
        return merged.copy(
            title = p.title.ifEmpty { s.title },
            agent = p.agent?.ifEmpty { null } ?: s.agent,
            older = (merged.messages.firstOrNull { !it.provisional }?.i ?: 1) > 1,
            loading = false,
            error = null,
        )
    }

    /** An older page, above the first row drawn; a row already there is not drawn twice. */
    fun older(s: ConversationState, p: ReadPage): ConversationState {
        val top = s.messages.firstOrNull { !it.provisional }?.i ?: return s
        val rows = p.messages.map(::step).filter { it.i < top }
        return s.copy(messages = rows + s.messages, older = (rows.firstOrNull()?.i ?: top) > 1)
    }

    /** A step read whole (maxChars 0) takes the place of its cut copy. */
    fun whole(s: ConversationState, p: ReadPage): ConversationState {
        val w = p.messages.singleOrNull()?.let(::step) ?: return s
        return s.copy(messages = s.messages.map { if (!it.provisional && it.i == w.i && it.key == w.key) w else it })
    }

    /**
     * A message this phone sent is drawn as its own bubble until the farm shows it back: then the record has one
     * more user step with its words than when it was sent (counted, so the phone's clock is never compared
     * with the PC's).
     */
    fun echoed(s: ConversationState): ConversationState {
        if (s.outgoing.isEmpty()) return s
        val said = s.messages.filter { it.role == Role.USER }.groupingBy { it.text?.trim().orEmpty() }.eachCount().toMutableMap()
        val left = s.outgoing.filter { o ->
            val n = said[o.text.trim()] ?: 0
            if (n > o.seen) {
                said[o.text.trim()] = n - 1
                false
            } else {
                true
            }
        }
        return if (left.size == s.outgoing.size) s else s.copy(outgoing = left)
    }

    /** How many user steps already say these words: an echo is one more than this. */
    fun seen(s: ConversationState, text: String): Int = s.messages.count { it.role == Role.USER && it.text?.trim() == text.trim() }

    fun tailArgs(transcript: String): JsonObject = args(transcript, -PAGE, PAGE)

    /** The page above the first row drawn; null at the conversation's start. */
    fun olderArgs(s: ConversationState, transcript: String): JsonObject? {
        val top = s.messages.firstOrNull { !it.provisional }?.i ?: return null
        if (top <= 1) return null
        val from = (top - PAGE).coerceAtLeast(1)
        return args(transcript, from, top - from)
    }

    /** What was written after the last row a read gave (a turn ended, the link came back). */
    fun newArgs(s: ConversationState, transcript: String): JsonObject = args(transcript, LiveMerge.readFrom(s), PAGE)

    fun wholeArgs(transcript: String, m: Message): JsonObject = buildJsonObject {
        put("session", JsonPrimitive(transcript))
        put("roles", JsonPrimitive(ROLES))
        put("from", JsonPrimitive(m.i))
        put("limit", JsonPrimitive(1))
        put("maxChars", JsonPrimitive(0))
    }

    private fun args(transcript: String, from: Int, limit: Int): JsonObject = buildJsonObject {
        put("session", JsonPrimitive(transcript))
        put("roles", JsonPrimitive(ROLES))
        put("from", JsonPrimitive(from))
        put("limit", JsonPrimitive(limit))
    }
}
