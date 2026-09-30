package com.muvusoft.agentfarm.ui.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.muvusoft.agentfarm.core.lock.LockPolicy

/** A destructive action waiting for its confirmation. `verb` names the confirm button (1-2 words). */
data class Destructive(val title: String, val detail: String, val verb: String, val run: () -> Unit)

/**
 * Returns the one door every destructive action goes through: the owner's biometric or screen lock,
 * or a native dialog on a phone that has neither. The dialog is drawn where this is called.
 */
@Composable
fun rememberDestructiveConfirm(): (Destructive) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Destructive?>(null) }
    pending?.let { d ->
        AlertDialog(
            onDismissRequest = { pending = null },
            modifier = Modifier.testTag("destructive-dialog"),
            title = { Text(d.title) },
            text = { Text(d.detail) },
            confirmButton = { TextButton(onClick = { pending = null; d.run() }) { Text(d.verb) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Vazgeç") } },
        )
    }
    return remember(context) {
        { d: Destructive ->
            when (LockPolicy.confirm(OwnerCheck.availability(context))) {
                LockPolicy.Confirm.OWNER -> OwnerCheck.ask(context, d.title, d.detail) { ok -> if (ok) d.run() }
                LockPolicy.Confirm.DIALOG -> { pending = d }
            }
        }
    }
}
