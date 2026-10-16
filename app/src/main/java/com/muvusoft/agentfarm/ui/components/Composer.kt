package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.conversation.ComposerMode
import com.muvusoft.agentfarm.ui.theme.LocalStatusColors

class ComposerActions(
    val onText: (String) -> Unit,
    val onSend: () -> Unit,
    val onAttach: () -> Unit,
    val onDictate: () -> Unit,
)

/**
 * The bottom of a conversation. While a turn runs on an engine that cannot be steered, it says the message
 * goes when the turn ends; a session this phone cannot write to says why in its place.
 */
@Composable
fun Composer(mode: ComposerMode, readOnly: String?, text: String, actions: ComposerActions, canSend: Boolean = true) {
    if (mode == ComposerMode.READ_ONLY) {
        Surface(Modifier.fillMaxWidth().testTag("read-only"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.outline)
                Text(readOnly.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Surface(Modifier.fillMaxWidth().testTag("composer"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (mode == ComposerMode.AFTER_TURN) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp).testTag("after-turn"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(LocalStatusColors.current.waiting)
                    Text(stringResource(R.string.after_turn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onAttach) { Icon(Icons.Filled.Add, stringResource(R.string.attach)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = actions.onText,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.message_hint)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                )
                IconButton(onClick = actions.onDictate) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.dictate)) }
                FilledIconButton(onClick = actions.onSend, enabled = canSend && text.isNotBlank(), modifier = Modifier.testTag("composer-send")) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                }
            }
        }
    }
}

/** A typed answer to a question, when no option says it. */
@Composable
fun FreeAnswer(onFree: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).testTag("free-answer"),
            placeholder = { Text(stringResource(R.string.answer_hint)) },
            maxLines = 3,
            shape = RoundedCornerShape(12.dp),
        )
        IconButton(onClick = { onFree(text); text = "" }, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
        }
    }
}
