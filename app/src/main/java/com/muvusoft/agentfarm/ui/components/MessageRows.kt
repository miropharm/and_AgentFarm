package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.conversation.Block
import com.muvusoft.agentfarm.core.conversation.Blocks
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.SendState
import com.muvusoft.agentfarm.core.markdown.Markdown
import com.muvusoft.agentfarm.core.session.SessionText
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.ui.theme.CodeFont
import com.muvusoft.agentfarm.ui.theme.LocalStatusColors

@Composable
private fun sendWord(state: SendState): String = stringResource(
    when (state) {
        SendState.WAITING -> R.string.send_waiting
        SendState.SENDING -> R.string.send_sending
        SendState.DELIVERED -> R.string.send_delivered
        SendState.AFTER_TURN -> R.string.after_turn
    },
)

/** What the user said, on the right; a message sent from here carries where it is. */
@Composable
fun UserBubble(text: String, state: SendState?, clipped: Boolean, onShowAll: () -> Unit, tag: String) {
    val max = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    Column(Modifier.fillMaxWidth().testTag(tag), horizontalAlignment = Alignment.End) {
        Surface(
            Modifier.widthIn(max = max),
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            SelectionContainer { Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        if (clipped) TextButton(onClick = onShowAll) { Text(stringResource(R.string.show_all)) }
        if (state != null) {
            Text(sendWord(state), Modifier.padding(top = 4.dp, end = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An agent's answer, full width, as markdown. */
@Composable
fun AnswerBlock(m: Message, onShowAll: () -> Unit) {
    val blocks = remember(m.text) { Markdown.parse(m.text.orEmpty()) }
    Column(Modifier.fillMaxWidth().testTag("answer-${m.i}")) {
        SelectionContainer { MarkdownText(blocks) }
        if (m.clipped != null) TextButton(onClick = onShowAll) { Text(stringResource(R.string.show_all)) }
    }
}

/** Tool steps in a row, folded to one line; open, each step is a line; a step's whole text is a sheet. */
@Composable
fun StepsRow(steps: Block.Steps) {
    var open by rememberSaveable(steps.first) { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<List<String>?>(null) }
    val error = MaterialTheme.colorScheme.error
    Surface(Modifier.fillMaxWidth().testTag("steps-${steps.first}"), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = stringResource(if (open) R.string.steps_hide else R.string.steps_show)) { open = !open }
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.outline)
                Text(Blocks.summary(steps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Blocks.calls(steps).forEach { call ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.show_details)) { sheet = Blocks.sheet(steps, call) }
                            .heightIn(min = 44.dp).padding(start = 44.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(Blocks.stepName(call), style = MaterialTheme.typography.labelLarge, color = if (call.isError) error else MaterialTheme.colorScheme.onSurface)
                        Text(
                            Blocks.stepDetail(call),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = CodeFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    sheet?.let { AFTip(it, onDismiss = { sheet = null }) }
}

/** A system line: compacted, resumed, a model switch. */
@Composable
fun NoteRow(m: Message) {
    Text(
        m.text.orEmpty(),
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
    )
}

/** The turn in flight: the step it is on now, live. */
@Composable
fun LiveLine(step: Step?, now: Long) {
    val words = step?.let { SessionText.step(it, now) } ?: stringResource(R.string.step_thinking)
    Row(
        Modifier.fillMaxWidth().testTag("live").padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(LocalStatusColors.current.running)
        Text(stringResource(R.string.live_now, words), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
