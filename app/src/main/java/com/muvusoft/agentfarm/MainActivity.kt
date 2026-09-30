package com.muvusoft.agentfarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.muvusoft.agentfarm.ui.AppTheme
import com.muvusoft.agentfarm.ui.ShellApp

class MainActivity : ComponentActivity() {
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingLink.value = intent?.dataString
        val app = AgentFarmApp.of(this)
        setContent {
            AppTheme {
                ShellApp(versionName = BuildConfig.VERSION_NAME, store = app.store, manager = app.manager, incomingLink = incomingLink.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink.value = intent.dataString
    }
}
