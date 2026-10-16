package com.muvusoft.agentfarm.core.session

import kotlinx.serialization.json.JsonArray
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

    /**
     * A Console card's questions answered at once, in the order the card drew them (vsc console.answer `answers`:
     * a label, or a list for a many-pick question). A question left unpicked is left out; none picked is null.
     */
    fun questions(card: String, ask: Ask, picked: List<List<String>>): AnswerCall? {
        val answers = ask.questions.mapIndexedNotNull { k, q ->
            val labels = picked.getOrNull(k).orEmpty().filter(String::isNotBlank)
            when {
                labels.isEmpty() -> null
                q.multiSelect -> q.question to JsonArray(labels.map { JsonPrimitive(it) })
                else -> q.question to JsonPrimitive(labels.first())
            }
        }.toMap()
        if (answers.isEmpty()) return null
        return AnswerCall("console.answer", buildJsonObject {
            put("id", JsonPrimitive(card))
            put("answers", JsonObject(answers))
        })
    }

    /** Words typed when no option says it (vsc console.answer `response`). */
    fun free(card: String, text: String) = AnswerCall(
        "console.answer",
        buildJsonObject {
            put("id", JsonPrimitive(card))
            put("response", JsonPrimitive(text))
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
