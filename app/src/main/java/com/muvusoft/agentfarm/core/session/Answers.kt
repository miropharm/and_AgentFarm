package com.muvusoft.agentfarm.core.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A call that settles what waits: the op and its args. */
data class AnswerCall(val op: String, val args: JsonObject)

/** The one place the phone builds an answer; a notification button and a Now row answer through it. */
object Answers {
    const val ALLOW = "permission-allow"
    const val DENY = "permission-deny"

    /** A Console question answered with one of its options (vsc console.answer `answers`). */
    fun question(card: String, question: String, label: String) = AnswerCall(
        "console.answer",
        buildJsonObject {
            put("id", JsonPrimitive(card))
            put("answers", JsonObject(mapOf(question to JsonPrimitive(label))))
        },
    )

    /** A Needs You row's button (vsc needs.act); the key is the farm's, never built here. */
    fun need(key: String, action: String) = AnswerCall(
        "needs.act",
        buildJsonObject {
            put("key", JsonPrimitive(key))
            put("action", JsonPrimitive(action))
        },
    )
}
