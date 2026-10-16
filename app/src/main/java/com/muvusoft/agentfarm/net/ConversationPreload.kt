package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.contract.ReadPage
import com.muvusoft.agentfarm.core.conversation.ConversationBuild
import com.muvusoft.agentfarm.core.conversation.ConversationCopy
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.state.Link

/**
 * The running sessions Now shows, read ahead: each one's last page goes into its Conversation's copy in the
 * background, once while Now is in view, so a tap opens on it at once and then only reads what came after.
 * A read nobody waits for goes outside the outbox, like every read.
 */
class ConversationPreload(
    private val manager: ConnectionManager,
    private val store: ConversationStore,
    private val farmId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val done = mutableSetOf<String>()

    suspend fun ahead(keys: List<ConversationKey>) {
        for (key in keys) {
            val transcript = key.transcript ?: continue
            if (!done.add(key.id)) continue
            val page = manager.read(farmId, OP_READ, ConversationBuild.tailArgs(transcript))
                ?.takeIf { it.ok }?.let { Ops.read(ReadPage.serializer(), it.result) } ?: continue
            // A copy keeps no link: the Conversation that opens on it draws the link it has then.
            val empty = ConversationState(key.id, key.agent, key.title, "", Link.Offline("preload", 0), 0)
            val s = ConversationBuild.tail(empty, page).copy(engineLabel = page.engine)
            store.save(farmId, key.id, ConversationCopy.of(clock(), key, s))
        }
    }

    private companion object {
        const val OP_READ = "sessions.read"
    }
}
