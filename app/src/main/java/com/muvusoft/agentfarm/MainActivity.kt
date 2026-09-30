package com.muvusoft.agentfarm

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.ui.AppTheme
import com.muvusoft.agentfarm.ui.ShellApp

/** A FragmentActivity because the system owner prompt (biometric or screen lock) needs one. */
class MainActivity : FragmentActivity() {
    private val incomingLink = mutableStateOf<String?>(null)
    private val incomingShare = mutableStateOf<String?>(null)
    private val openNow = mutableStateOf(false)

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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        if (intent?.getBooleanExtra(OPEN_NOW, false) == true) {
            openNow.value = true
        } else if (intent?.action == Intent.ACTION_SEND) {
            incomingShare.value = Share.text(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            incomingLink.value = intent?.dataString
        }
    }

    companion object {
        /** An intent extra: open the focused farm's Now page (the widget and the quick settings tile). */
        const val OPEN_NOW = "openNow"
    }
}
