package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.Time
import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.AskQuestion
import com.muvusoft.agentfarm.ui.theme.CodeFont
import com.muvusoft.agentfarm.ui.theme.LocalStatusColors

/** What the card hands back: the picks per question, a typed answer, or the permission verdict. */
class AskActions(
    val onAnswer: (List<List<String>>) -> Unit,
    val onFree: (String) -> Unit,
    val onAllow: () -> Unit,
    val onDeny: () -> Unit,
)

/**
 * The question or permission a turn waits on, above the composer. A single one-pick question is answered
 * with one tap; several questions or a many-pick one are picked, then sent. A permission shows the tool
 * and its command whole.
 */
@Composable
fun AskCard(ask: Ask, now: Long, actions: AskActions) {
    val status = LocalStatusColors.current
    Surface(
        Modifier.fillMaxWidth().testTag("ask-card"),
        shape = MaterialTheme.shapes.large,
        color = status.waitingContainer,
        contentColor = status.onWaitingContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(status.waiting)
                Text(kindWord(ask.kind), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(Time.ago(now - ask.since), style = MaterialTheme.typography.bodySmall)
            }
            when (ask.kind) {
                AskKind.QUESTION -> Questions(ask, actions)
                AskKind.PERMISSION, AskKind.APPROVAL -> Permission(ask, actions)
            }
        }
    }
}

@Composable
private fun Questions(ask: Ask, actions: AskActions) {
    val picked = remember(ask) { mutableStateMapOf<Int, List<String>>() }
    val oneTap = ask.questions.size == 1 && !ask.questions[0].multiSelect
    if (ask.questions.isEmpty()) Text(ask.detail, style = MaterialTheme.typography.bodyLarge)
    ask.questions.forEachIndexed { qi, q ->
        Question(q, picked[qi].orEmpty()) { label ->
            if (oneTap) {
                actions.onAnswer(listOf(listOf(label)))
            } else {
                val now = picked[qi].orEmpty()
                picked[qi] = if (q.multiSelect) (if (label in now) now - label else now + label) else listOf(label)
            }
        }
    }
    if (!oneTap && ask.questions.isNotEmpty()) {
        Button(
            onClick = { actions.onAnswer(ask.questions.indices.map { picked[it].orEmpty() }) },
            enabled = ask.questions.indices.all { picked[it].orEmpty().isNotEmpty() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.send)) }
    }
    FreeAnswer(actions.onFree)
}

@Composable
private fun Question(q: AskQuestion, picked: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        q.header?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        q.options.forEach { o ->
            val on = o.label in picked
            val shape = RoundedCornerShape(12.dp)
            val content: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text((if (on) "✓ " else "") + o.label, style = MaterialTheme.typography.bodyLarge)
                    o.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val mod = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = on }
            if (on) Button(onClick = { onPick(o.label) }, mod, shape = shape) { content() }
            else OutlinedButton(onClick = { onPick(o.label) }, mod, shape = shape) { content() }
        }
    }
}

@Composable
private fun Permission(ask: Ask, actions: AskActions) {
    ask.tool?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Text(
            ask.detail,
            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            fontFamily = CodeFont,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = actions.onDeny, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.deny)) }
        Button(onClick = actions.onAllow, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.allow)) }
    }
}
