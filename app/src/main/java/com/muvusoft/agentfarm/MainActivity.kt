package com.muvusoft.agentfarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.view.NowRequest
import com.muvusoft.agentfarm.core.view.ShellRequest
import com.muvusoft.agentfarm.net.SharedImages
import com.muvusoft.agentfarm.net.drawAlike
import com.muvusoft.agentfarm.net.languageOverride
import com.muvusoft.agentfarm.ui.page.dictationLanguage
import com.muvusoft.agentfarm.ui.page.speechIntent
import com.muvusoft.agentfarm.ui.theme.AppTheme
import com.muvusoft.agentfarm.ui.ShellApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A FragmentActivity because the system owner prompt (biometric or screen lock) needs one. */
class MainActivity : FragmentActivity() {
    private val incomingLink = mutableStateOf<String?>(null)
    private val incomingShare = mutableStateOf<Shared?>(null)
    private val openNow = mutableStateOf<NowRequest?>(null)
    private val openStop = mutableStateOf(false)

    // The widget's microphone: what the recogniser heard goes to the share screen, which says it into a session.
    private val dictate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val results = if (r.resultCode == RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) else null
        ShellRequest.heard(results)?.let { incomingShare.value = Shared(it) }
    }

    private var drawnIn: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        drawnIn = AgentFarmApp.of(newBase).language.value
        languageOverride(drawnIn)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        take(intent)
        val app = AgentFarmApp.of(this)
        // A farm told another language: the screens are drawn again in it, their saved state carried across.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { app.language.first { !drawAlike(it, drawnIn) }; recreate() } }
        setContent {
            AppTheme {
                ShellApp(
                    versionName = BuildConfig.VERSION_NAME, store = app.store, prefs = app.prefs, manager = app.manager,
                    incomingLink = incomingLink.value,
                    incomingShare = incomingShare.value,
                    onShareDone = { incomingShare.value = null },
                    openNow = openNow.value,
                    onOpenNowDone = { openNow.value = null },
                    openStop = openStop.value,
                    onStopDone = { openStop.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        if (intent?.getBooleanExtra(OPEN_VOICE, false) == true) {
            intent.removeExtra(OPEN_VOICE)
            runCatching { dictate.launch(speechIntent(getString(R.string.voice_prompt), dictationLanguage(AgentFarmApp.of(this).prefs))) }
        } else if (intent?.getBooleanExtra(OPEN_NOW, false) == true) {
            openNow.value = NowRequest(intent.getStringExtra(OPEN_FARM), intent.getStringExtra(OPEN_SESSION))
        } else if (intent?.getBooleanExtra(OPEN_STOP, false) == true) {
            intent.removeExtra(OPEN_STOP)
            openStop.value = true
        } else if (intent?.action == Intent.ACTION_SEND) {
            val image = intent.type?.takeIf { it.startsWith("image/") }?.let { mime ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { SharedImages.describe(this, it, mime) }
            }
            incomingShare.value = Shared.of(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT), image)
        } else {
            incomingLink.value = intent?.dataString
        }
    }

    companion object {
        /** An intent extra: open the focused farm's Now page (the widget and the quick settings tile). */
        const val OPEN_NOW = "openNow"

        /** With OPEN_NOW, from a notification: the farm whose Now opens, and the Console card whose Conversation opens over it. */
        const val OPEN_FARM = "openFarm"
        const val OPEN_SESSION = "openSession"

        /** An intent extra: dictate, then offer the words to a session (the widget's microphone). */
        const val OPEN_VOICE = "openVoice"

        /** An intent extra: offer the running turns to stop (the widget's stop). */
        const val OPEN_STOP = "openStop"
    }
}
