package com.muvusoft.agentfarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.ShareTarget
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.request
import com.muvusoft.agentfarm.ui.components.AFTip
import com.muvusoft.agentfarm.ui.lock.Destructive
import com.muvusoft.agentfarm.ui.lock.rememberDestructiveConfirm
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A send the farm stopped at a gate: the yes goes back to the same farm and session, never to a new pick. */
private data class Asked(val farm: String, val target: String, val gate: String)

/**
 * A text shared from another app, said into a running Console session with `console.send`. Only an
 * online farm is offered; the sessions come from `console.sessions`, one waiting on you first.
 * The farm's remote gate (today's budget, a command that cannot be undone) may stop it: the farm's
 * sentence is shown and "Send anyway" asks the owner before the same text goes again with the yes.
 */
@Composable
fun ShareScreen(text: String, manager: ConnectionManager, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val shell by manager.state.collectAsState()
    val online = shell.farms.filter { it.link is Link.Online }.map { it.farm }
    // Derived, not remembered: a farm that comes online while this is open is offered at once.
    var picked by remember { mutableStateOf<String?>(null) }
    val farmId = listOfNotNull(picked, shell.focused).firstOrNull { f -> online.any { it.id == f } } ?: online.firstOrNull()?.id
    var targets by remember { mutableStateOf<List<ShareTarget>?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var asked by remember { mutableStateOf<Asked?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val confirm = rememberDestructiveConfirm()
    val confirmTitle = stringResource(R.string.share_confirm_title)
    val confirmVerb = stringResource(R.string.share_confirm)

    // Each send is a new call; a yes is the same text again with the gate the farm named.
    fun send(farm: String, to: String, gate: String?) {
        busy = true
        scope.launch {
            val args = buildJsonObject {
                put("id", JsonPrimitive(to))
                put("text", JsonPrimitive(text))
                if (gate != null) put("confirm", JsonPrimitive(gate))
            }
            val r = manager.request(farm, "console.send", args)
            outcome = Share.outcome(r?.ok == true, r?.result, r?.error)
            asked = Share.asks(r?.ok == true, r?.result)?.let { Asked(farm, to, it) }
            busy = false
        }
    }

    // A different pick starts over: a yes given for one session is never spent on another.
    fun startOver() {
        if (asked != null) {
            asked = null
            outcome = null
        }
    }

    LaunchedEffect(farmId) {
        targets = null
        val f = farmId ?: return@LaunchedEffect
        val r = manager.request(f, "console.sessions")
        targets = if (r?.ok == true) Share.targets(r.result) else emptyList()
        target = targets?.firstOrNull()?.id
    }

    Surface(Modifier.fillMaxSize().testTag("share")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.share_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Preview(text)
            if (online.isEmpty()) {
                Text(stringResource(R.string.share_no_farm), Modifier.padding(top = 16.dp).testTag("share-no-farm"))
            }
            if (online.size > 1) {
                Section(stringResource(R.string.share_farm))
                Choices(online.map { it.id to it.name }, farmId, "share-farm") { picked = it; startOver() }
            }
            val list = targets
            if (farmId != null) {
                Section(stringResource(R.string.share_session))
                when {
                    list == null -> Text(stringResource(R.string.share_loading))
                    list.isEmpty() -> Text(stringResource(R.string.share_no_session), Modifier.testTag("share-no-session"))
                    else -> Choices(list.map { it.id to "${it.agent} · ${Share.statusWord(it)}" }, target, "share-to") { target = it; startOver() }
                }
            }
            outcome?.let { Text(it, Modifier.padding(top = 12.dp).testTag("share-outcome"), color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone, modifier = Modifier.testTag("share-close")) {
                    Text(stringResource(if (outcome == null || asked != null) R.string.cancel else R.string.close))
                }
                val f = farmId
                val t = target
                val a = asked
                Button(
                    onClick = {
                        when {
                            a != null -> confirm(
                                Destructive(
                                    title = confirmTitle,
                                    detail = outcome.orEmpty(),
                                    verb = confirmVerb,
                                    run = { send(a.farm, a.target, a.gate) },
                                ),
                            )
                            f != null && t != null -> send(f, t, null)
                            else -> Unit
                        }
                    },
                    enabled = !busy && (a != null || (f != null && t != null && outcome == null)),
                    modifier = Modifier.padding(start = 8.dp).testTag("share-send"),
                ) { Text(stringResource(if (a != null) R.string.share_confirm else R.string.share_send)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Preview(text: String) {
    var whole by remember { mutableStateOf(false) }
    if (whole) AFTip(listOf(text), onDismiss = { whole = false })
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            Share.preview(text),
            Modifier.combinedClickable(
                onClickLabel = stringResource(R.string.share_whole),
                onClick = { whole = true },
                onLongClick = { whole = true },
            ).padding(12.dp).testTag("share-preview"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Choices(rows: List<Pair<String, String>>, chosen: String?, tag: String, onChoose: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        rows.forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = id == chosen, role = Role.RadioButton, onClick = { onChoose(id) })
                    .testTag("$tag-$id"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = id == chosen, onClick = null)
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
    }
}
