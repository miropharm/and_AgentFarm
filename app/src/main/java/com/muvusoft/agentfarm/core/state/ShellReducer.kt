package com.muvusoft.agentfarm.core.state

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.NeedsChanged
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.Frame
import com.muvusoft.agentfarm.core.contract.Gap
import com.muvusoft.agentfarm.core.contract.Refuse
import com.muvusoft.agentfarm.core.contract.Welcome

/** Pure transitions of ShellState. Every function returns a new state; none reads a clock or the network. */
object ShellReducer {
    /** Adds a farm, or replaces the pairing of one already known (re-pairing keeps its event position). */
    fun pair(s: ShellState, farm: PairedFarm, now: Long): ShellState {
        val old = s.farm(farm.id)
        val status = FarmStatus(farm, Link.Offline("paired", now), old?.lastSeq ?: 0)
        val farms = if (old == null) s.farms + status else s.farms.map { if (it.farm.id == farm.id) status else it }
        return s.copy(farms = farms, focused = s.focused ?: farm.id)
    }

    /** Forgets a farm and everything queued for it. */
    fun forget(s: ShellState, farmId: String): ShellState = s.copy(
        farms = s.farms.filterNot { it.farm.id == farmId },
        focused = if (s.focused == farmId) s.farms.firstOrNull { it.farm.id != farmId }?.farm?.id else s.focused,
        outbox = s.outbox.filterNot { it.farm == farmId },
    )

    fun focus(s: ShellState, farmId: String): ShellState = if (s.farm(farmId) == null) s else s.copy(focused = farmId)

    fun link(s: ShellState, farmId: String, link: Link): ShellState = update(s, farmId) { it.copy(link = link) }

    /** What a frame from a farm changes. Frames that concern a page (view.*) change nothing here. */
    fun onFrame(s: ShellState, farmId: String, frame: Frame, address: String, now: Long): ShellState = when (frame) {
        is Welcome -> update(s, farmId) {
            it.copy(link = Link.Online(address, frame.scope, frame.features, now))
        }
        is Refuse -> update(s, farmId) { it.copy(link = Link.Refused(frame.reason, frame.detail, now)) }
        is EventFrame -> update(s, farmId) { event(it.copy(lastSeq = maxOf(it.lastSeq, frame.seq)), frame) }
        is Gap -> update(s, farmId) { it.copy(lastSeq = maxOf(it.lastSeq, frame.to)) }
        is CallResult -> s.copy(outbox = s.outbox.filterNot { it.id == frame.id && it.farm == farmId })
        else -> s
    }

    /** The resumeAfter a hello should carry: null on a first connection. */
    fun resumeAfter(s: ShellState, farmId: String): Long? = s.farm(farmId)?.lastSeq?.takeIf { it > 0 }

    fun enqueue(s: ShellState, item: OutboxItem): ShellState =
        if (s.outbox.any { it.id == item.id }) s else s.copy(outbox = s.outbox + item)

    fun expire(s: ShellState, now: Long): ShellState {
        val kept = Outbox.fresh(s.outbox, now)
        return if (kept.size == s.outbox.size) s else s.copy(outbox = kept)
    }

    /** Items to send to a farm, oldest first. */
    fun pending(s: ShellState, farmId: String): List<OutboxItem> =
        s.outbox.filter { it.farm == farmId }.sortedBy { it.createdAt }

    fun sent(s: ShellState, id: String): ShellState =
        s.copy(outbox = s.outbox.map { if (it.id == id) it.copy(attempts = it.attempts + 1) else it })

    /** What an event's data changes in the farm's summary; an unknown or malformed event changes nothing. */
    private fun event(f: FarmStatus, frame: EventFrame): FarmStatus = when (val d = Codec.eventData(frame)) {
        is NeedsChanged -> f.copy(needs = d.count)
        is TurnFinished -> f.copy(lastTurnAt = maxOf(f.lastTurnAt ?: 0, frame.ts))
        else -> f
    }

    private fun update(s: ShellState, farmId: String, f: (FarmStatus) -> FarmStatus): ShellState =
        s.copy(farms = s.farms.map { if (it.farm.id == farmId) f(it) else it })
}

/** A call id unique on this phone: time in base 36 plus random bits, `c_` first like Agent Farm's own. */
fun newCallId(nowMs: Long, random: Long): String =
    "c_" + nowMs.toString(36) + java.lang.Long.toString(random and 0xFFFFFFFFFFL, 36)
