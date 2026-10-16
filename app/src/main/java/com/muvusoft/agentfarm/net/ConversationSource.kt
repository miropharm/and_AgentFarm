package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.NeedsList
import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.contract.ReadPage
import com.muvusoft.agentfarm.core.conversation.ConversationBuild
import com.muvusoft.agentfarm.core.conversation.ConversationCopy
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.ConversationText
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.Outgoing
import com.muvusoft.agentfarm.core.conversation.SendGate
import com.muvusoft.agentfarm.core.conversation.SendState
import com.muvusoft.agentfarm.core.conversation.Sending
import com.muvusoft.agentfarm.core.conversation.Sent
import com.muvusoft.agentfarm.core.live.LiveDepth
import com.muvusoft.agentfarm.core.live.LiveEvent
import com.muvusoft.agentfarm.core.live.LiveMerge
import com.muvusoft.agentfarm.core.session.AnswerCall
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.share.SharedImage
import com.muvusoft.agentfarm.core.share.Stop
import com.muvusoft.agentfarm.core.state.Link
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One Conversation: the phone's copy at once, the last page when the farm answers, older pages as the list
 * reaches its top, and the card's whole live stream while the screen is in view (each turn's end and each
 * return of the link read what the stream told, so the record replaces it). Reads go outside the outbox;
 * what the phone says (a message, an answer) goes through it and arrives once.
 */
