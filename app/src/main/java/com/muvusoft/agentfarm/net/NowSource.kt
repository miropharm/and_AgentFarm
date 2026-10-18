package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.NeedsList
import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.contract.RecentPage
import com.muvusoft.agentfarm.core.live.Live
import com.muvusoft.agentfarm.core.live.LiveDepth
import com.muvusoft.agentfarm.core.live.LiveEvent
import com.muvusoft.agentfarm.core.live.LiveMerge
import com.muvusoft.agentfarm.core.now.NowBuild
import com.muvusoft.agentfarm.core.now.NowCopy
import com.muvusoft.agentfarm.core.now.NowOpen
import com.muvusoft.agentfarm.core.now.NowTarget
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.NowText
import com.muvusoft.agentfarm.core.now.Waiting
import com.muvusoft.agentfarm.core.now.Waits
import com.muvusoft.agentfarm.core.session.Quick
import com.muvusoft.agentfarm.core.state.Link
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One farm's Now: the phone's copy at once, the farm's answers whenever it comes online, the history a page at
 * a time, and every card's live summary while the screen is in view. Reads go outside the outbox
 * (ConnectionManager.read), so a read nobody waits for is never sent again; answers go through it.
 */
class NowSource(
    private val manager: ConnectionManager,
    private val store: NowStore,
    private val farmId: String,
    private val preload: ConversationPreload? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<NowState> = _state
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val paging = AtomicBoolean(false)
    private val reading = AtomicBoolean(false)
    private val again = AtomicBoolean(false)
    private val asked = mutableSetOf<String>()

    private fun name() = manager.state.value.farm(farmId)?.farm?.name ?: farmId
    private fun link(): Link = manager.state.value.farm(farmId)?.link ?: Link.Offline("unpaired", 0)

    private fun initial(): NowState =
        store.load(farmId)?.state(name(), link()) ?: NowState(name(), link(), 0, loading = true)

    /** Runs while the screen is in view; leaving it ends the live watch. */
    suspend fun run() = coroutineScope {
        launch {
            manager.state.map { it.farm(farmId)?.link }.distinctUntilChanged().collect { l ->
                if (l == null) return@collect
                _state.update { it.copy(link = l) }
                if (l is Link.Online) fresh()
            }
        }
        launch {
            manager.frames.filter { it.farm == farmId }.collect { f -> (f.frame as? EventFrame)?.let { onEvent(it.type) } }
        }
        launch { manager.watch(farmId, Live.EVERY_SESSION, LiveDepth.SUMMARY).collect { onLive(it) } }
    }

    /** Pull to refresh: the three answers again. */
    suspend fun refresh() {
        if (link() is Link.Online) fresh()
    }

    /**
     * The next history page, once the list reaches its end; one at a time. A page that comes back after the fresh
     * head moved the list's end is not laid on it, and the new end is asked in its place: nothing grew, so the
     * loader that asked would never ask again.
     */
    suspend fun more() {
        if (_state.value.cursor == null || !paging.compareAndSet(false, true)) return
        try {
            do {
                val cursor = _state.value.cursor ?: return
                val page = page(manager.read(farmId, OP_RECENT, pageArgs(cursor))) ?: return
                var laid = false
                _state.update { s ->
                    laid = s.cursor == cursor
                    if (laid) NowBuild.more(s, page) else s
                }
            } while (!laid)
        } finally {
            paging.set(false)
        }
    }

    /** A row's own button: the farm's answer call, through the outbox (it must arrive exactly once). */
    fun quick(w: Waiting, q: Quick) {
        val call = Waits.answer(w, q) ?: return
        manager.call(farmId, call.op, call.args)
    }

    /** The three answers; a read asked for while one runs is made once more after it, never side by side. */
    private suspend fun fresh() {
        if (!reading.compareAndSet(false, true)) {
            again.set(true)
            return
        }
        _refreshing.value = true
        try {
            do {
                again.set(false)
                readAll()
            } while (again.get())
        } finally {
            reading.set(false)
            _refreshing.value = false
        }
    }

    private suspend fun readAll() {
        val (c, p, n) = coroutineScope {
            val cards = async { manager.read(farmId, OP_CONSOLE, buildJsonObject { put("view", JsonPrimitive("summary")) }) }
            val page = async { manager.read(farmId, OP_RECENT, pageArgs(null)) }
            val needs = async { manager.read(farmId, OP_NEEDS) }
            Triple(cards.await(), page.await(), needs.await())
        }
        val rows = c?.takeIf { it.ok }?.let { Ops.consoleRows(it.result) }
        val first = page(p)
        val waits = n?.takeIf { it.ok }?.let { Ops.read(NeedsList.serializer(), it.result) }
        if (rows == null || first == null || waits == null) {
            _state.update { it.copy(loading = false, error = NowText.notRead(c, p, n, online = link() is Link.Online)) }
            return
        }
        val copy = NowCopy(clock(), rows, first, waits)
        store.save(farmId, copy)
        _state.update { old ->
            val built = NowBuild.carry(old, copy.state(name(), link()))
            if (old.recent.isEmpty()) built else NowBuild.head(built.copy(recent = old.recent, cursor = old.cursor), first)
        }
        val now = _state.value
        preload?.ahead(now.running.mapNotNull { (NowOpen.of(now, it.session) as? NowTarget.Conversation)?.key })
    }

    private suspend fun head() {
        val first = page(manager.read(farmId, OP_RECENT, pageArgs(null))) ?: return
        _state.update { NowBuild.head(it, first) }
    }

    private suspend fun onLive(e: LiveEvent) {
        if (e !is LiveEvent.Beat) return
        val before = _state.value
        _state.update { LiveMerge.now(it, e.session, e.data, clock()) }
        when {
            NowBuild.turnEnded(before, e.session, e.data.working) -> head()
            e.session !in before.cards && asked.add(e.session) -> fresh()
        }
    }

    private suspend fun onEvent(type: String) {
        if (type == NEEDS_CHANGED) fresh()
    }

    private fun page(r: CallResult?): RecentPage? = r?.takeIf { it.ok }?.let { Ops.read(RecentPage.serializer(), it.result) }

    private fun pageArgs(cursor: String?): JsonObject = buildJsonObject {
        put("view", JsonPrimitive("page"))
        if (cursor != null) put("cursor", JsonPrimitive(cursor))
    }

    private companion object {
        const val OP_CONSOLE = "console.sessions"
        const val OP_RECENT = "sessions.recent"
        const val OP_NEEDS = "needs.list"
        const val NEEDS_CHANGED = "needs.changed"
    }
}
