package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.ConsoleRow
import com.muvusoft.agentfarm.core.contract.NeedsList
import com.muvusoft.agentfarm.core.contract.RecentPage
import com.muvusoft.agentfarm.core.state.Link
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * The phone's copy of a farm's Now: the farm's last answers themselves (the cards, the first history page,
 * the needs), so the copy is drawn by the same NowBuild as a fresh answer and never says something else.
 */
@Serializable
data class NowCopy(
    val updatedAt: Long,
    val cards: List<ConsoleRow>,
    val page: RecentPage? = null,
    val needs: NeedsList? = null,
) {
    fun state(farm: String, link: Link): NowState = NowBuild.of(farm, link, updatedAt, cards, page, needs)

    fun encode(): String = Codec.json.encodeToString(serializer(), this)

    companion object {
        /** A saved copy, or null when there is none or it does not read (an older build's file is dropped). */
        fun decode(text: String?): NowCopy? {
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
