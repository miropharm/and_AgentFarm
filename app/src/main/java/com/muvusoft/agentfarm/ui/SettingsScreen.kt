package com.muvusoft.agentfarm.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.core.buildLabel
import com.muvusoft.agentfarm.core.lock.LockPolicy

/** This phone's own settings. `onLockToggle` receives the wanted value; the caller asks the owner before storing it. */
@Composable
fun SettingsScreen(
    versionName: String,
    lockOnOpen: Boolean,
    availability: LockPolicy.Availability,
    onLockToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val reason = LockPolicy.reason(availability)
    Surface(Modifier.fillMaxSize().testTag("settings")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                }
                Text("Ayarlar", style = MaterialTheme.typography.titleLarge)
            }
            Section("Uygulama kilidi")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Açılışta kimlik sor", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Uygulama açılırken ve bir dakikadan uzun arka planda kaldıktan sonra.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = lockOnOpen,
                    onCheckedChange = onLockToggle,
                    enabled = reason == null,
                    modifier = Modifier.testTag("lock-switch"),
                )
            }
            if (reason != null) {
                Text(reason, Modifier.padding(top = 4.dp).testTag("lock-reason"), color = MaterialTheme.colorScheme.error)
            }
            Text(
                if (reason == null) "Çiftliği unutmak gibi geri alınamayan eylemler her zaman kimlik sorar."
                else "Geri alınamayan eylemler bu telefonda onay penceresiyle sorulur.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Section("Bu cihaz")
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodyMedium)
            Text(buildLabel(versionName), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
}
