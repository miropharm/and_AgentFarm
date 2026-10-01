package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.QuotaWarn
import com.muvusoft.agentfarm.core.contract.TurnFinished
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The notification channels, loudest first. Quiet hours are Android's Do Not Disturb, not ours. */
enum class Channel(val id: String) { ASK("ask"), NOTICE("notice"), TURN("turn"), LINK("link") }

/**
 * A button on a notification: the bridge op it calls. `unlock` asks for the device to be unlocked
 * first (a tool approval); `reply` takes the text typed into the notification as `replyArg`.
 */
data class AlertAction(
    val label: String,
    val op: String,
    val args: JsonObject,
    val unlock: Boolean = false,
    val replyArg: String? = null,
)

data class Alert(val channel: Channel, val tag: String, val title: String, val text: String, val actions: List<AlertAction>)

object Alerts {
    /** Android shows three buttons on a notification; a question with more options is answered in the app. */
    const val MAX_BUTTONS = 3

    /** The notification an event deserves, or null for events that only update state. */
    fun of(farmName: String, e: FarmEventData): Alert? = when (e) {
        is AskOpened -> Alert(
            Channel.ASK, "ask:${e.askId}", "${e.agent} is asking · $farmName", e.question,
            if (e.options.size in 1..MAX_BUTTONS) {
                e.options.map { AlertAction(it.label, "console.answer", answer(e.session, e.question, it.label)) }
            } else {
                listOf(AlertAction("Reply", "console.answer", buildJsonObject { put("id", e.session) }, replyArg = "response"))
            },
        )
        is PermissionOpened -> Alert(
            Channel.ASK, "perm:${e.permId}", "${e.agent} asks permission: ${e.tool} · $farmName", e.summary,
            listOf(
                AlertAction("Allow", "needs.act", act(e.key, "permission-allow"), unlock = true),
                AlertAction("Deny", "needs.act", act(e.key, "permission-deny")),
            ),
        )
        is TurnFinished -> Alert(Channel.TURN, "turn:${e.session}", "${e.agent}: turn finished · $farmName", e.title, emptyList())
        is NoticePosted -> Alert(Channel.NOTICE, "notice:${e.id}", "${e.agent} · $farmName", e.title, emptyList())
        is QuotaWarn -> Alert(
            Channel.NOTICE, "quota:${e.engine}:${e.window}", "Quota warning · $farmName",
            "${e.engine} ${e.window} window is ${e.percent.toInt()}% full", emptyList(),
        )
        else -> null
    }

    private fun answer(session: String, question: String, label: String) = buildJsonObject {
        put("id", session)
        put("answers", JsonObject(mapOf(question to JsonPrimitive(label))))
    }

    private fun act(key: String, action: String) = buildJsonObject {
        put("key", key)
        put("action", action)
    }
}
