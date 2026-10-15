package com.muvusoft.agentfarm.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.link.Wake
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.WakeSender
import kotlinx.coroutines.launch

/** A farm that is not online: the wait (kind · reason · since), and a wake for its PC when it sleeps and its card is known. */
@Composable
fun WaitScreen(farmId: String, farmName: String, link: Link?, manager: ConnectionManager) {
    val context = LocalContext.current
    val nics = remember(farmId) { AgentFarmApp.of(context).prefs.wake(farmId) }
    var outcome by remember(farmId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val text = link?.let { LinkText.of(it, System.currentTimeMillis()).long } ?: stringResource(R.string.farm_unpaired)
        Text(text, Modifier.testTag("page-wait"), style = MaterialTheme.typography.bodyLarge)
        if (Wake.offered(link, nics)) {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val r = WakeSender.send(nics)
                        outcome = r.fold(
                            onSuccess = { context.getString(R.string.wake_sent, farmName) },
                            onFailure = { context.getString(R.string.wake_failed, it.message ?: it.javaClass.simpleName) },
                        )
                        if (r.isSuccess) manager.retryAfterWake(farmId)
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 16.dp).testTag("wake-go"),
            ) { Text(stringResource(R.string.wake_go)) }
            Text(stringResource(R.string.wake_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        outcome?.let { Text(it, Modifier.padding(top = 12.dp).testTag("wake-outcome"), color = MaterialTheme.colorScheme.primary) }
    }
}
