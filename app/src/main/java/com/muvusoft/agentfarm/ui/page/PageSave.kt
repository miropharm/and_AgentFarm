package com.muvusoft.agentfarm.ui.page

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.view.ShellRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** The one way a page's `afSave` reaches the phone's save picker. */
class PageSave internal constructor(private val launch: (ShellRequest.Save) -> Unit) {
    fun start(ask: ShellRequest.Save) = launch(ask)
}

/** The system's own save picker for a file of [mime], proposing [name]. */
fun saveIntent(name: String, mime: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .setType(mime)
    .putExtra(Intent.EXTRA_TITLE, name)

/**
 * A file the farm made (an export) kept where the person chooses: the system's save picker
 * (CREATE_DOCUMENT) asks where, the bytes are written there off the main thread, and the page's
 * `afSaveDone` carries the name the picker kept (it may have added " (1)"). A closed picker answers
 * with nothing; one save at a time, so a second answers the first as closed.
 */
@Composable
fun rememberPageSave(deliver: (String) -> Unit): PageSave {
    val context = LocalContext.current
    val send by rememberUpdatedState(deliver)
    val damaged = stringResource(R.string.save_damaged)
    val noPicker = stringResource(R.string.save_unavailable)
    val notWritten = stringResource(R.string.save_failed)
    val scope = rememberCoroutineScope()
    val waiting = remember { arrayOfNulls<Pair<ShellRequest.Save, ByteArray>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val (ask, bytes) = waiting[0] ?: return@rememberLauncherForActivityResult
        waiting[0] = null
        val uri = r.data?.data
        if (r.resultCode != Activity.RESULT_OK || uri == null) {
            send(ShellRequest.saveDone(ask.token, null).toString())
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val answer = withContext(Dispatchers.IO) { writeSaved(context, uri, bytes, ask, notWritten) }
            send(answer.toString())
        }
    }
    return remember(launcher) {
        PageSave { ask ->
            waiting[0]?.let { send(ShellRequest.saveDone(it.first.token, null).toString()) }
            waiting[0] = null
            val bytes = ShellRequest.saveBytes(ask.data)
            if (bytes == null) {
                send(ShellRequest.saveDone(ask.token, null, damaged).toString())
                return@PageSave
            }
            waiting[0] = ask to bytes
            try {
                launcher.launch(saveIntent(ask.name, ask.mime))
            } catch (e: ActivityNotFoundException) {
                waiting[0] = null
                send(ShellRequest.saveDone(ask.token, null, noPicker).toString())
            }
        }
    }
}

/** Writes [bytes] to the chosen [uri]; the answer names what the provider calls the file, else the asked name. */
internal fun writeSaved(context: Context, uri: Uri, bytes: ByteArray, ask: ShellRequest.Save, notWritten: String): JsonObject {
    val wrote = runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
    if (!wrote) return ShellRequest.saveDone(ask.token, null, notWritten)
    val shown = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    return ShellRequest.saveDone(ask.token, shown?.takeIf { it.isNotBlank() } ?: ask.name)
}
