package com.muvusoft.agentfarm.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.handsfree.HandsFree
import com.muvusoft.agentfarm.core.handsfree.HandsFreeState
import com.muvusoft.agentfarm.net.Headset

/**
 * The headset mode's switch: turning it on asks for the microphone first, and a refusal leaves it off. The line
 * under it says where the mode stands (the permission, the headset) as the headset comes and goes.
 */
@Composable
fun HandsFreeRow(on: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var microphone by remember { mutableStateOf(Headset.microphone(context)) }
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphone = granted
        refused = !granted
        if (granted) onChange(true)
    }
    val headset by remember { Headset.watch(context) }.collectAsState(initial = Headset.on(context))
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = on,
                role = Role.Switch,
                onValueChange = { wanted ->
                    refused = false
                    microphone = Headset.microphone(context)
                    if (wanted && !microphone) ask.launch(Manifest.permission.RECORD_AUDIO) else onChange(wanted)
                },
            ).testTag("handsfree-switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.handsfree), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.handsfree_detail), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = on, onCheckedChange = null)
        }
        val line = when (HandsFree.state(on, microphone, headset)) {
            HandsFreeState.OFF -> if (refused) R.string.handsfree_needs_microphone else null
            HandsFreeState.NEEDS_MICROPHONE -> R.string.handsfree_needs_microphone
            HandsFreeState.NO_HEADSET -> R.string.handsfree_no_headset
            HandsFreeState.READY -> R.string.handsfree_ready
        }
        line?.let { Text(stringResource(it), Modifier.padding(top = 4.dp).testTag("handsfree-state"), style = MaterialTheme.typography.bodySmall) }
        if (on) TurnsRow()
    }
}

/** Whether the mode also listens after a finished turn, for that session's next instruction (off: questions only). */
@Composable
private fun TurnsRow() {
    val prefs = AgentFarmApp.of(LocalContext.current).prefs
    var turns by remember { mutableStateOf(prefs.handsFreeTurns) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp).toggleable(
            value = turns,
            role = Role.Switch,
            onValueChange = { prefs.handsFreeTurns = it; turns = it },
        ).testTag("handsfree-turns"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.handsfree_turns), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.handsfree_turns_detail), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = turns, onCheckedChange = null)
    }
}
