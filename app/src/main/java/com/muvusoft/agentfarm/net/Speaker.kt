package com.muvusoft.agentfarm.net

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.muvusoft.agentfarm.core.speech.SpokenLog
import com.muvusoft.agentfarm.core.speech.Utterance
import java.util.Locale

/**
 * Reads utterances aloud with the phone's own text-to-speech engine, in Turkish. The engine starts on
 * the first utterance (nothing is loaded for a user who never turns reading on); what arrives while it
 * starts waits. Speech goes out as a notification sound, so Do Not Disturb silences it like the rest.
 */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var usable = true
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
        speak(u)
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        waiting.clear()
    }

    private fun start() {
        if (tts != null) return
        // The listener waits for this lock, so it always finds `tts` assigned.
        tts = TextToSpeech(context.applicationContext) { status -> synchronized(this@Speaker) { started(status) } }
    }

    private fun started(status: Int) {
        val engine = tts ?: return
        // setLanguage answers LANG_AVAILABLE (0) or better when the voice can say Turkish; negative when not.
        val lang = if (status == TextToSpeech.SUCCESS) engine.setLanguage(TURKISH) else TextToSpeech.ERROR
        if (lang < TextToSpeech.LANG_AVAILABLE) {
            // A voice that cannot say Turkish would mangle every word: stay silent, say why in the log.
            Log.w(TAG, "text-to-speech unusable (status=$status, language=$lang)")
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
        waiting.toList().also { waiting.clear() }.forEach(::speak)
    }

    private fun speak(u: Utterance) {
        val mode = if (u.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(u.text, mode, null, u.id)
    }

    private companion object {
        const val TAG = "AFSpeak"
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
    }
}
