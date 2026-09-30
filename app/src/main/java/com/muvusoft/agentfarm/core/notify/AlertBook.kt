package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskClosed
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.NeedsChanged
import com.muvusoft.agentfarm.core.contract.PermissionClosed
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded

/**
 * The open question and permission alerts, by farm and session, so an alert whose wait ended on
 * the farm (answered at the desk or on Telegram, session closed) leaves the phone too: by its own
 * id when the farm says which one closed, or all of a farm's when nothing waits there any more. In memory: a process that
 * restarts forgets what an earlier one posted.
 */
class AlertBook {
    private data class Open(val farm: String, val session: String, val tag: String)

    private val open = mutableListOf<Open>()

    /** Tags to take down because of this event; call before posting the event's own alert. */
    @Synchronized
    fun settle(farm: String, e: FarmEventData): List<String> {
        val gone = when (e) {
            is NeedsChanged -> if (e.count == 0L) open.filter { it.farm == farm } else emptyList()
            is SessionEnded -> open.filter { it.farm == farm && it.session == e.session }
            is AskClosed -> open.filter { it.farm == farm && it.tag == "ask:${e.askId}" }
            is PermissionClosed -> open.filter { it.farm == farm && it.tag == "perm:${e.permId}" }
            else -> emptyList()
        }
        open.removeAll(gone)
        return gone.map { it.tag }
    }

    /** Remembers an alert that waits on the farm; other alerts are not tracked. */
    @Synchronized
    fun posted(farm: String, e: FarmEventData, alert: Alert) {
        val session = when (e) {
            is AskOpened -> e.session
            is PermissionOpened -> e.session
            else -> return
        }
        open.removeAll { it.tag == alert.tag }
        open += Open(farm, session, alert.tag)
    }
}
