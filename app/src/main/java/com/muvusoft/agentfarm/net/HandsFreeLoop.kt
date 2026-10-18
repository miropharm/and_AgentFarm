package com.muvusoft.agentfarm.net

import android.util.Log
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.handsfree.Heard
import com.muvusoft.agentfarm.core.handsfree.StatusCall
import com.muvusoft.agentfarm.core.handsfree.VoiceAnswer
import com.muvusoft.agentfarm.core.handsfree.VoiceNew
import com.muvusoft.agentfarm.core.session.AnswerCall
import com.muvusoft.agentfarm.core.speech.Utterance
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The headset mode's answer by voice (S-8, S-9; design: the vault's Kulaklik Modu note). A question, or a finished
 * turn when the user wants it, is read; a tone plays, what is said within [VoiceAnswer.LISTEN_MS] is read back, and
 * unless it is called off within [VoiceAnswer.CANCEL_MS] it goes to the card through the outbox - the question's
 * answer, or the session's next instruction - now, or when the farm answers again (then the phone says it waits).
 * Silence sends nothing and says nothing. One at a time: a newer one cuts an older one's loop. A card whose
 * Conversation is on screen is answered there, not by voice. The headset button's presses run here too (S-10, S-11):
 * the farm's Now said and its question answered, or a new session by voice.
 */
