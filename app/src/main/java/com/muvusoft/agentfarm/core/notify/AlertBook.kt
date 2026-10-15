package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskAnswersSoon
import com.muvusoft.agentfarm.core.contract.AskClosed
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.GateClosed
import com.muvusoft.agentfarm.core.contract.GateOpened
import com.muvusoft.agentfarm.core.contract.NeedsChanged
import com.muvusoft.agentfarm.core.contract.PermissionClosed
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded

/**
 * The open question and permission alerts, by farm and session, so an alert whose wait ended on
 * the farm (answered at the desk or on Telegram, session closed) leaves the phone too: by its own
 * id when the farm says which one closed, or all of a farm's when nothing waits there any more.
 * A question's own alert is kept as first drawn, for its reminder to show again. In memory: a process
 * that restarts forgets what an earlier one posted.
 */
class AlertBook {
    private data class Open(val farm: String, val session: String, val tag: String, val alert: Alert)

    private val open = mutableListOf<Open>()

    /** Tags to take down because of this event; call before posting the event's own alert. */
    @Synchronized
    fun settle(farm: String, e: FarmEventData): List<String> {
        val gone = when (e) {
            is NeedsChanged -> if (e.count == 0L) open.filter { it.farm == farm } else emptyList()
            is SessionEnded -> open.filter { it.farm == farm && it.session == e.session }
            is AskClosed -> open.filter { it.farm == farm && it.tag == "ask:${e.askId}" }
            is PermissionClosed -> open.filter { it.farm == farm && it.tag == "perm:${e.permId}" }
            is GateClosed -> open.filter { it.farm == farm && it.tag == "gate:${e.gateId}" }
            else -> emptyList()
        }
        open.removeAll(gone)
        return gone.map { it.tag }
    }

    /** The notification an event deserves: a question's reminder is that question's own alert again. */
    fun alertFor(farm: String, farmName: String, e: FarmEventData): Alert? =
        if (e is AskAnswersSoon) Alerts.reminder(alertOf(farm, "ask:${e.askId}"), farmName, e) else Alerts.of(farmName, e)

    /** The alert a waiting question or permission was first shown with, while it still waits. */
    @Synchronized
    fun alertOf(farm: String, tag: String): Alert? = open.firstOrNull { it.farm == farm && it.tag == tag }?.alert

    /** Remembers an alert that waits on the farm; a reminder keeps the record of the question it reminds of. */
    @Synchronized
    fun posted(farm: String, e: FarmEventData, alert: Alert) {
        val session = when (e) {
            is AskOpened -> e.session
            is PermissionOpened -> e.session
            // A gate waits on a workflow run, not a session: no session's end takes it down, only its own close.
            is GateOpened -> "run:${e.run}"
            is AskAnswersSoon -> if (open.any { it.farm == farm && it.tag == alert.tag }) return else e.session
            else -> return
        }
        open.removeAll { it.farm == farm && it.tag == alert.tag }
        open += Open(farm, session, alert.tag, alert)
    }
}
