package com.muvusoft.agentfarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
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
