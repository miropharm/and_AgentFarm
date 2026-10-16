package com.muvusoft.agentfarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.ShareTarget
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.request
import com.muvusoft.agentfarm.ui.components.ChoiceSection
import com.muvusoft.agentfarm.ui.components.Choices
import com.muvusoft.agentfarm.ui.components.rememberSendFlow

/**
 * Words or a picture shared from another app, said into a running Console session (`console.send`) or
 * opening a new one with an agent (`console.new`, the agent's last launch setup). An online farm is
 * offered, and for words alone also one that does not answer but whose sessions are known: they then
 * wait in the outbox and a notification tells how it went. A picture goes up to the PC first, so only
 * a farm that answers takes it. The farm's remote gate (today's budget, a command that cannot be
 * undone) may stop a send: its sentence is shown and "Send anyway" asks the owner before the same
 * text goes again with the yes.
 */
@Composable
fun ShareScreen(shared: Shared, manager: ConnectionManager, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val shell by manager.state.collectAsState()
    val image = shared.image
    val offered = shell.farms.filter { it.link is Link.Online || (image == null && it.sessions != null) }
    val online = offered.filter { it.link is Link.Online }.map { it.farm.id }
    // Derived, not remembered: a farm that comes online while this is open is offered at once.
    var picked by remember { mutableStateOf<String?>(null) }
    val farmId = Share.farmFor(picked, shell.focused, online, offered.map { it.farm.id })
    val farmOnline = farmId != null && farmId in online
    var targets by remember { mutableStateOf<List<ShareTarget>?>(null) }
    var agents by remember { mutableStateOf<List<String>>(emptyList()) }
    var target by remember { mutableStateOf<String?>(null) }
    val flow = rememberSendFlow(manager)
    val outcome = flow.outcome
    val asked = flow.asked

    fun send(farm: String, to: String) = flow.send(farm, to, shared, Share.newAgent(to) ?: targets?.firstOrNull { it.id == to }?.agent ?: to)

    fun startOver() = flow.startOver()

    // A farm that does not answer offers what it last listed; once it answers, sessions and agents are read again.
    LaunchedEffect(farmId, farmOnline) {
        targets = null
        val f = farmId ?: return@LaunchedEffect
        if (!farmOnline) {
            agents = manager.state.value.farm(f)?.agents.orEmpty()
            targets = manager.state.value.farm(f)?.sessions.orEmpty()
        } else {
            val a = manager.request(f, "console.new")
            agents = if (a?.ok == true) Share.agents(a.result).also { manager.agents(f, it) } else emptyList()
            val r = manager.request(f, "console.sessions")
            targets = if (r?.ok == true) Share.targets(r.result).also { manager.sessions(f, it) } else emptyList()
        }
        val list = targets.orEmpty()
        target = target?.takeIf { t -> Share.newAgent(t)?.let { it in agents } ?: list.any { it.id == t } } ?: list.firstOrNull()?.id
    }

    Surface(Modifier.fillMaxSize().testTag("share")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.share_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            SharePreview(shared)
            if (image?.tooBig == true) {
                Text(
                    stringResource(R.string.share_image_big, (Share.IMAGE_MAX_BYTES / 1_048_576).toInt()),
                    Modifier.padding(top = 12.dp).testTag("share-image-big"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (offered.isEmpty()) {
                Text(
                    stringResource(if (image != null) R.string.share_image_online else R.string.share_no_farm),
                    Modifier.padding(top = 16.dp).testTag("share-no-farm"),
                )
            }
            // One farm that answers needs no chip; one that does not is said on its chip.
            if (offered.size > 1 || (farmId != null && !farmOnline)) {
                ChoiceSection(stringResource(R.string.share_farm))
                Choices(offered.map { it.farm.id to Share.farmLabel(it.farm.name, it.farm.id in online) }, farmId, "share-farm") {
                    picked = it
                    startOver()
                }
            }
            val list = targets
            if (farmId != null) {
                ChoiceSection(stringResource(R.string.share_session))
                when {
                    list == null -> Text(stringResource(R.string.share_loading))
                    list.isEmpty() -> Text(stringResource(R.string.share_no_session), Modifier.testTag("share-no-session"))
                    else -> Choices(list.map { it.id to "${it.agent} · ${Share.statusWord(it)}" }, target, "share-to") { target = it; startOver() }
                }
                if (list != null && agents.isNotEmpty()) {
                    ChoiceSection(stringResource(R.string.share_new))
                    Choices(agents.map { it to it }, Share.newAgent(target), "share-new") { target = Share.newTarget(it); startOver() }
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
                            a != null -> flow.again()
                            f != null && t != null -> send(f, t)
                            else -> Unit
                        }
                    },
                    enabled = !flow.busy && image?.tooBig != true && (a != null || (f != null && t != null && outcome == null)),
                    modifier = Modifier.padding(start = 8.dp).testTag("share-send"),
                ) { Text(stringResource(if (a != null) R.string.share_confirm else R.string.share_send)) }
            }
        }
    }
}
