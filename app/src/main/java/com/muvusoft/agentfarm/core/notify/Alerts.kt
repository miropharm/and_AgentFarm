package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.Quiet
import com.muvusoft.agentfarm.core.contract.QuotaWarn
import com.muvusoft.agentfarm.core.contract.TurnFinished
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The notification channels, loudest first; each is one subject the user can tune in the phone's settings.
 * Quiet hours are Android's Do Not Disturb, not ours.
 */
enum class Channel(val id: String) {
    ASK("ask"),
    IMPORTANT("important"),
    NOTICE("notice"),
    TURN("turn"),
    ROUTINE("routine"),
    LINK("link"),
}

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

/** `silent`: shown without sound, vibration or reading aloud, because the user silenced it on the farm. */
data class Alert(
    val channel: Channel,
    val tag: String,
    val title: String,
    val text: String,
    val actions: List<AlertAction>,
    val silent: Boolean = false,
)

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
            silenced(e.quiet),
        )
        is PermissionOpened -> Alert(
            Channel.ASK, "perm:${e.permId}", "${e.agent} asks permission: ${e.tool} · $farmName", e.summary,
            listOf(
                AlertAction("Allow", "needs.act", act(e.key, "permission-allow"), unlock = true),
                AlertAction("Deny", "needs.act", act(e.key, "permission-deny")),
            ),
            silenced(e.quiet),
        )
        is TurnFinished -> Alert(Channel.TURN, "turn:${e.session}", "${e.agent}: turn finished · $farmName", e.title, emptyList(), silenced(e.quiet))
        is NoticePosted -> Alert(noticeChannel(e.level), "notice:${e.id}", "${e.agent} · $farmName", e.title, emptyList(), silenced(e.quiet))
        is QuotaWarn -> Alert(
            Channel.NOTICE, "quota:${e.engine}:${e.window}", "Quota warning · $farmName",
            "${e.engine} ${e.window} window is ${e.percent.toInt()}% full", emptyList(), silenced(e.quiet),
        )
        else -> null
    }

    /** A notice's importance (1 routine · 2 medium · 3 important · 4 critical) picks its channel. */
    fun noticeChannel(level: Long): Channel = when {
        level >= 3 -> Channel.IMPORTANT
        level == 2L -> Channel.NOTICE
        else -> Channel.ROUTINE
    }

    /**
     * The farm's mute and a muted session are the user's own word, so the phone keeps quiet too. The
     * farm's quiet hours alone do not silence the phone: there, the phone's Do Not Disturb decides.
     */
    fun silenced(quiet: Quiet?): Boolean = quiet?.reasons.orEmpty().any { it == "fleet" || it == "session" }

    private fun answer(session: String, question: String, label: String) = buildJsonObject {
        put("id", session)
        put("answers", JsonObject(mapOf(question to JsonPrimitive(label))))
    }

    private fun act(key: String, action: String) = buildJsonObject {
        put("key", key)
        put("action", action)
    }
}
