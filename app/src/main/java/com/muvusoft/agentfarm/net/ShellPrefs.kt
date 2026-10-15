package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.WakeNic
import kotlinx.serialization.builtins.ListSerializer
import com.muvusoft.agentfarm.core.speech.SpeakMode
import com.muvusoft.agentfarm.core.speech.VoiceLanguage

/** The shell's own device settings, kept on this phone only. */
class ShellPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shell", Context.MODE_PRIVATE)

    var lockOnOpen: Boolean
        get() = prefs.getBoolean(LOCK_ON_OPEN, false)
        set(value) = prefs.edit().putBoolean(LOCK_ON_OPEN, value).apply()

    var speakMode: SpeakMode
        get() = SpeakMode.of(prefs.getString(SPEAK_MODE, null))
        set(value) = prefs.edit().putString(SPEAK_MODE, value.key).apply()

    /** A BCP 47 tag, or "" to follow the phone's language (core/speech/VoiceLanguage). */
    var voiceLanguage: String
        get() = prefs.getString(VOICE_LANGUAGE, null) ?: VoiceLanguage.PHONE
        set(value) = prefs.edit().putString(VOICE_LANGUAGE, value).apply()

    /** The network cards a farm named in its last welcome, kept for waking its PC while it sleeps. */
    fun wake(farmId: String): List<WakeNic> =
        prefs.getString(WAKE + farmId, null)?.let { runCatching { Codec.json.decodeFromString(NICS, it) }.getOrNull() }.orEmpty()

    /** An empty list forgets them (the farm was forgotten); a welcome without cards keeps the last ones. */
    fun setWake(farmId: String, nics: List<WakeNic>) {
        val e = prefs.edit()
        if (nics.isEmpty()) e.remove(WAKE + farmId) else e.putString(WAKE + farmId, Codec.json.encodeToString(NICS, nics))
        e.apply()
    }

    private companion object {
        val NICS = ListSerializer(WakeNic.serializer())
        const val WAKE = "wake."
        const val LOCK_ON_OPEN = "lockOnOpen"
        const val SPEAK_MODE = "speakMode"
        const val VOICE_LANGUAGE = "voiceLanguage"
    }
}
