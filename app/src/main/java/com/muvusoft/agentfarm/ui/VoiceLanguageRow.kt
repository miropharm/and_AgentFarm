package com.muvusoft.agentfarm.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.speech.VoiceLanguage
import com.muvusoft.agentfarm.net.phoneLocale
import java.util.Locale

/**
 * The voice language (read aloud and dictation). MIRROR: the row shows the language in effect, and
 * "Phone language" always says what it resolves to. A language this phone has no voice for is said on
 * screen, under the row, because nothing will be read aloud until a voice is added.
 */
@Composable
fun VoiceLanguageRow(chosen: String, onChoose: (String) -> Unit) {
    val phone = remember { phoneLocale() }
    val tag = VoiceLanguage.tag(chosen, phone)
    var picking by remember { mutableStateOf(false) }
    val value = if (chosen.isBlank()) stringResource(R.string.voice_language_phone_value, VoiceLanguage.name(tag))
    else VoiceLanguage.name(tag)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)
            .clickable(onClickLabel = stringResource(R.string.voice_language_change)) { picking = true }
            .testTag("voice-language"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.voice_language), style = MaterialTheme.typography.bodyLarge)
            Text(value, Modifier.testTag("voice-language-value"), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (!voiceAvailable(tag)) {
        Text(
            stringResource(R.string.voice_missing, VoiceLanguage.name(tag)),
            Modifier.padding(top = 4.dp).testTag("voice-missing"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (picking) {
        LanguageDialog(chosen = chosen, phoneName = VoiceLanguage.name(phone.toLanguageTag()), onPick = {
            onChoose(it)
            picking = false
        }, onDismiss = { picking = false })
    }
}

@Composable
private fun LanguageDialog(chosen: String, phoneName: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val current = chosen.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.voice_language)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp).selectableGroup().testTag("voice-language-list")) {
                items(VoiceLanguage.choices(current)) { t ->
                    val label = if (t == VoiceLanguage.PHONE) stringResource(R.string.voice_language_phone_value, phoneName)
                    else VoiceLanguage.name(t)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = t == current, role = Role.RadioButton, onClick = { onPick(t) })
                            .testTag("voice-lang-" + t.ifEmpty { "phone" }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = t == current, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}

/** Whether this phone's text-to-speech can say the language; true until the engine has answered. */
@Composable
private fun voiceAvailable(tag: String): Boolean {
    val context = LocalContext.current
    var available by remember(tag) { mutableStateOf(true) }
    DisposableEffect(tag) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val r = if (status == TextToSpeech.SUCCESS) engine?.isLanguageAvailable(Locale.forLanguageTag(tag)) ?: TextToSpeech.ERROR
            else TextToSpeech.ERROR
            available = r >= TextToSpeech.LANG_AVAILABLE
        }
        onDispose { engine?.shutdown() }
    }
    return available
}
