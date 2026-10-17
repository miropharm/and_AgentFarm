package com.muvusoft.agentfarm.net

import android.content.Context
import android.content.SharedPreferences
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.WakeNic
import com.muvusoft.agentfarm.core.lang.AppLanguage
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

    /** This phone's push token (net/PushWakeService), null until Firebase gave one. */
    var pushToken: String?
        get() = prefs.getString(PUSH_TOKEN, null)
        set(value) = prefs.edit().putString(PUSH_TOKEN, value).apply()

    /** The network cards a farm named in its last welcome, kept for waking its PC while it sleeps. */
    fun wake(farmId: String): List<WakeNic> =
        prefs.getString(WAKE + farmId, null)?.let { runCatching { Codec.json.decodeFromString(NICS, it) }.getOrNull() }.orEmpty()

    /** An empty list forgets them (the farm was forgotten); a welcome without cards keeps the last ones. */
    fun setWake(farmId: String, nics: List<WakeNic>) {
        val e = prefs.edit()
        if (nics.isEmpty()) e.remove(WAKE + farmId) else e.putString(WAKE + farmId, Codec.json.encodeToString(NICS, nics))
        e.apply()
    }

    /** The language the app draws in (core/lang/AppLanguage) from what the paired farms told; null for the phone's own. */
    fun language(): String? =
        AppLanguage.of(prefs.all.filterKeys { it.startsWith(TOLD) }.values.mapNotNull { AppLanguage.read(it as? String) })

    /** A farm's welcome told the language its pages are drawn in (null from a farm that does not say). */
    fun tellLanguage(farmId: String, tag: String?, now: Long) {
        val before = AppLanguage.read(prefs.getString(TOLD + farmId, null))
        val after = AppLanguage.tell(before, tag, now) ?: return
        if (after != before) prefs.edit().putString(TOLD + farmId, AppLanguage.write(after)).apply()
    }

    fun forgetLanguage(farmId: String) = prefs.edit().remove(TOLD + farmId).apply()

    /** Calls [changed] on the main thread when [language] may have changed; keep the returned listener (it is held weakly). */
    fun watchLanguage(changed: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == null || key.startsWith(TOLD)) changed() }
            .also { prefs.registerOnSharedPreferenceChangeListener(it) }

    private companion object {
        val NICS = ListSerializer(WakeNic.serializer())
        const val WAKE = "wake."
        const val TOLD = "told."
        const val LOCK_ON_OPEN = "lockOnOpen"
        const val SPEAK_MODE = "speakMode"
        const val VOICE_LANGUAGE = "voiceLanguage"
        const val PUSH_TOKEN = "pushToken"
    }
}
