package com.muvusoft.agentfarm.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.muvusoft.agentfarm.core.power.BatteryPolicy

/** Battery-optimization state, re-read on every resume so it follows what the user changed in system settings. */
@Composable
fun BatteryRow() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isExempt(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = isExempt(context)
        onPauseOrDispose { }
    }
    val line = BatteryPolicy.line(exempt)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.state, Modifier.testTag("battery-state"), style = MaterialTheme.typography.bodyLarge)
            Text(line.detail, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { openSettings(context) }, modifier = Modifier.testTag("battery-open")) {
            Text(line.action)
        }
    }
}

private fun isExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

// Some vendor builds drop the optimization list; the app's own info page is the next door to it.
private fun openSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}
