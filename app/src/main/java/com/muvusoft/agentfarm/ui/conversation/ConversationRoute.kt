package com.muvusoft.agentfarm.ui.conversation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.session.Answers
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.ConversationSource
import com.muvusoft.agentfarm.net.ConversationStore
import com.muvusoft.agentfarm.net.SharedImages
import com.muvusoft.agentfarm.ui.components.AskActions
import com.muvusoft.agentfarm.ui.components.ComposerActions
import com.muvusoft.agentfarm.ui.lock.Destructive
import com.muvusoft.agentfarm.ui.lock.rememberDestructiveConfirm
import com.muvusoft.agentfarm.ui.page.rememberSpeech
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A session's Conversation, fed by its ConversationSource while it is in view. Stopping the turn and answering
 * the farm's gate take the owner's confirmation; [onFarmPage] opens the farm's own session page for what the
 * native screen does not draw (its details, its menu).
 */
@Composable
fun ConversationRoute(farmId: String, key: ConversationKey, manager: ConnectionManager, store: ConversationStore, onBack: () -> Unit, onFarmPage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember(farmId, key) { ConversationSource(context.applicationContext, manager, store, farmId, key, scope) }
    val state by source.state.collectAsState()
    var draft by rememberSaveable(key.id) { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(source) { source.run() }
    // Its question is answered here while it is on screen, not by the headset mode's voice.
    DisposableEffect(key.card) {
        val loop = AgentFarmApp.of(context).handsFree
        loop.inFront = key.card
        onDispose { if (loop.inFront == key.card) loop.inFront = null }
    }
    BackHandler(onBack = onBack)
    val confirm = rememberDestructiveConfirm()
    val gateTitle = stringResource(R.string.share_confirm_title)
    val gateVerb = stringResource(R.string.share_confirm)
    val stopTitle = stringResource(R.string.stop_confirm_title, state.agent)
    val stopDetail = stringResource(R.string.stop_confirm_detail)
    val stopVerb = stringResource(R.string.stop_go)
    val unavailable = stringResource(R.string.voice_unavailable)
    val speech = rememberSpeech { heard ->
        heard?.firstOrNull { it.isNotBlank() }?.let { words -> draft = if (draft.isBlank()) words else "$draft $words" }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            source.send(draft, image = SharedImages.describe(context, uri, context.contentResolver.getType(uri) ?: "image/*"))
            draft = ""
        }
    }
    // The farm's gate stopped a send: its words come back to the box, and the yes sends them naming the gate.
    val gate = state.gate
    LaunchedEffect(gate) {
        if (gate == null) return@LaunchedEffect
        if (draft.isBlank()) draft = gate.text
        confirm(Destructive(title = gateTitle, detail = gate.sentence, verb = gateVerb, run = { draft = ""; source.send(gate.text, gate.gate) }))
    }
    ConversationScreen(
        state = state,
        now = now,
        draft = draft,
        actions = ConversationActions(
            onBack = onBack,
            onDetails = onFarmPage,
            onStop = { confirm(Destructive(title = stopTitle, detail = stopDetail, verb = stopVerb, run = { scope.launch { source.stop() } })) },
            onOverflow = onFarmPage,
            onOlder = { scope.launch { source.older() } },
            onRetry = { scope.launch { source.fresh() } },
            onShowAll = { m -> scope.launch { source.showAll(m) } },
            ask = AskActions(
                onAnswer = { picked ->
                    val ask = state.ask
                    val card = key.card
                    if (ask != null && card != null) Answers.questions(card, ask, picked)?.let(source::answer)
                },
                onFree = { text -> key.card?.let { source.answer(Answers.free(it, text)) } },
                onAllow = { scope.launch { source.permissionKey()?.let { source.answer(Answers.need(it, Answers.ALLOW)) } } },
                onDeny = { scope.launch { source.permissionKey()?.let { source.answer(Answers.need(it, Answers.DENY)) } } },
            ),
            composer = ComposerActions(
                onText = {
                    draft = it
                    source.dropGate()
                },
                onSend = {
                    source.send(draft)
                    draft = ""
                },
                onAttach = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDictate = { if (!speech.start()) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() },
            ),
        ),
    )
}
