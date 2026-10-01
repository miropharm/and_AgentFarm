package com.muvusoft.agentfarm.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.buildLabel
import com.muvusoft.agentfarm.core.lock.LockPolicy
import com.muvusoft.agentfarm.core.speech.SpeakMode

/** This phone's own settings. `onLockToggle` receives the wanted value; the caller asks the owner before storing it. */
@Composable
fun SettingsScreen(
    versionName: String,
    lockOnOpen: Boolean,
    availability: LockPolicy.Availability,
    onLockToggle: (Boolean) -> Unit,
    speakMode: SpeakMode,
    onSpeakMode: (SpeakMode) -> Unit,
    voiceLanguage: String,
    onVoiceLanguage: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val reason = LockPolicy.reason(availability)
    Surface(Modifier.fillMaxSize().testTag("settings")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            Section(stringResource(R.string.settings_lock))
            // One toggle for TalkBack: the label, its detail and the state are read together, and the whole row takes the tap.
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = lockOnOpen,
                    enabled = reason == null,
                    role = Role.Switch,
                    onValueChange = onLockToggle,
                ).testTag("lock-switch"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lock_on_open), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.lock_on_open_detail), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = lockOnOpen,
                    onCheckedChange = null,
                    enabled = reason == null,
                )
            }
            if (reason != null) {
                Text(reason, Modifier.padding(top = 4.dp).testTag("lock-reason"), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(if (reason == null) R.string.lock_destructive_asks else R.string.lock_destructive_confirms),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Section(stringResource(R.string.settings_speech))
            SpeakChoice(speakMode, onSpeakMode)
            VoiceLanguageRow(chosen = voiceLanguage, onChoose = onVoiceLanguage)
            Section(stringResource(R.string.settings_battery))
            BatteryRow()
            Section(stringResource(R.string.settings_device))
            Text(stringResource(R.string.device_line, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE), style = MaterialTheme.typography.bodyMedium)
            Text(buildLabel(versionName), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}
