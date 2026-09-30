package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.speech.SpeakMode

/** The shell's own device settings, kept on this phone only. */
class ShellPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shell", Context.MODE_PRIVATE)

    var lockOnOpen: Boolean
        get() = prefs.getBoolean(LOCK_ON_OPEN, false)
        set(value) = prefs.edit().putBoolean(LOCK_ON_OPEN, value).apply()

    var speakMode: SpeakMode
        get() = SpeakMode.of(prefs.getString(SPEAK_MODE, null))
        set(value) = prefs.edit().putString(SPEAK_MODE, value.key).apply()

    private companion object {
        const val LOCK_ON_OPEN = "lockOnOpen"
        const val SPEAK_MODE = "speakMode"
    }
}
