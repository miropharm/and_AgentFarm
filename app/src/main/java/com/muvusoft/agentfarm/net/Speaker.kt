package com.muvusoft.agentfarm.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.muvusoft.agentfarm.core.speech.PageReading
import com.muvusoft.agentfarm.core.speech.ReadingAsk
import com.muvusoft.agentfarm.core.speech.ReadingAt
import com.muvusoft.agentfarm.core.speech.ReadingControl
import com.muvusoft.agentfarm.core.speech.ReadingMove
import com.muvusoft.agentfarm.core.speech.ReadingPiece
import com.muvusoft.agentfarm.core.speech.SpokenLog
import com.muvusoft.agentfarm.core.speech.Utterance
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/**
 * The phone's one voice: alerts read aloud (S-2) and a farm page's text the user asked for (S-5), on one
 * text-to-speech engine. The engine starts on the first utterance (nothing is loaded for a user who never
 * reads); what arrives while it starts waits.
 *
 * An alert is said in the voice language in effect (`language`, asked before every utterance so a change in
 * Settings applies to the next one) and goes out as a notification sound, so Do Not Disturb silences it like
 * the rest. A page's reading is the user's own choice: it is said in the text's language when this phone
 * has a voice for it (else the user's), goes out as media, holds audio focus while it plays and pauses when
 * a call or another player takes it. One page reading at a time: a new one cuts the old, and an alert that
 * interrupts (a question) ends it.
 */
