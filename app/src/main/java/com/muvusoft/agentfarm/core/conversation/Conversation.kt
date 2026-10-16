package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link

/** sessions.read `role`: the same five words for every engine. */
enum class Role(val wire: String) {
    USER("user"), ASSISTANT("assistant"), TOOL("tool"), TOOL_RESULT("toolResult"), SYSTEM("system");

    companion object {
        /** A Console snapshot's agent/result are the same steps as assistant/toolResult. */
        fun of(wire: String): Role = when (wire) {
            "user" -> USER
            "assistant", "agent" -> ASSISTANT
            "tool" -> TOOL
            "toolResult", "result" -> TOOL_RESULT
            else -> SYSTEM
        }
    }
}

/** One step of sessions.read: `{ i, turn, role, ts, text?, tool?, input?, isError?, clipped? }`. */
data class Message(
    val i: Int,
    val turn: Int,
    val role: Role,
    val ts: Long,
    val text: String? = null,
    val tool: String? = null,
    val input: String? = null,
    val isError: Boolean = false,
    /** The full length of a text the farm cut; null = it came whole. */
    val clipped: Int? = null,
    /** sessions.read `key`: the step's name across reads and live frames. */
    val key: String? = null,
    /** Told by a live frame and not yet carried by a sessions.read page (core/live/LiveMerge). */
    val provisional: Boolean = false,
)

/** Where a message sent from this phone is, until the farm shows it back as a user message. */
enum class SendState { WAITING, SENDING, DELIVERED, AFTER_TURN }

/** `seen`: the user steps that already said these words when it was sent (ConversationBuild.echoed). */
data class Outgoing(val id: String, val text: String, val state: SendState, val seen: Int = 0)

/** How the bottom of the conversation takes input. */
enum class ComposerMode { WRITE, AFTER_TURN, READ_ONLY }

data class ConversationState(
    val session: String,
    val agent: String,
    val title: String,
    val engineLabel: String,
    val link: Link,
    val updatedAt: Long,
    val messages: List<Message> = emptyList(),
    val outgoing: List<Outgoing> = emptyList(),
    val running: Boolean = false,
    val turnSince: Long? = null,
    val cost: Double? = null,
    val live: Step? = null,
    val ask: Ask? = null,
    /** console.sessions `steerable`: a message sent while the turn runs reaches it now. */
    val steerable: Boolean = true,
    /** Why this phone can read but not write here; null = it can write. */
    val readOnly: String? = null,
    /** Older messages exist above the first one on screen. */
    val older: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** A send the farm's gate stopped, until the owner says yes or writes something else. */
    val gate: SendGate? = null,
) {
    val composer: ComposerMode get() = when {
        readOnly != null -> ComposerMode.READ_ONLY
        running && !steerable -> ComposerMode.AFTER_TURN
        else -> ComposerMode.WRITE
    }
}
