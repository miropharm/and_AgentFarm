package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.Time
import com.muvusoft.agentfarm.core.now.Recent
import com.muvusoft.agentfarm.core.now.Running
import com.muvusoft.agentfarm.core.now.Waiting
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.Asks
import com.muvusoft.agentfarm.core.session.Quick
import com.muvusoft.agentfarm.core.session.SessionText
import com.muvusoft.agentfarm.ui.theme.LocalStatusColors

@Composable
fun kindWord(kind: AskKind): String = stringResource(
    when (kind) {
        AskKind.QUESTION -> R.string.ask_question
        AskKind.PERMISSION -> R.string.ask_permission
        AskKind.APPROVAL -> R.string.ask_approval
    },
)

/** The agent's name, in the accent: who a row belongs to. */
@Composable
fun AgentName(agent: String) {
    Text(agent, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
}

/** A turn parked on the user. A short answer is given from the row; anything else opens the card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaitingRow(w: Waiting, now: Long, onOpen: () -> Unit, onQuick: (Quick) -> Unit) {
    val status = LocalStatusColors.current
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("waiting-${w.session}"),
        shape = MaterialTheme.shapes.medium,
        color = status.waitingContainer,
        contentColor = status.onWaitingContainer,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(status.waiting)
                Text(kindWord(w.ask.kind), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(w.agent, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Time.ago(now - w.ask.since), style = MaterialTheme.typography.bodySmall)
            }
            Text(w.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Asks.firstLine(w.ask), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val quick = Asks.quick(w.ask)
            if (quick.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    quick.forEach { q ->
                        when (q) {
                            Quick.Allow -> Button(onClick = { onQuick(q) }) { Text(stringResource(R.string.allow)) }
                            Quick.Deny -> OutlinedButton(onClick = { onQuick(q) }) { Text(stringResource(R.string.deny)) }
                            is Quick.Option -> OutlinedButton(onClick = { onQuick(q) }) { Text(q.label, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/** A running turn: who, what, the step in flight live, how long and what it cost. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RunningCard(r: Running, now: Long, onOpen: () -> Unit) {
    var tip by remember { mutableStateOf(false) }
    val step = r.step?.let { SessionText.step(it, now) } ?: stringResource(R.string.step_thinking)
    Surface(
        Modifier.fillMaxWidth().testTag("running-${r.session}")
            .combinedClickable(onClickLabel = stringResource(R.string.open_conversation), onClick = onOpen,
                onLongClickLabel = stringResource(R.string.show_details), onLongClick = { tip = true }),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) { AgentName(r.agent) }
                Text(SessionText.turn(r.turnSince, r.cost, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(r.title, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(LocalStatusColors.current.running)
                Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (tip) AFTip(listOfNotNull(r.title, r.agent, step, SessionText.turn(r.turnSince, r.cost, now)), onDismiss = { tip = false })
}

/** A finished session: its title, when, and the first line of its last answer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentRow(r: Recent, now: Long, onOpen: () -> Unit) {
    var tip by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().testTag("recent-${r.session}")
            .combinedClickable(onClickLabel = stringResource(R.string.open_conversation), onClick = onOpen,
                onLongClickLabel = stringResource(R.string.show_details), onLongClick = { tip = true })
            .heightIn(min = 56.dp).padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.failed) Text(stringResource(R.string.failed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            Text(Time.ago(now - r.endedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentName(r.agent)
            Text(r.lastLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (tip) AFTip(listOf(r.title, r.agent, r.lastLine), onDismiss = { tip = false })
}
