package com.muvusoft.agentfarm

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.muvusoft.agentfarm.core.handsfree.HandsFree
import com.muvusoft.agentfarm.core.handsfree.HandsFreeState
import com.muvusoft.agentfarm.core.handsfree.Presses
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.HandsFreeLoop
import com.muvusoft.agentfarm.net.Headset
import com.muvusoft.agentfarm.net.HeadsetButton
import com.muvusoft.agentfarm.net.HeadsetCommands
import com.muvusoft.agentfarm.net.RecognizerEars
import com.muvusoft.agentfarm.net.appLocale
import com.muvusoft.agentfarm.core.notify.Alerts
import com.muvusoft.agentfarm.net.AlertPoster
import com.muvusoft.agentfarm.net.AppVisible
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.net.OutboxStore
import com.muvusoft.agentfarm.net.PushWakeService
import com.muvusoft.agentfarm.net.ShellPrefs
import com.muvusoft.agentfarm.net.Speaker
import com.muvusoft.agentfarm.net.phoneLocale
import com.muvusoft.agentfarm.core.speech.VoiceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One connection manager, one farm store and one voice for the whole process: the screens and the link
 * service share them (an alert and a page's reading are one channel, the newest cutting the older).
 */
class AgentFarmApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { FarmStore(this) }
    val prefs by lazy { ShellPrefs(this) }
    private val outbox by lazy { OutboxStore(this) }
    val speaker by lazy { Speaker(this, ::voiceLocale) }
    /** The headset mode's answer loop; it listens only while the mode is ready. */
    val handsFree by lazy {
        HandsFreeLoop(scope, speaker, ::voiceLocale, visible = { visible.on.value }) { farm, call ->
            manager.call(farm, call.op, call.args)
            manager.state.value.farm(farm)?.link is Link.Online
        }.also {
            it.ready = { HandsFree.state(prefs.handsFree, Headset.microphone(this), Headset.on(this)) == HandsFreeState.READY }
            it.ears = RecognizerEars(this)
            it.turns = { prefs.handsFreeTurns }
        }
    }
    /** The headset button while the mode is on: one press says how the farm is, two open a session by voice. */
    val headsetButton by lazy {
        val commands = HeadsetCommands(scope, manager, handsFree, ::voiceLocale)
        HeadsetButton(this) { kind ->
            when (kind) {
                Presses.Kind.ONE -> commands.status()
                Presses.Kind.TWO -> commands.newSession()
            }
        }
    }
    /** Whether a screen of the app is on: a farm's link is held open while it is (core/power/LinkRest). */
    val visible = AppVisible()
    private val _language by lazy { MutableStateFlow(prefs.language()) }
    /** The app's own language (core/lang/AppLanguage), null for the phone's; follows what the farms tell. */
    val language: StateFlow<String?> get() = _language
    private lateinit var languageWatch: SharedPreferences.OnSharedPreferenceChangeListener
    val manager by lazy {
        ConnectionManager(
            scope,
            saved = outbox.load(System.currentTimeMillis()),
            save = { outbox.save(it) },
            onWake = { farm, nics -> prefs.setWake(farm, nics) },
            onReceipt = { farmName, item, result -> AlertPoster.post(this, item.farm, Alerts.receipt(item, farmName, result)) },
            pushToken = { prefs.pushToken },
            onLanguage = { farm, tag -> prefs.tellLanguage(farm, tag, System.currentTimeMillis()) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        Words.lang = appLocale(language.value).language
        languageWatch = prefs.watchLanguage {
            _language.value = prefs.language()
            Words.lang = appLocale(language.value).language
            AlertPoster.channels(this)
        }
        registerActivityLifecycleCallbacks(visible)
        PushWakeService.setUp(this)
    }

    /** The read-aloud voice's and the headset mode's language (Settings > Read aloud > Voice language). */
    private fun voiceLocale() = VoiceLanguage.locale(prefs.voiceLanguage, phoneLocale())

    /** The phone's own language changed: the core's sentences follow when no farm's language stands over it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Words.lang = appLocale(language.value).language
    }

    companion object {
        fun of(context: Context): AgentFarmApp = context.applicationContext as AgentFarmApp
    }
}