class ConversationSource(
    private val context: Context,
    private val manager: ConnectionManager,
    private val store: ConversationStore,
    private val farmId: String,
    private val key: ConversationKey,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var transcript: String? = key.transcript
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ConversationState> = _state
    private val reading = Mutex()
    private val paging = AtomicBoolean(false)

    private fun link(): Link = manager.state.value.farm(farmId)?.link ?: Link.Offline("unpaired", 0)

    private fun initial(): ConversationState {
        val base = store.load(farmId, key.id)?.state(link())
            ?: ConversationState(key.id, key.agent, key.title, "", link(), 0, loading = key.transcript != null || key.card != null)
        return base.copy(readOnly = if (key.card == null) ConversationText.NOT_IN_CONSOLE else null)
    }

    /** Runs while the screen is in view; leaving it ends the live watch. */
    suspend fun run() = coroutineScope {
        launch {
            manager.state.map { it.farm(farmId)?.link }.distinctUntilChanged().collect { l ->
                if (l == null) return@collect
                _state.update { it.copy(link = l) }
                // The link back reads on from the last row read (the copy's, or the screen's); only a screen with
                // nothing read, or one that missed more than a page, reads the last page.
                if (l is Link.Online) reading.withLock { caughtUp() }
            }
        }
        val card = key.card
        if (card != null) launch { manager.watch(farmId, card, LiveDepth.FULL).collect { onLive(it) } }
    }

    /** Retry, and the link back: the last page again. */
    suspend fun fresh() = reading.withLock { readTail() }

    /** The page above the first row, once the list reaches its top; one at a time. */
    suspend fun older() {
        val t = transcript ?: return
        val args = ConversationBuild.olderArgs(_state.value, t) ?: return
        if (!paging.compareAndSet(false, true)) return
        try {
            page(manager.read(farmId, OP_READ, args))?.let { p -> _state.update { ConversationBuild.older(it, p) } }
        } finally {
            paging.set(false)
        }
    }

    /** A step the farm cut, read whole. */
    suspend fun showAll(m: Message) {
        val t = transcript ?: return
        page(manager.read(farmId, OP_READ, ConversationBuild.wholeArgs(t, m)))?.let { p -> _state.update { ConversationBuild.whole(it, p) } }
    }

    /**
     * Words into the card: drawn at once as the phone's own bubble, then the farm's answer says where they are.
     * A farm that does not answer gets them through the outbox, and a notification tells how it went. `gate` is
     * the yes to the farm's gate, after the owner's confirmation.
     */
    fun send(text: String, gate: String? = null, image: SharedImage? = null) {
        val card = key.card ?: return
        val words = text.trim()
        if (words.isEmpty() && image == null) return
        val online = link() is Link.Online
        if (image != null && !online) {
            _state.update { it.copy(error = ConversationText.PICTURE_ONLINE) }
            return
        }
        val shown = words.ifEmpty { image?.name.orEmpty() }
        val o = Outgoing(UUID.randomUUID().toString(), shown, if (online) SendState.SENDING else SendState.WAITING, ConversationBuild.seen(_state.value, shown))
        _state.update { it.copy(outgoing = it.outgoing + o, gate = null, error = null) }
        if (!online) {
            val (op, args) = Share.call(card, words, null, gate)
            manager.call(farmId, op, args, receipt = key.agent)
            return
        }
        scope.launch {
            // A picture goes up to the PC first; if that never answered, nothing was said.
            val r = ShareSend.send(context, manager, farmId, card, Shared(words, image), gate)
            val v = if (r == null && image != null) Sent.Refused(ConversationText.NO_ANSWER) else Sending.of(r)
            _state.update { s ->
                val bubble = Sending.bubble(v)
                val out = if (bubble == null) s.outgoing.filterNot { it.id == o.id } else s.outgoing.map { if (it.id == o.id) it.copy(state = bubble) else it }
                ConversationBuild.echoed(
                    s.copy(
                        outgoing = out,
                        gate = (v as? Sent.Gate)?.let { SendGate(words, it.gate, it.sentence) } ?: s.gate,
                        error = (v as? Sent.Refused)?.sentence ?: s.error,
                    ),
                )
            }
        }
    }

    /** The owner wrote something else: the gate's question no longer stands. */
    fun dropGate() = _state.update { it.copy(gate = null) }

    /** Stops the running turn (after the owner's confirmation); the live stream says it stopped. */
    suspend fun stop() {
        val card = key.card ?: return
        val r = manager.request(farmId, OP_INTERRUPT, buildJsonObject { put("id", JsonPrimitive(card)) })
        if (r?.ok != true) _state.update { it.copy(error = Stop.outcome(false, r?.result, r?.error)) }
    }

    /** An answer to the card's question, through the outbox (it must arrive once). */
    fun answer(call: AnswerCall) {
        manager.call(farmId, call.op, call.args)
    }

    /** The farm's key for this session's permission request (needs.list), the only thing a permission is answered by. */
    suspend fun permissionKey(): String? {
        val r = manager.read(farmId, OP_NEEDS) ?: return null
        val needs = r.takeIf { it.ok }?.let { Ops.read(NeedsList.serializer(), it.result) } ?: return null
        return needs.items.firstOrNull { n ->
            n.kind == PERMISSION && (n.ref.str("consoleId") == key.card || (transcript != null && n.ref.str("sessionId") == transcript))
        }?.key
    }

    private suspend fun onLive(e: LiveEvent) {
        when (e) {
            is LiveEvent.Beat -> {
                val before = _state.value
                _state.update { ConversationBuild.echoed(LiveMerge.conversation(it, e.data, clock())) }
                if (LiveMerge.turnEnded(before, e.data)) reading.withLock { caughtUp() }
            }
            LiveEvent.Resumed -> reading.withLock { caughtUp() }
            is LiveEvent.Ended -> _state.update { it.copy(running = false, live = null, ask = null, readOnly = ConversationText.ended(e.reason)) }
        }
    }

    /** What the stream told, read from the record: after the last row read, or the last page when more was missed. */
    private suspend fun caughtUp() {
        if (_state.value.messages.none { !it.provisional }) return readTail()
        val t = named() ?: return
        val p = page(manager.read(farmId, OP_READ, ConversationBuild.newArgs(_state.value, t))) ?: return
        if (p.next != null) return readTail()
        keep { s -> LiveMerge.confirm(s, p.messages.map(ConversationBuild::step)) }
    }

    private suspend fun readTail() {
        val t = named()
        if (t == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        val r = manager.read(farmId, OP_READ, ConversationBuild.tailArgs(t))
        val p = page(r)
        if (p == null) {
            _state.update { it.copy(loading = false, error = ConversationText.notRead(r)) }
            return
        }
        keep { s -> ConversationBuild.tail(s, p).copy(engineLabel = p.engine) }
    }

    /** A read's result on screen and in the copy. */
    private fun keep(f: (ConversationState) -> ConversationState) {
        val s = _state.updateAndGet { ConversationBuild.echoed(f(it)).copy(updatedAt = clock()) }
        store.save(farmId, key.id, ConversationCopy.of(s.updatedAt, key, s))
    }

    /** The transcript a card hosts: known, or asked of the farm once the engine has named it. */
    private suspend fun named(): String? {
        transcript?.let { return it }
        val card = key.card ?: return null
        val r = manager.read(farmId, OP_CONSOLE, buildJsonObject {
            put("id", JsonPrimitive(card))
            put("view", JsonPrimitive("summary"))
        })
        return r?.takeIf { it.ok }?.let { Ops.consoleRows(it.result) }?.firstOrNull { it.id == card }?.sessionId?.also { transcript = it }
    }

    private fun page(r: CallResult?): ReadPage? = r?.takeIf { it.ok }?.let { Ops.read(ReadPage.serializer(), it.result) }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val OP_READ = "sessions.read"
        const val OP_CONSOLE = "console.sessions"
        const val OP_NEEDS = "needs.list"
        const val OP_INTERRUPT = "console.interrupt"
        const val PERMISSION = "permission"
    }
}
