package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.view.ShellRequest
import com.muvusoft.agentfarm.net.SharedImages
import com.muvusoft.agentfarm.ui.page.dictationLanguage
import com.muvusoft.agentfarm.ui.page.speechIntent
import com.muvusoft.agentfarm.ui.AppTheme
import com.muvusoft.agentfarm.ui.ShellApp

/** A FragmentActivity because the system owner prompt (biometric or screen lock) needs one. */
class MainActivity : FragmentActivity() {
    private val incomingLink = mutableStateOf<String?>(null)
    private val incomingShare = mutableStateOf<Shared?>(null)
    private val openNow = mutableStateOf(false)
    private val openStop = mutableStateOf(false)

    // The widget's microphone: what the recogniser heard goes to the share screen, which says it into a session.
    private val dictate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val results = if (r.resultCode == RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) else null
        ShellRequest.heard(results)?.let { incomingShare.value = Shared(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        take(intent)
        val app = AgentFarmApp.of(this)
        setContent {
            AppTheme {
                ShellApp(
                    versionName = BuildConfig.VERSION_NAME, store = app.store, prefs = app.prefs, manager = app.manager,
                    incomingLink = incomingLink.value,
                    incomingShare = incomingShare.value,
                    onShareDone = { incomingShare.value = null },
                    openNow = openNow.value,
                    onOpenNowDone = { openNow.value = false },
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
            openNow.value = true
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

        /** An intent extra: dictate, then offer the words to a session (the widget's microphone). */
        const val OPEN_VOICE = "openVoice"

        /** An intent extra: offer the running turns to stop (the widget's stop). */
        const val OPEN_STOP = "openStop"
    }
}
