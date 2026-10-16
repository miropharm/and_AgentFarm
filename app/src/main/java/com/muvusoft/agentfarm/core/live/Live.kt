package com.muvusoft.agentfarm.core.live

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.Role
import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.Asks
import com.muvusoft.agentfarm.core.session.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** How much a watch is told: the whole stream (Conversation) or the card's state (Now). */
enum class LiveDepth(val wire: String) { FULL("full"), SUMMARY("summary") }

/** One beat of a watched Console card (the contract's `live.data`), in the phone's words. */
data class LiveCard(
    val agent: String,
    /** The Console's own status: starting, idle, working, ended, error. */
    val status: String,
    /** When the running turn started; null when no turn runs. */
    val turnSince: Long?,
    val cost: Double?,
    val steerable: Boolean,
    /** The call in flight; null between calls. */
    val step: Step?,
    val asking: Ask?,
    /** Full depth only (null at summary): the rows new or grown since the last beat, provisional, `i` unset. */
    val messages: List<Message>?,
) {
    val working: Boolean get() = status == Live.WORKING
}

/** What a watch hears. */
sealed interface LiveEvent {
    data class Beat(val session: String, val data: LiveCard) : LiveEvent

    /** The link came back and the watch was asked again: what was missed is read with sessions.read. */
    data object Resumed : LiveEvent

    /** The farm ended the watch: "ended" (the session went), "unavailable" (it cannot be watched here). */
    data class Ended(val reason: String?) : LiveEvent
}

object Live {
    /** A Console card running a turn (vsc ConsoleStatus). */
    const val WORKING = "working"

    /** The session id that watches every Console card (summary depth only). */
    const val EVERY_SESSION = "*"

    /** A live frame's data; null when it does not read (the beat is dropped, the next one comes). */
    fun read(data: JsonObject): LiveCard? {
        val w = try {
            Codec.json.decodeFromJsonElement(WireLive.serializer(), data)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return LiveCard(
            agent = w.agent,
            status = w.status,
            turnSince = w.turnSince,
            cost = w.cost,
            steerable = w.steerable,
            step = w.step?.let { Step(it.tool, it.detail, it.since) },
            asking = Asks.read(w.asking),
            messages = w.messages?.map {
                Message(0, it.turn, Role.of(it.role), it.ts, it.text, it.tool, it.input, it.isError, it.clipped, it.key, provisional = true)
            },
        )
    }
}

@Serializable
private data class WireStep(val tool: String, val detail: String? = null, val since: Long)

@Serializable
private data class WireRow(
    val key: String,
    val turn: Int = 0,
    val role: String,
    val ts: Long,
    val text: String? = null,
    val tool: String? = null,
    val input: String? = null,
    val isError: Boolean = false,
    val clipped: Int? = null,
)

@Serializable
private data class WireLive(
    val agent: String,
    val status: String,
    val turnSince: Long? = null,
    val cost: Double? = null,
    val steerable: Boolean,
    val step: WireStep? = null,
    val asking: JsonElement? = null,
    val messages: List<WireRow>? = null,
)