class HandsFreeLoop(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val voice: () -> Locale,
    private val visible: () -> Boolean,
    private val send: (farm: String, call: AnswerCall) -> Boolean,
) {
    /** Whether the mode may listen now: on, the microphone granted, a headset on (core/handsfree/HandsFree). */
    @Volatile
    var ready: () -> Boolean = { false }

    @Volatile
    var ears: Ears? = null

    /** Says an utterance and returns once it was said to its end; a test hears it instead of a voice. */
    @Volatile
    var mouth: suspend (Utterance) -> Boolean = this::spoken

    /** The card whose Conversation is open (ui/conversation/ConversationRoute). */
    @Volatile
    var inFront: String? = null

    private val ending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val count = AtomicInteger()
    private var loop: Job? = null

    init {
        speaker.ended = { id, whole -> ending.remove(id)?.complete(whole) }
    }

    /** Whether the mode also listens after a finished turn ([ShellPrefs.handsFreeTurns]). */
    @Volatile
    var turns: () -> Boolean = { false }

    @Volatile
    private var asking = false

    /**
     * An alert to read as [u]: a question (its answer), or a finished turn when [turns] (its session's next
     * instruction). True when the loop took it and reads it itself; false leaves it to the plain voice. A turn that
     * ends while a question is being answered is not read over it.
     */
    fun take(farm: String, u: Utterance, event: FarmEventData): Boolean {
        val e = ears ?: return false
        val card = when (event) {
            is AskOpened -> event.session
            is TurnFinished -> event.session.takeIf { turns() } ?: return false
            else -> return false
        }
        if (!ready() || (visible() && inFront == card)) return false
        synchronized(this) {
            if (event is TurnFinished && asking) return true
            begin(asks = event is AskOpened) {
                answer(farm, u, e) { said ->
                    if (event is AskOpened) VoiceAnswer.heard(card, event.question, event.options.map { it.label }, said) else VoiceAnswer.instruction(card, said)
                }
            }
        }
        return true
    }

    /**
     * One press of the headset button (S-10): what [said] says is read - the farm's Now, or the phone's word for why
     * there is none - then the farm's first Console question is answered by voice as a question read aloud is. A
     * press cuts whatever the loop was doing: the user asked for this, now.
     */
    fun status(farm: String, said: StatusCall.Said): Boolean {
        val e = ears ?: return false
        if (!ready()) return false
        val (u, ask) = when (said) {
            is StatusCall.Farm -> Utterance("handsfree-${count.incrementAndGet()}", said.summary.spoken.text.trim(), interrupt = true, lang = said.summary.spoken.lang.trim()) to said.summary.asks.firstOrNull()
            is StatusCall.Own -> line(said.text).copy(interrupt = true) to null
        }
        synchronized(this) {
            begin(asks = ask != null) {
                if (!mouth(u)) return@begin
                val a = ask?.takeUnless { visible() && inFront == it.session } ?: return@begin
                val q = a.spoken?.takeIf { it.text.isNotBlank() }?.let { Utterance("handsfree-${count.incrementAndGet()}", it.text.trim(), interrupt = false, lang = it.lang.trim()) }
                    ?: line("${a.agent}: ${a.question}")
                answer(farm, q, e) { s -> VoiceAnswer.heard(a.session, a.question, a.options.map { it.label }, s) }
            }
        }
        return true
    }

    /**
     * Two presses of the headset button (S-11): "which agent?" (named by the farm's own [agents]), "what should it
     * do?", both read back, and unless called off the session opens through [open] (console.new); what the farm
     * answered is said. Silence at either question opens nothing and says nothing; nothing is read over it.
     */
    fun newSession(farm: String, agents: List<String>, open: suspend (AnswerCall) -> CallResult?): Boolean {
        val e = ears ?: return false
        if (!ready()) return false
        synchronized(this) {
            begin(asks = true) {
                val lang = voice().language
                if (!mouth(line(VoiceNew.whichAgent(lang)).copy(interrupt = true))) return@begin
                val agent = heard(e, VoiceAnswer.LISTEN_MS, lang)?.let { VoiceNew.agent(it, agents) } ?: return@begin
                if (!mouth(line(VoiceNew.whatToDo(lang, agent)))) return@begin
                val words = heard(e, VoiceNew.PROMPT_MS, lang) ?: return@begin
                mouth(line(VoiceNew.readBack(lang, agent, words)))
                if (calledOff(e, lang)) return@begin
                Log.i(TAG, "console.new by voice")
                mouth(line(VoiceNew.said(lang, agent, open(VoiceNew.call(agent, words)))))
            }
        }
        return true
    }

    /** A tone, then what is said within [ms]; null for silence and for words that call it off (said so). */
    private suspend fun heard(e: Ears, ms: Long, lang: String): String? {
        e.tone()
        val said = e.listen(voice(), ms)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!VoiceAnswer.cancels(said)) return said
        mouth(line(VoiceAnswer.cancelled(lang)))
        return null
    }

    /** The loop's one run at a time: a newer one cuts an older one; [asks] while a question is being answered. */
    private fun begin(asks: Boolean, body: suspend () -> Unit) {
        loop?.cancel()
        asking = asks
        loop = scope.launch {
            try {
                body()
            } finally {
                synchronized(this@HandsFreeLoop) { if (loop == coroutineContext[Job]) asking = false }
            }
        }
    }

    private suspend fun answer(farm: String, u: Utterance, ears: Ears, what: (String) -> Heard?) {
        if (!mouth(u)) return
        val lang = voice().language
        val got = heard(ears, VoiceAnswer.LISTEN_MS, lang)?.let(what) ?: return
        mouth(line(VoiceAnswer.readBack(lang, got.words)))
        if (calledOff(ears, lang)) return
        Log.i(TAG, "${got.call.op} by voice")
        if (!send(farm, got.call)) mouth(line(VoiceAnswer.waits(lang)))
    }

    /** The read-back's while: true when what is said in it calls the answer off (and that is said). */
    private suspend fun calledOff(e: Ears, lang: String): Boolean {
        val more = e.listen(voice(), VoiceAnswer.CANCEL_MS)
        if (more == null || !VoiceAnswer.cancels(more)) return false
        mouth(line(VoiceAnswer.cancelled(lang)))
        return true
    }

    private fun line(text: String) = Utterance("handsfree-${count.incrementAndGet()}", text, interrupt = false, lang = voice().toLanguageTag())

    /** Through the phone's voice; false when it will not be said or does not end in time. */
    private suspend fun spoken(u: Utterance): Boolean {
        val end = CompletableDeferred<Boolean>()
        ending[u.id] = end
        if (!speaker.say(u)) {
            ending.remove(u.id)
            return false
        }
        return withTimeoutOrNull(SAY_MS) { end.await() } ?: false.also { ending.remove(u.id) }
    }

    private companion object {
        const val TAG = "AFHandsFree"

        /** The longest an utterance is waited for (a question is cut at Speech.MAX_CHARS). */
        const val SAY_MS = 60_000L
    }
}
