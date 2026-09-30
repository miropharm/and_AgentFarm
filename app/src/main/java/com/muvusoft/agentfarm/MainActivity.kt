package com.muvusoft.agentfarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.muvusoft.agentfarm.ui.AppTheme
import com.muvusoft.agentfarm.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { HomeScreen(versionName = BuildConfig.VERSION_NAME) }
        }
    }
}
