package com.muvusoft.agentfarm.net

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.muvusoft.agentfarm.core.speech.SpokenLog
import com.muvusoft.agentfarm.core.speech.Utterance
import java.util.Locale

/**
 * Reads utterances aloud with the phone's own text-to-speech engine, in the voice language in effect
 * (`language`, asked before every utterance so a change in Settings applies to the next one). The engine
 * starts on the first utterance (nothing is loaded for a user who never turns reading on); what arrives
 * while it starts waits. Speech goes out as a notification sound, so Do Not Disturb silences it like the rest.
 */
class Speaker(private val context: Context, private val language: () -> Locale) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var usable = true
    private var applied: Locale? = null
    private val waiting = mutableListOf<Utterance>()
    private val log = SpokenLog()

    // Alerts arrive on the socket's coroutine, the engine answers on the main thread: one lock for both.
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

    @Synchronized
    fun shutdown() {
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
            return
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        ready = true
        val queued = waiting.toList().also { waiting.clear() }
        if (apply()) queued.forEach(::speak)
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

    private fun speak(u: Utterance) {
        val mode = if (u.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(u.text, mode, null, u.id)
    }

    private companion object {
        const val TAG = "AFSpeak"
    }
}
