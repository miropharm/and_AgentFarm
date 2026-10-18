package com.muvusoft.agentfarm.core.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// The op answers the native screens read, as the contract's "ops" declares them: every declared field under its
// name, nullable exactly when the contract says `type?` (test/test_contract_models.js reads both sides).

/** One `console.sessions` row; `view: "summary"` answers exactly these fields. */
@Serializable
data class ConsoleRow(
    val id: String,
    val agent: String,
    val sessionId: String? = null,
    val engine: String,
    val engineLabel: String,
    val steerable: Boolean,
    val status: String,
    val model: String? = null,
    val effort: String? = null,
    val applied: JsonObject? = null,
    val cost: Double,
    val turns: Int,
    val held: Int,
    val lastTurn: JsonObject? = null,
    val contextPct: Double? = null,
    val asking: JsonObject? = null,
    val exitReason: String? = null,
    val messages: JsonArray? = null,
)

/** `sessions.recent { view: "page" }`: light rows newest first; `next` is the following page's cursor. */
@Serializable
data class RecentPage(
    val rows: List<RecentRow>,
    val next: String? = null,
)

@Serializable
data class RecentRow(
    val id: String,
    val agent: String,
    val title: String,
    val engine: String,
    val lastTs: Long,
    val origin: String,
    val status: String? = null,
    val waiting: Boolean,
    val lastLine: String? = null,
)

/** `needs.list`: what waits for the user anywhere on the farm. */
@Serializable
data class NeedsList(
    val items: List<NeedRow>,
    val blocking: Int,
    val hidden: Int,
)

@Serializable
data class NeedRow(
    val kind: String,
    val agentName: String,
    val title: String,
    val detail: String,
    val ts: Long,
    val ref: JsonObject,
    val actions: JsonArray,
    val key: String,
    val options: JsonArray? = null,
)

/** `sessions.read`: one page of a session's steps. */
@Serializable
data class ReadPage(
    val id: String,
    val file: String,
    val engine: String,
    val title: String,
    val agent: String? = null,
    val turns: Int,
    val total: Int,
    val from: Int,
    val returned: Int,
    val next: Int? = null,
    val clipped: Int,
    val messages: List<ReadStep>,
)

@Serializable
data class ReadStep(
    val i: Int,
    val key: String,
    val turn: Int? = null,
    val role: String,
    val ts: Long,
    val text: String? = null,
    val tool: String? = null,
    val input: String? = null,
    val isError: Boolean? = null,
    val clipped: Int? = null,
)

/** `pages.list`: the farm pages the phone's Menu lists, in its order and groups (the PC owns the list). */
@Serializable
data class PagesList(val pages: List<MenuPage>)

@Serializable
data class MenuPage(val id: String, val label: String, val group: String)

/** `now.summary`: Now said by the farm as one sentence, its two counts, and its Console questions as ask.opened says them. */
@Serializable
data class NowSummary(val spoken: Spoken, val waiting: Int, val running: Int, val asks: List<AskOpened>)

/** The model of each op's object answer (an array answer has none: its rows are the answer). */
val OP_ANSWERS: Map<String, KSerializer<*>> = mapOf(
    "sessions.read" to ReadPage.serializer(),
    "sessions.recent" to RecentPage.serializer(),
    "needs.list" to NeedsList.serializer(),
    "pages.list" to PagesList.serializer(),
    "now.summary" to NowSummary.serializer(),
)

/** The model of each op's rows. */
val OP_ROWS: Map<String, KSerializer<*>> = mapOf(
    "console.sessions" to ConsoleRow.serializer(),
    "sessions.read" to ReadStep.serializer(),
    "sessions.recent" to RecentRow.serializer(),
    "needs.list" to NeedRow.serializer(),
    "pages.list" to MenuPage.serializer(),
    "now.summary" to AskOpened.serializer(),
)

object Ops {
    /** An op's answer as its model; null when it does not read (a screen keeps what it had). */
    fun <T> read(serializer: KSerializer<T>, result: JsonElement?): T? {
        if (result == null) return null
        return try {
            Codec.json.decodeFromJsonElement(serializer, result)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** console.sessions' rows; a row that does not read is left out, the rest still draw. */
    fun consoleRows(result: JsonElement?): List<ConsoleRow>? =
        (result as? JsonArray)?.mapNotNull { read(ConsoleRow.serializer(), it) }
}
