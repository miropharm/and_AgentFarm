package com.muvusoft.agentfarm.net

import android.content.Context

/** The shell's own device settings, kept on this phone only. */
class ShellPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shell", Context.MODE_PRIVATE)

    var lockOnOpen: Boolean
        get() = prefs.getBoolean(LOCK_ON_OPEN, false)
        set(value) = prefs.edit().putBoolean(LOCK_ON_OPEN, value).apply()

    private companion object {
        const val LOCK_ON_OPEN = "lockOnOpen"
    }
}
