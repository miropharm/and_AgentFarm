package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.ReadStep
import com.muvusoft.agentfarm.core.state.Link
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/**
 * The phone's copy of one Conversation: the last steps sessions.read gave, as the farm gave them, so the copy is
 * drawn by the same ConversationBuild as a fresh page. Live rows not yet read are never kept.
 */
@Serializable
data class ConversationCopy(val updatedAt: Long, val key: ConversationKey, val engine: String, val steps: List<ReadStep>) {
    fun state(link: Link): ConversationState = ConversationState(
        session = key.id, agent = key.agent, title = key.title, engineLabel = engine, link = link, updatedAt = updatedAt,
        messages = steps.map(ConversationBuild::step), older = (steps.firstOrNull()?.i ?: 1) > 1,
    )

    fun encode(): String = Codec.json.encodeToString(serializer(), this)

    companion object {
        /** The steps a copy keeps: the last answer and what led to it, enough to open on. */
        const val KEEP = 100

        fun of(updatedAt: Long, key: ConversationKey, s: ConversationState): ConversationCopy = ConversationCopy(
            updatedAt, key.copy(agent = s.agent, title = s.title), s.engineLabel,
            s.messages.filter { !it.provisional && it.key != null }.takeLast(KEEP).map { m ->
                ReadStep(m.i, m.key!!, m.turn, m.role.wire, m.ts, m.text, m.tool, m.input, m.isError.takeIf { it }, m.clipped)
            },
        )

        /** A saved copy, or null when there is none or it does not read (an older build's file is dropped). */
        fun decode(text: String?): ConversationCopy? {
            if (text.isNullOrBlank()) return null
            return try {
                Codec.json.decodeFromString(serializer(), text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
