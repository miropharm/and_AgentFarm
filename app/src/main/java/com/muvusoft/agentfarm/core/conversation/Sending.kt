package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.now.NowText
import com.muvusoft.agentfarm.core.share.Share
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** What became of words sent into a Console card, read off the farm's own answer to console.send. */
sealed interface Sent {
    /** The card has them; the bubble stays until the farm shows them back. */
    data object Delivered : Sent

    /** The turn runs on an engine that cannot be steered: they are said when it ends. */
    data object AfterTurn : Sent

    /** No answer yet: the outbox keeps the call and sends it when the farm answers. */
    data object Queued : Sent

    data class Refused(val sentence: String) : Sent

    /** The farm's gate stopped them (a command that cannot be undone): its sentence, and the gate a yes names. */
    data class Gate(val gate: String, val sentence: String) : Sent
}

/** A send the farm's gate stopped, waiting for the owner's yes: the same words go again naming the gate. */
data class SendGate(val text: String, val gate: String, val sentence: String)

object Sending {
    fun of(r: CallResult?): Sent {
        if (r == null) return Sent.Queued
        if (!r.ok) return Sent.Refused(Share.outcome(false, r.result, r.error))
        Share.asks(true, r.result)?.let { return Sent.Gate(it, Share.outcome(true, r.result, null)) }
        val held = ((r.result as? JsonObject)?.get("held") as? JsonPrimitive)?.booleanOrNull == true
        return if (held) Sent.AfterTurn else Sent.Delivered
    }

    /** The bubble's state for a verdict; null = no bubble (refused, or waiting for the yes). */
    fun bubble(v: Sent): SendState? = when (v) {
        Sent.Delivered -> SendState.DELIVERED
        Sent.AfterTurn -> SendState.AFTER_TURN
        Sent.Queued -> SendState.WAITING
        is Sent.Refused, is Sent.Gate -> null
    }
}

/** The Conversation's sentences that are verdicts, not labels. */
object ConversationText {
    const val NOT_IN_CONSOLE = "Not open in the Console: the phone reads it, and writes only to a session the Console hosts."
    const val NO_ANSWER = "The farm did not answer."
    const val PICTURE_ONLINE = "A picture goes only while the farm answers."

    /**
     * Why a page is not on screen: the farm's own sentence, else silence, else a shape this build does not read.
     * Silence while the link is down is no line of its own: the copy's age already says it.
     */
    fun notRead(r: CallResult?, online: Boolean): String? = when {
        r == null -> if (online) NO_ANSWER else null
        else -> r.takeIf { !it.ok }?.error?.takeIf(String::isNotBlank) ?: NowText.NOT_READ
    }

    /** The farm ended the live watch: the card left the Console ("ended"), or it cannot be followed here. */
    fun ended(reason: String?): String =
        if (reason == "ended") "This session closed in the Console; what it said stays here to read." else "This session cannot be followed live from here${reason?.let { " ($it)" }.orEmpty()}."
}
