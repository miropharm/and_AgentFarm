package com.muvusoft.agentfarm.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.core.GREETING_TITLE
import com.muvusoft.agentfarm.core.buildLabel

/**
 * The device's light or dark, live: the activity is not recreated on a theme change (configChanges uiMode,
 * so an open farm page is not reloaded), so the system bars are re-coloured here on every change.
 */
@Suppress("DEPRECATION")
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val bar = colors.background.toArgb()
        window.statusBarColor = bar
        window.navigationBarColor = bar
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The shell's title line: app name and the build it is. */
@Composable
fun ShellHeader(versionName: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(GREETING_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(buildLabel(versionName), style = MaterialTheme.typography.bodySmall)
    }
}
