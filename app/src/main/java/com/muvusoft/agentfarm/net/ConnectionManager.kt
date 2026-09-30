package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.Call
import com.muvusoft.agentfarm.core.contract.Frame
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.state.OutboxItem
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellReducer
import com.muvusoft.agentfarm.core.state.ShellState
import com.muvusoft.agentfarm.core.state.newCallId
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
        scope.launch { _state.map { it.outbox }.distinctUntilChanged().drop(1).collect { save(it) } }
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

    /** Queues a call for a farm and sends it now when that farm is online. Returns the call id. */
    fun call(farmId: String, op: String, args: JsonObject? = null): String {
        val id = newCallId(clock(), random.nextLong())
        _state.update { ShellReducer.enqueue(it, OutboxItem(id, farmId, op, args, clock())) }
        flush(farmId)
        return id
    }

    fun focus(farmId: String) = _state.update { ShellReducer.focus(it, farmId) }

    /** Where a farm's pages come from right now; null while it is not online. */
    fun pageAccess(farmId: String): PageAccess? = conns[farmId]?.pageAccess()

    /** Sends a page frame (view.open / view.msg / view.close); false when the farm is not online. Not queued: a page reopens. */
    fun send(farmId: String, frame: Frame): Boolean = conns[farmId]?.send(frame) == true

    @Synchronized
    fun stopAll() {
        conns.values.forEach { it.stop() }
        conns.clear()
    }

    private fun events(farmId: String) = FarmConnectionEvents(
        onLink = { link -> _state.update { ShellReducer.link(it, farmId, link) } },
        onFrame = { frame, address ->
            _state.update { ShellReducer.onFrame(it, farmId, frame, address, clock()) }
            if (frame is Welcome) flush(farmId, resend = true)
            _frames.tryEmit(FarmFrame(farmId, frame))
        },
        resumeAfter = { ShellReducer.resumeAfter(_state.value, farmId) },
    )

    /** Sends what is still unsent; after a welcome also what was sent but never answered (the id makes it harmless). */
    private fun flush(farmId: String, resend: Boolean = false) {
        _state.update { ShellReducer.expire(it, clock()) }
        val conn = conns[farmId] ?: return
        for (item in ShellReducer.pending(_state.value, farmId).filter { resend || it.attempts == 0 }) {
            if (!conn.send(Call(item.id, item.op, item.args))) return
            _state.update { ShellReducer.sent(it, item.id) }
        }
    }
}
