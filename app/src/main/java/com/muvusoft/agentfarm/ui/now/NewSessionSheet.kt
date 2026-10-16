package com.muvusoft.agentfarm.ui.now

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.conversation.ComposerMode
import com.muvusoft.agentfarm.core.now.NewSession
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.share.SharedImage
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.SharedImages
import com.muvusoft.agentfarm.net.read
import com.muvusoft.agentfarm.ui.SharePreview
import com.muvusoft.agentfarm.ui.components.ChoiceSection
import com.muvusoft.agentfarm.ui.components.Choices
import com.muvusoft.agentfarm.ui.components.Composer
import com.muvusoft.agentfarm.ui.components.ComposerActions
import com.muvusoft.agentfarm.ui.components.SkeletonRows
import com.muvusoft.agentfarm.ui.components.rememberSendFlow
import com.muvusoft.agentfarm.ui.page.rememberSpeech

private const val OP_NEW = "console.new"

/**
 * A new session from Now: an agent (the ones used last first) and the first message, typed or dictated, with a
 * picture. It opens through the farm's console.new with the agent's last launch setup, the door a share uses;
 * the farm's refusal (no such agent, today's budget spent) is said here. A farm that does not answer takes the
 * words into the outbox, from the agents it last listed; a picture waits for a farm that answers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionSheet(farmId: String, state: NowState, manager: ConnectionManager, onOpened: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val flow = rememberSendFlow(manager)
    val online = state.link is Link.Online
    var agents by remember { mutableStateOf<List<String>?>(null) }
    var agent by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var image by remember { mutableStateOf<SharedImage?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val unavailable = stringResource(R.string.voice_unavailable)
    val speech = rememberSpeech { heard ->
        heard?.firstOrNull { it.isNotBlank() }?.let { words -> text = if (text.isBlank()) words else "$text $words" }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            image = SharedImages.describe(context, uri, context.contentResolver.getType(uri) ?: "image/*")
            flow.clear()
        }
    }
    // A farm that answers is asked for its agents; one that does not offers the ones it last listed.
    LaunchedEffect(farmId, online) {
        val listed = if (online) manager.read(farmId, OP_NEW)?.takeIf { it.ok }?.let { Share.agents(it.result) } else null
        listed?.let { manager.agents(farmId, it) }
        val list = NewSession.agents(listed ?: manager.state.value.farm(farmId)?.agents.orEmpty(), state)
        agents = list
        agent = agent?.takeIf { it in list } ?: list.firstOrNull()
    }
    LaunchedEffect(flow.delivered) { if (flow.delivered) onOpened() }

    val img = image
    val held = when {
        img == null -> null
        img.tooBig -> stringResource(R.string.share_image_big, (Share.IMAGE_MAX_BYTES / 1_048_576).toInt())
        !online -> stringResource(R.string.new_image_online)
        else -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("new-session")) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.new_session), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            ChoiceSection(stringResource(R.string.new_agent))
            val list = agents
            when {
                list == null -> SkeletonRows(1)
                list.isEmpty() -> Text(stringResource(R.string.new_no_agent), Modifier.testTag("new-no-agent"))
                else -> Choices(list.map { it to it }, agent, "new-agent") { agent = it; flow.clear() }
            }
            if (img != null) {
                SharePreview(Shared("", img))
                TextButton(onClick = { image = null; flow.clear() }, modifier = Modifier.testTag("new-drop-image")) {
                    Text(stringResource(R.string.new_drop_image))
                }
            }
            (held ?: note ?: flow.outcome)?.let {
                Text(it, Modifier.padding(vertical = 8.dp).testTag("new-outcome"), color = if (held != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            if (flow.asked != null) {
                Button(onClick = flow::again, enabled = !flow.busy, modifier = Modifier.testTag("new-confirm")) { Text(stringResource(R.string.share_confirm)) }
            }
            val to = agent
            Composer(
                mode = ComposerMode.WRITE,
                readOnly = null,
                text = text,
                canSend = to != null && held == null && !flow.busy && flow.outcome == null,
                actions = ComposerActions(
                    onText = { text = it; note = null; flow.clear() },
                    onSend = { if (to != null) flow.send(farmId, Share.newTarget(to), Shared(text.trim(), img), to) },
                    onAttach = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDictate = { note = if (speech.start()) null else unavailable },
                ),
            )
        }
    }
}