class Speaker(private val context: Context, private val language: () -> Locale) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var usable = true
    private var applied: Locale? = null
    private val waiting = mutableListOf<Utterance>()
    private val log = SpokenLog()

    /** A page's reading: whose it is ([owner], the page's view), what it reads, where it is, whom it tells. */
    private class Reading(val owner: String, val ask: ReadingAsk, var at: ReadingAt, val tell: (JsonObject) -> Unit) {
        var last: String? = null
    }

    private var reading: Reading? = null
    private var round = 0
    private var holding = false
    private val audio: AudioManager? by lazy { context.getSystemService(AudioManager::class.java) }
    private val focus: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(MEDIA)
            .setOnAudioFocusChangeListener { change -> focusChanged(change) }
            .build()
    }

    // Alerts arrive on the socket's coroutine, pages on the WebView's bridge thread, the engine answers on its
    // own: one lock for all of them.
    @Synchronized
    fun say(u: Utterance) {
        if (!usable || !log.first(u.id)) return
        if (!ready) {
            if (u.interrupt) waiting.clear()
            waiting += u
            start()
            return
        }
        if (apply()) speak(u)
    }

    /** Read a page's text, cutting whatever this phone reads; [tell] hears every state, the last one idle. */
    @Synchronized
    fun read(owner: String, ask: ReadingAsk, tell: (JsonObject) -> Unit) {
        end()
        val at = PageReading.start(ask) ?: return tell(PageReading.state(ask.token, null))
        reading = Reading(owner, ask, at, tell)
        if (!usable) return end(PageReading.VOICE_FAILED)
        tell(PageReading.state(ask.token, at))
        if (ready) play() else start()
    }

    /** A press on the page's controls; only the page whose reading it is can steer it. */
    @Synchronized
    fun control(owner: String, c: ReadingControl) {
        val r = reading?.takeIf { it.owner == owner } ?: return
        when (val m = PageReading.move(r.at, c)) {
            ReadingMove.End -> end()
            ReadingMove.Stay -> r.tell(PageReading.state(r.ask.token, r.at))
            is ReadingMove.Halt -> halt(r, m.at)
            is ReadingMove.Play -> {
                r.at = m.at
                play()
            }
        }
    }

    /** The page is gone: its reading ends with it. */
    @Synchronized
    fun stopReading(owner: String) {
        if (reading?.owner == owner) end()
    }

    @Synchronized
    fun shutdown() {
        end(silence = false)
        tts?.shutdown()
        tts = null
        ready = false
        applied = null
        waiting.clear()
    }

    private fun start() {
        if (tts != null) return
        // The listener waits for this lock, so it always finds `tts` assigned.
        tts = TextToSpeech(context.applicationContext) { status -> synchronized(this@Speaker) { started(status) } }
    }

    private fun started(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "text-to-speech engine unusable (status=$status)")
            usable = false
            waiting.clear()
            end(PageReading.VOICE_FAILED, silence = false)
            return
        }
        engine.setOnUtteranceProgressListener(progress)
        ready = true
        val queued = waiting.toList().also { waiting.clear() }
        if (queued.isNotEmpty() && apply()) queued.forEach(::speak)
        if (reading != null) play()
    }

    /**
     * Points the voice at the language in effect. False when this phone has no voice for it: a voice that
     * cannot say the language would mangle every word, so nothing is read (Settings says why on screen).
     */
    private fun apply(): Boolean {
        val want = language()
        if (want == applied) return true
        val engine = tts ?: return false
        // setLanguage answers LANG_AVAILABLE (0) or better when the voice can say the language; negative when not.
        val r = engine.setLanguage(want)
        if (r < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "no voice for ${want.toLanguageTag()} (language=$r)")
            applied = null
            return false
        }
        applied = want
        return true
    }

    // The engine copies its language, rate and audio attributes into each request as it is made, so an alert
    // and a reading each set their own before speaking and never change what the other has queued.
    private fun speak(u: Utterance) {
        val engine = tts ?: return
        engine.setAudioAttributes(ALERT)
        engine.setSpeechRate(1f)
        val mode = if (u.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(u.text, mode, null, u.id)
    }

    /** Plays the reading from its paragraph to the end, as one queue the engine works through. */
    private fun play() {
        val r = reading ?: return
        val engine = tts?.takeIf { ready } ?: return
        val pass = ++round
        if (!hold()) return halt(r, r.at.copy(paused = true))
        // The reading leaves the engine on its own language: the next alert points it back.
        applied = null
        val voice = listOfNotNull(r.ask.lang.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag), language())
            .firstOrNull { engine.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: return end(PageReading.noVoice(r.ask.lang.ifBlank { language().toLanguageTag() }))
        Log.i(TAG, "reading ${r.at.paragraph + 1}/${r.at.count} in ${voice.toLanguageTag()}")
        engine.setAudioAttributes(MEDIA)
        engine.setSpeechRate(PageReading.speechRate(r.at.rate))
        val max = TextToSpeech.getMaxSpeechInputLength()
        r.last = null
        for (p in r.at.paragraph until r.at.count) {
            PageReading.chunks(r.ask.paragraphs[p], max).forEachIndexed { j, piece ->
                val id = ReadingPiece(pass, p, j).id
                val mode = if (r.last == null) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(piece, mode, null, id) != TextToSpeech.SUCCESS) return end(PageReading.VOICE_FAILED)
                r.last = id
            }
        }
        if (r.last == null) return end()
        r.tell(PageReading.state(r.ask.token, r.at))
    }

    /** The voice stops where it is and the reading waits there. */
    private fun halt(r: Reading, at: ReadingAt) {
        r.at = at
        round++
        tts?.stop()
        release()
        r.tell(PageReading.state(r.ask.token, r.at))
    }

    /**
     * The reading is over: its page hears it ([error] says why it could not go on). [silence] is false when
     * the engine already stopped it - an alert that cut in must not be silenced in turn.
     */
    private fun end(error: String? = null, silence: Boolean = true) {
        val r = reading ?: return
        reading = null
        round++
        if (silence) tts?.stop()
        release()
        r.tell(PageReading.state(r.ask.token, null, error))
    }

    @Synchronized
    private fun heard(id: String?, done: Boolean, failed: Boolean) {
        val r = reading ?: return
        val piece = ReadingPiece.of(id) ?: return
        if (piece.round != round) return
        when {
            failed -> end(PageReading.VOICE_FAILED)
            done -> if (id == r.last) end(silence = false)
            piece.piece == 0 && piece.paragraph != r.at.paragraph -> {
                r.at = r.at.copy(paragraph = piece.paragraph)
                r.tell(PageReading.state(r.ask.token, r.at))
            }
        }
    }

    /** Something else cut the queue (an alert that interrupts): the reading is over, without a second stop. */
    @Synchronized
    private fun cut(id: String?) {
        if (ReadingPiece.of(id)?.round == round) end(silence = false)
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = heard(utteranceId, done = false, failed = false)

        override fun onDone(utteranceId: String?) = heard(utteranceId, done = true, failed = false)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = heard(utteranceId, done = false, failed = true)

        override fun onError(utteranceId: String?, errorCode: Int) = heard(utteranceId, done = false, failed = true)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = cut(utteranceId)
    }

    private fun hold(): Boolean {
        if (!holding) holding = audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holding
    }

    private fun release() {
        if (holding) audio?.abandonAudioFocusRequest(focus)
        holding = false
    }

    /** A call or another player took the sound: the reading pauses where it is; the page's ▶ goes on. */
    @Synchronized
    private fun focusChanged(change: Int) {
        val r = reading ?: return
        if (r.at.paused) return
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) halt(r, r.at.copy(paused = true))
    }

    private companion object {
        const val TAG = "AFSpeak"
        val ALERT: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
