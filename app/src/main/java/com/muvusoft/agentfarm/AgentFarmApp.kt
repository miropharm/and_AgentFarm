package com.muvusoft.agentfarm

import android.app.Application
import android.content.Context
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.net.OutboxStore
import com.muvusoft.agentfarm.net.ShellPrefs
import com.muvusoft.agentfarm.net.Speaker
import com.muvusoft.agentfarm.net.phoneLocale
import com.muvusoft.agentfarm.core.speech.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * One connection manager, one farm store and one voice for the whole process: the screens and the link
 * service share them (an alert and a page's reading are one channel, the newest cutting the older).
 */
class AgentFarmApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { FarmStore(this) }
    val prefs by lazy { ShellPrefs(this) }
    private val outbox by lazy { OutboxStore(this) }
    val speaker by lazy { Speaker(this) { VoiceLanguage.locale(prefs.voiceLanguage, phoneLocale()) } }
    val manager by lazy {
        ConnectionManager(scope, saved = outbox.load(System.currentTimeMillis()), save = { outbox.save(it) })
    }

    companion object {
        fun of(context: Context): AgentFarmApp = context.applicationContext as AgentFarmApp
    }
}
