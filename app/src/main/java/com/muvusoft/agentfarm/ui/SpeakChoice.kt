package com.muvusoft.agentfarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.speech.SpeakMode

private val LABEL = mapOf(
    SpeakMode.OFF to R.string.speak_off,
    SpeakMode.ASKS to R.string.speak_asks,
    SpeakMode.ASKS_AND_TURNS to R.string.speak_asks_and_turns,
)

/** Exactly one of three: radio rows, each row the whole tap target and one TalkBack node. */
@Composable
fun SpeakChoice(mode: SpeakMode, onMode: (SpeakMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        SpeakMode.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = m == mode, role = Role.RadioButton, onClick = { onMode(m) })
                    .testTag("speak-${m.key}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = m == mode, onClick = null)
                Text(stringResource(LABEL.getValue(m)), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(R.string.speak_detail), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
    }
}
