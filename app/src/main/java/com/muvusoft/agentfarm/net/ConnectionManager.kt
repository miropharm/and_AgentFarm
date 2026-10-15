package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.away.Away
import com.muvusoft.agentfarm.core.contract.Call
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.Frame
import com.muvusoft.agentfarm.core.contract.WakeNic
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.link.Reconnect
import com.muvusoft.agentfarm.core.share.ShareTarget
import com.muvusoft.agentfarm.core.state.Outbox
import com.muvusoft.agentfarm.core.state.OutboxItem
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellReducer
import com.muvusoft.agentfarm.core.state.ShellState
import com.muvusoft.agentfarm.core.state.newCallId
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** A frame from a farm, for whoever renders it (pages, notifications). */
data class FarmFrame(val farm: String, val frame: Frame)

/**
 * Every paired farm's connection, and the one ShellState they all write through ShellReducer.
 * Calls get their id here, on the phone, so a resend after a reconnect is harmless. The outbox starts
 * from `saved` and every change to it goes to `save`.
 */
class ConnectionManager(
    private val scope: CoroutineScope,
    saved: List<OutboxItem>,
    save: (List<OutboxItem>) -> Unit,
    /** A welcome named the farm's network cards: kept for waking its PC later (taken as the frame arrives, never missed). */
    private val onWake: (farmId: String, nics: List<WakeNic>) -> Unit,
    /** A share queued offline was answered, or waited too long (`result` null): its receipt is shown. */
    private val onReceipt: (farmName: String, item: OutboxItem, result: CallResult?) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connect: (PairedFarm, FarmConnectionEvents) -> FarmConnection = { f, e -> FarmConnection(f, e) },
) {
    private val random = SecureRandom()
    private val conns = ConcurrentHashMap<String, FarmConnection>()
    private val _state = MutableStateFlow(ShellState(outbox = saved))
    private val _frames = MutableSharedFlow<FarmFrame>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val state: StateFlow<ShellState> = _state
    val frames: SharedFlow<FarmFrame> = _frames

    init {
        // No drop(1): a call made before this collector subscribes is already in its first value.
        scope.launch { _state.map { it.outbox }.distinctUntilChanged().collect { save(it) } }
    }

    /** Makes the connections match the paired farms: new ones start, forgotten ones stop, re-paired ones restart. */
    @Synchronized
    fun setFarms(farms: List<PairedFarm>) {
        val ids = farms.map { it.id }.toSet()
        for (id in conns.keys - ids) {
            conns.remove(id)?.stop()
            _state.update { ShellReducer.forget(it, id) }
        }
        for (farm in farms) {
            val known = _state.value.farm(farm.id)?.farm
            if (known == farm && conns.containsKey(farm.id)) continue
            conns.remove(farm.id)?.stop()
            _state.update { ShellReducer.pair(it, farm, clock()) }
            val conn = connect(farm, events(farm.id))
            conns[farm.id] = conn
            conn.start(scope)
        }
    }

    /**
     * Queues a call for a farm and sends it now when that farm is online. Returns the call id. `receipt`
     * names whom a share made offline goes to: how it went is told once the farm answers or it expires.
     */
    fun call(farmId: String, op: String, args: JsonObject? = null, receipt: String? = null): String {
        val id = newCallId(clock(), random.nextLong())
        _state.update { ShellReducer.enqueue(it, OutboxItem(id, farmId, op, args, clock(), receipt = receipt)) }
        flush(farmId)
        return id
    }

    fun focus(farmId: String) = _state.update { ShellReducer.focus(it, farmId) }

    fun sessions(farmId: String, targets: List<ShareTarget>) = _state.update { ShellReducer.sessions(it, farmId, targets) }

    fun agents(farmId: String, names: List<String>) = _state.update { ShellReducer.agents(it, farmId, names) }

    /** Drops what waited too long, telling the receipts; also run on a clock, since a farm that never answers flushes nothing. */
    fun expire() {
        val now = clock()
        val before = _state.value.outbox
        val gone = before.filter { it.receipt != null } - Outbox.fresh(before, now).toSet()
        _state.update { ShellReducer.expire(it, now) }
        gone.forEach { onReceipt(nameOf(it.farm), it, null) }
    }

    private fun nameOf(farmId: String) = _state.value.farm(farmId)?.farm?.name ?: farmId

    fun away(farmId: String, away: Away?) = _state.update { ShellReducer.away(it, farmId, away) }

    /** Where a farm's pages come from right now; null while it is not online. */
    fun pageAccess(farmId: String): PageAccess? = conns[farmId]?.pageAccess()

    /** Sends a page frame (view.open / view.msg / view.close); false when the farm is not online. Not queued: a page reopens. */
    fun send(farmId: String, frame: Frame): Boolean = conns[farmId]?.send(frame) == true

    fun onNetwork(net: Reconnect.Net) = conns.values.forEach { it.onNetwork(net) }

    /** A paired farm answered on the local network at `address` (FarmFinder). */
    fun onFound(farmId: String, address: String) = conns[farmId]?.onFound(address)

    /** A woken PC needs a little while: the link tries again at each of these marks instead of waiting out its backoff. */
    fun retryAfterWake(farmId: String) {
        scope.launch {
            for (mark in Reconnect.AFTER_WAKE_MS) {
                delay(mark)
                conns[farmId]?.retryNow()
            }
        }
    }

    @Synchronized
    fun stopAll() {
        conns.values.forEach { it.stop() }
        conns.clear()
    }

    private fun events(farmId: String) = FarmConnectionEvents(
        onLink = { link -> _state.update { ShellReducer.link(it, farmId, link) } },
        onFrame = { frame, address ->
            val answered = (frame as? CallResult)?.let { r -> _state.value.outbox.firstOrNull { it.id == r.id && it.farm == farmId && it.receipt != null } }
            _state.update { ShellReducer.onFrame(it, farmId, frame, address, clock()) }
            if (answered != null) onReceipt(nameOf(farmId), answered, frame as CallResult)
            if (frame is Welcome) {
                frame.wake?.takeIf { it.isNotEmpty() }?.let { onWake(farmId, it) }
                flush(farmId, resend = true)
            }
            _frames.tryEmit(FarmFrame(farmId, frame))
        },
        resumeAfter = { ShellReducer.resumeAfter(_state.value, farmId) },
    )

    /** Sends what is still unsent; after a welcome also what was sent but never answered (the id makes it harmless). */
    private fun flush(farmId: String, resend: Boolean = false) {
        expire()
        val conn = conns[farmId] ?: return
        for (item in ShellReducer.pending(_state.value, farmId).filter { resend || it.attempts == 0 }) {
            if (!conn.send(Call(item.id, item.op, item.args))) return
            _state.update { ShellReducer.sent(it, item.id) }
        }
    }
}
