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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.share.ShareTarget
import com.muvusoft.agentfarm.core.share.Stop
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.request
import com.muvusoft.agentfarm.ui.components.ChoiceSection
import com.muvusoft.agentfarm.ui.components.Choices
import com.muvusoft.agentfarm.ui.lock.Destructive
import com.muvusoft.agentfarm.ui.lock.rememberDestructiveConfirm
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The widget's stop: the turns running on an online farm, one picked, stopped with console.interrupt after the
 * owner's yes (a native confirmation, like every act that cannot be undone). The session survives the stop.
 */
@Composable
fun StopScreen(manager: ConnectionManager, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val shell by manager.state.collectAsState()
    val online = shell.farms.filter { it.link is Link.Online }.map { it.farm }
    var picked by remember { mutableStateOf<String?>(null) }
    val farmId = listOfNotNull(picked, shell.focused).firstOrNull { f -> online.any { it.id == f } } ?: online.firstOrNull()?.id
    var running by remember { mutableStateOf<List<ShareTarget>?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val confirm = rememberDestructiveConfirm()

    LaunchedEffect(farmId, round) {
        running = null
        val f = farmId ?: return@LaunchedEffect
        val r = manager.request(f, "console.sessions")
        running = if (r?.ok == true) Stop.running(r.result) else emptyList()
        target = running?.firstOrNull { it.id == target }?.id ?: running?.firstOrNull()?.id
    }

    fun stop(farm: String, session: String) {
        busy = true
        scope.launch {
            val r = manager.request(farm, "console.interrupt", buildJsonObject { put("id", JsonPrimitive(session)) })
            outcome = Stop.outcome(r?.ok == true, r?.result, r?.error)
            busy = false
            round++
        }
    }

    Surface(Modifier.fillMaxSize().testTag("stop")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.stop_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (online.isEmpty()) Text(stringResource(R.string.stop_no_farm), Modifier.padding(top = 16.dp).testTag("stop-no-farm"))
            if (online.size > 1) {
                ChoiceSection(stringResource(R.string.share_farm))
                Choices(online.map { it.id to it.name }, farmId, "stop-farm") { picked = it; outcome = null }
            }
            val list = running
            if (farmId != null) {
                ChoiceSection(stringResource(R.string.share_session))
                when {
                    list == null -> Text(stringResource(R.string.share_loading))
                    list.isEmpty() -> Text(stringResource(R.string.stop_none), Modifier.testTag("stop-none"))
                    else -> Choices(list.map { it.id to it.agent }, target, "stop-to") { target = it; outcome = null }
                }
            }
            outcome?.let { Text(it, Modifier.padding(top = 12.dp).testTag("stop-outcome"), color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone, modifier = Modifier.testTag("stop-close")) { Text(stringResource(R.string.close)) }
                val f = farmId
                val t = list?.firstOrNull { it.id == target }
                Button(
                    onClick = {
                        if (f != null && t != null) {
                            confirm(
                                Destructive(
                                    title = context.getString(R.string.stop_confirm_title, t.agent),
                                    detail = context.getString(R.string.stop_confirm_detail),
                                    verb = context.getString(R.string.stop_go),
                                    run = { stop(f, t.id) },
                                ),
                            )
                        }
                    },
                    enabled = !busy && f != null && t != null,
                    modifier = Modifier.padding(start = 8.dp).testTag("stop-go"),
                ) { Text(stringResource(R.string.stop_go)) }
            }
        }
    }
}
