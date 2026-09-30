package com.muvusoft.agentfarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.ui.AppTheme
import com.muvusoft.agentfarm.ui.ShellApp

class MainActivity : ComponentActivity() {
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingLink.value = intent?.dataString
        val store = FarmStore(applicationContext)
        val manager = ConnectionManager(lifecycleScope)
        setContent {
            AppTheme {
                ShellApp(versionName = BuildConfig.VERSION_NAME, store = store, manager = manager, incomingLink = incomingLink.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingLink.value = intent.dataString
    }
}
