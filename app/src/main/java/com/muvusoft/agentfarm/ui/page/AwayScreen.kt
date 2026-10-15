package com.muvusoft.agentfarm.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.away.Away
import com.muvusoft.agentfarm.core.away.AwayVerdict
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.request
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The farm's VS Code is closed: what its service last saw, and the one thing it can do — start Agent Farm. */
@Composable
fun AwayScreen(farmId: String, farmName: String, away: Away, manager: ConnectionManager) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("away"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(away.sentence, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        AwayVerdict.lines(away, now).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (away.top.isNotEmpty()) {
            Text(
                stringResource(R.string.away_waiting),
                Modifier.padding(top = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
            )
            away.top.forEach { Text(AwayVerdict.row(it), style = MaterialTheme.typography.bodyMedium) }
        }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val r = manager.request(farmId, "farm.open")
                    answer = r?.let { AwayVerdict.opened(it.ok, it.result, it.error, farmName, System.currentTimeMillis()) }
                        ?: AwayVerdict.NO_ANSWER
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.padding(top = 16.dp).testTag("away-open"),
        ) { Text(stringResource(R.string.away_open)) }
        answer?.let { Text(it, Modifier.testTag("away-answer"), style = MaterialTheme.typography.bodyMedium) }
    }
}
