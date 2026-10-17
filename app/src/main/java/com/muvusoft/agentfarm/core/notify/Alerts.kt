package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskAnswersSoon
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.state.Outbox
import com.muvusoft.agentfarm.core.state.OutboxItem
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.GateOpened
import com.muvusoft.agentfarm.core.contract.MissionEnded
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.Quiet
import com.muvusoft.agentfarm.core.contract.QuotaWarn
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.session.Answers
import kotlinx.serialization.json.JsonObject
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

/**
 * `silent`: shown without sound, vibration or reading aloud, because the user silenced it on the farm.
 * `renotify`: an update of a notification already shown that must sound again (a reminder).
 */
data class Alert(
    val channel: Channel,
    val tag: String,
    val title: String,
    val text: String,
    val actions: List<AlertAction>,
    val silent: Boolean = false,
    val renotify: Boolean = false,
    /** The Console card it is about: a tap opens its Conversation, the card open when it asks. */
    val session: String? = null,
)

object Alerts {
    /** Android shows three buttons on a notification; a question with more options is answered in the app. */
    const val MAX_BUTTONS = 3

    /** The notification an event deserves, or null for events that only update state. */
    fun of(farmName: String, e: FarmEventData): Alert? = when (e) {
        is AskOpened -> Alert(
            Channel.ASK, "ask:${e.askId}", Words.t("{0} is asking · {1}", e.agent, farmName), e.question,
            withStop(
                if (e.options.size in 1..MAX_BUTTONS) {
                    e.options.map { o -> Answers.question(e.session, e.question, o.label).let { c -> AlertAction(o.label, c.op, c.args) } }
                } else {
                    listOf(AlertAction(Words.t("Reply"), "console.answer", buildJsonObject { put("id", e.session) }, replyArg = "response"))
                },
                e.session,
            ),
            silenced(e.quiet),
            session = e.session,
        )
        is PermissionOpened -> Alert(
            Channel.ASK, "perm:${e.permId}", Words.t("{0} asks permission: {1} · {2}", e.agent, e.tool, farmName), e.summary,
            withStop(
                listOf(
                    Answers.need(e.key, Answers.ALLOW).let { c -> AlertAction(Words.t("Allow"), c.op, c.args, unlock = true) },
                    Answers.need(e.key, Answers.DENY).let { c -> AlertAction(Words.t("Deny"), c.op, c.args) },
                ),
                e.session,
            ),
            silenced(e.quiet),
            session = e.session,
        )
        is TurnFinished -> Alert(Channel.TURN, "turn:${e.session}", Words.t("{0}: turn finished · {1}", e.agent, farmName), e.title, emptyList(), silenced(e.quiet), session = e.session)
        is NoticePosted -> Alert(noticeChannel(e.level), "notice:${e.id}", "${e.agent} · $farmName", e.title, emptyList(), silenced(e.quiet))
        is GateOpened -> Alert(
            Channel.ASK, "gate:${e.gateId}", Words.t("Workflow \"{0}\" asks · {1}", e.workflow, farmName), e.question,
            if (e.options.size in 1..MAX_BUTTONS) {
                e.options.mapIndexed { i, o -> AlertAction(o, "workflows.answer", gateAnswer(e.gateId) { put("option", i + 1) }) }
            } else {
                listOf(AlertAction(Words.t("Reply"), "workflows.answer", gateAnswer(e.gateId) { }, replyArg = "answer"))
            },
            silenced(e.quiet),
        )
        is MissionEnded -> Alert(
            if (e.outcome == "failed") Channel.NOTICE else Channel.TURN, "mission:${e.mission}",
            if (e.outcome == "failed") Words.t("Mission failed · {0}", farmName) else Words.t("Mission finished · {0}", farmName), e.title, emptyList(), silenced(e.quiet),
        )
        is QuotaWarn -> Alert(
            Channel.NOTICE, "quota:${e.engine}:${e.window}", Words.t("Quota warning · {0}", farmName),
            Words.t("{0} {1} window is {2}% full", e.engine, e.window, e.percent.toInt()), emptyList(), silenced(e.quiet),
        )
        else -> null
    }

    /**
     * A question about to answer itself (the farm takes its recommended option when its clock runs out): the
     * question's own notification again, buttons and all, with the time left first, sounding again. A phone
     * that never saw the question (it started later) shows the reminder alone.
     */
    fun reminder(question: Alert?, farmName: String, e: AskAnswersSoon): Alert {
        val head = Words.t("Answers itself in {0} min", e.minutes)
        return question?.copy(title = "$head · ${question.title}", silent = question.silent || silenced(e.quiet), renotify = true)
            ?: Alert(
                Channel.ASK, "ask:${e.askId}", Words.t("{0}'s question answers itself in {1} min · {2}", e.agent, e.minutes, farmName),
                Words.t("Answer it before then, or its recommended option is taken for you."), emptyList(), silenced(e.quiet), renotify = true,
            )
    }

    /** A share queued offline was answered, or waited too long (`result` null): arrived is routine, not arrived is a notice. */
    fun receipt(item: OutboxItem, farmName: String, result: CallResult?): Alert {
        val r = Share.receipt(item.receipt ?: item.farm, farmName, result, Outbox.MAX_AGE_HOURS)
        return Alert(if (r.delivered) Channel.ROUTINE else Channel.NOTICE, "sent:${item.id}", r.title, r.text, emptyList())
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

    /**
     * A waiting agent's turn can be stopped from its notification, where a button is left: console.interrupt
     * keeps the session. It cannot be undone, so the device is unlocked first.
     */
    private fun withStop(actions: List<AlertAction>, session: String): List<AlertAction> =
        if (actions.size < MAX_BUTTONS) actions + AlertAction(Words.t("Stop"), "console.interrupt", buildJsonObject { put("id", session) }, unlock = true) else actions

    private fun gateAnswer(gateId: String, more: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("id", gateId)
        more()
    }
}
