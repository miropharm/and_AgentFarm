package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.session.AnswerCall
import com.muvusoft.agentfarm.core.share.Share
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Two presses of the headset button (S-11): a new session by voice. The agent is asked for and named by the farm's
 * own list (console.new called bare; matched like an option, VoiceAnswer.option), then what it should do; both are
 * read back and, unless called off, the session opens through console.new - the door a share and the new-session
 * sheet use, with the agent's last launch setup. A name the farm does not know goes as said: the farm's refusal is
 * read in its own words.
 */
object VoiceNew {
    /** How long the phone listens for what the agent should do: a sentence, not one word. */
    const val PROMPT_MS = 15_000L

    fun whichAgent(lang: String): String = Words.tIn(lang, "Which agent?")

    fun whatToDo(lang: String, agent: String): String = Words.tIn(lang, "What should {0} do?", agent)

    fun readBack(lang: String, agent: String, words: String): String = Words.tIn(lang, "{0}: {1}. Opening.", agent, words)

    /** The agent the words name: one of the farm's own, else the words themselves; blank words name none. */
    fun agent(said: String, agents: List<String>): String? {
        val words = said.trim().takeIf { it.isNotEmpty() } ?: return null
        return VoiceAnswer.option(words, agents) ?: words
    }

    fun call(agent: String, words: String): AnswerCall = Share.call(Share.newTarget(agent), words, null, null).let { (op, args) -> AnswerCall(op, args) }

    fun isOpen(lang: String, agent: String): String = Words.tIn(lang, "{0} is open.", agent)

    /** What the phone says once the farm answered [r] (null: no answer in time, the call waits in the outbox). */
    fun said(lang: String, agent: String, r: CallResult?): String = when {
        r == null -> VoiceAnswer.waits(lang)
        r.ok -> isOpen(lang, ((r.result as? JsonObject)?.get("agent") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: agent)
        else -> r.error?.takeIf { it.isNotBlank() } ?: Words.tIn(lang, "Agent Farm did not open it.")
    }
}
