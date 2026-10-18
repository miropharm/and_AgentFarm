package com.muvusoft.agentfarm.net

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The headset mode's hearing: a tone, then what is said within a while. A test puts a scripted pair in its place. */
interface Ears {
    suspend fun tone()

    /** The words heard within [ms] in [lang]; null on silence, an error or when a call takes the sound. */
    suspend fun listen(lang: Locale, ms: Long): String?
}

/**
 * The phone's own recognizer (on the device where it can), through a Bluetooth headset's microphone when one is
 * there. While it listens it holds the sound alone, so music pauses; a call that takes the sound ends the listening
 * and nothing listens again until the next question. Everything is given back when it ends.
 */
class RecognizerEars(private val context: Context) : Ears {
    private val audio: AudioManager by lazy { context.getSystemService(AudioManager::class.java) }

    override suspend fun tone() {
        val beep = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        try {
            beep.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
            delay(TONE_MS + 50L)
        } finally {
            beep.release()
        }
    }

    override suspend fun listen(lang: Locale, ms: Long): String? = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "no speech recognition service on this phone")
            return@withContext null
        }
        val heard = CompletableDeferred<String?>()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(LISTENING)
            .setOnAudioFocusChangeListener { change -> if (change < 0) heard.complete(null) }
            .build()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return@withContext null
        val routed = route()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        try {
            recognizer.setRecognitionListener(Listener(heard))
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
            )
            withTimeoutOrNull(ms) { heard.await() }
        } finally {
            recognizer.destroy()
            if (routed && Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    /** A Bluetooth headset's microphone, which Android does not take by itself (Android 12+). */
    private fun route(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val headset = audio.availableCommunicationDevices.firstOrNull { it.type in BLUETOOTH } ?: return false
        return audio.setCommunicationDevice(headset)
    }

    private class Listener(private val heard: CompletableDeferred<String?>) : RecognitionListener {
        override fun onResults(results: Bundle?) {
            heard.complete(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
        }

        override fun onError(error: Int) {
            heard.complete(null)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val TAG = "AFEars"
        const val TONE_MS = 150
        const val TONE_VOLUME = 80
        val BLUETOOTH = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val LISTENING: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
