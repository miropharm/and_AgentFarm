package com.muvusoft.agentfarm.net

import android.util.Log
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.handsfree.Heard
import com.muvusoft.agentfarm.core.handsfree.VoiceAnswer
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
 * Conversation is on screen is answered there, not by voice.
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
            loop?.cancel()
            asking = event is AskOpened
            loop = scope.launch {
                try {
                    answer(farm, u, e) { said ->
                        if (event is AskOpened) VoiceAnswer.heard(card, event.question, event.options.map { it.label }, said) else VoiceAnswer.instruction(card, said)
                    }
                } finally {
                    synchronized(this@HandsFreeLoop) { if (loop == coroutineContext[Job]) asking = false }
                }
            }
        }
        return true
    }

    private suspend fun answer(farm: String, u: Utterance, ears: Ears, what: (String) -> Heard?) {
        if (!mouth(u)) return
        ears.tone()
        val said = ears.listen(voice(), VoiceAnswer.LISTEN_MS) ?: return
        val lang = voice().language
        if (VoiceAnswer.cancels(said)) {
            mouth(line(VoiceAnswer.cancelled(lang)))
            return
        }
        val heard = what(said) ?: return
        mouth(line(VoiceAnswer.readBack(lang, heard.words)))
        val more = ears.listen(voice(), VoiceAnswer.CANCEL_MS)
        if (more != null && VoiceAnswer.cancels(more)) {
            mouth(line(VoiceAnswer.cancelled(lang)))
            return
        }
        Log.i(TAG, "${heard.call.op} by voice")
        if (!send(farm, heard.call)) mouth(line(VoiceAnswer.waits(lang)))
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
