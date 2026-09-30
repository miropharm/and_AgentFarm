package com.muvusoft.agentfarm.ui.page

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.view.ShellRequest
import kotlinx.serialization.json.JsonElement

/** Whether this phone can dictate, and the one way a page's `afVoice` starts it. */
class Dictation internal constructor(val available: Boolean, private val launch: (JsonElement) -> Unit) {
    fun start(token: JsonElement) = launch(token)
}

/**
 * Dictation through the system's speech recogniser (RECOGNIZE_SPEECH, Turkish): whichever app the phone
 * answers it with — Google's, or TalkScribe once it takes that action — hears the words, and the page's
 * `afVoiceDone` carries the first real result. A cancel answers with no text; a phone with no recogniser
 * answers with the reason. One dictation at a time: a second request answers the first with nothing.
 */
@Composable
fun rememberDictation(deliver: (String) -> Unit): Dictation {
    val context = LocalContext.current
    val send by rememberUpdatedState(deliver)
    val unavailable = stringResource(R.string.voice_unavailable)
    val prompt = stringResource(R.string.voice_prompt)
    val waiting = remember { arrayOfNulls<JsonElement>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val token = waiting[0] ?: return@rememberLauncherForActivityResult
        waiting[0] = null
        val results = if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) else null
        send(ShellRequest.voiceDone(token, results).toString())
    }
    val intent = remember(prompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, ShellRequest.VOICE_LANGUAGE)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    return remember(launcher, intent, available) {
        Dictation(available) { token ->
            waiting[0]?.let { send(ShellRequest.voiceDone(it, null).toString()) }
            waiting[0] = token
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                waiting[0] = null
                send(ShellRequest.voiceDone(token, null, unavailable).toString())
            }
        }
    }
}
