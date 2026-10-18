package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.NowSummary
import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.ShellState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One press of the headset button (S-10): the farm says its Now itself (now.summary, in the voice's language), and
 * its first Console question is then answered by voice. The phone's own words say only why there is nothing to
 * say: no farm answering, or a farm that cannot say it (an Agent Farm older than the op).
 */
object StatusCall {
    const val OP = "now.summary"

    fun args(lang: String): JsonObject = buildJsonObject { put("lang", JsonPrimitive(lang)) }

    /** The farm a press asks: the one in focus, else the first connected, else the first paired. */
    fun farmOf(s: ShellState): String? =
        s.focused?.takeIf { s.farm(it) != null }
            ?: s.farms.firstOrNull { it.link is Link.Online }?.farm?.id
            ?: s.farms.firstOrNull()?.farm?.id

    sealed interface Said

    /** The farm's sentence, and the questions it may be followed by. */
    data class Farm(val summary: NowSummary) : Said

    /** The phone's own sentence. */
    data class Own(val text: String) : Said

    /** What a press says for the farm's answer [r] (null when none came) in the voice's language [lang]. */
    fun of(r: CallResult?, lang: String): Said {
        if (r == null) return Own(Words.tIn(lang, "Agent Farm does not answer right now."))
        val summary = if (r.ok) Ops.read(NowSummary.serializer(), r.result) else null
        return summary?.takeIf { it.spoken.text.isNotBlank() }?.let(::Farm)
            ?: Own(Words.tIn(lang, "Agent Farm could not say how it is: it may need an update on the computer."))
    }
}
