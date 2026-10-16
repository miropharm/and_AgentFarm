package com.muvusoft.agentfarm.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.conversation.Block
import com.muvusoft.agentfarm.core.conversation.Blocks
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.session.SessionText
import com.muvusoft.agentfarm.ui.components.AnswerBlock
import com.muvusoft.agentfarm.ui.components.AskActions
import com.muvusoft.agentfarm.ui.components.AskCard
import com.muvusoft.agentfarm.ui.components.Composer
import com.muvusoft.agentfarm.ui.components.ComposerActions
import com.muvusoft.agentfarm.ui.components.ErrorBanner
import com.muvusoft.agentfarm.ui.components.LiveLine
import com.muvusoft.agentfarm.ui.components.NoteRow
import com.muvusoft.agentfarm.ui.components.ScreenBar
import com.muvusoft.agentfarm.ui.components.SkeletonRows
import com.muvusoft.agentfarm.ui.components.StaleBanner
import com.muvusoft.agentfarm.ui.components.StepsRow
import com.muvusoft.agentfarm.ui.components.UserBubble
import kotlinx.coroutines.flow.first

class ConversationActions(
    val onBack: () -> Unit,
    val onDetails: () -> Unit,
    val onStop: () -> Unit,
    val onOverflow: () -> Unit,
    val onOlder: () -> Unit,
    val onRetry: () -> Unit,
    val onShowAll: (Message) -> Unit,
    val ask: AskActions,
    val composer: ComposerActions,
)

/**
 * One session's conversation. It opens at the START of the last answer; a running turn's live step is
 * below the last row; a question or permission waits above the composer. It only draws.
 */
@Composable
fun ConversationScreen(state: ConversationState, now: Long, draft: String, actions: ConversationActions) {
    val blocks = remember(state.messages) { Blocks.of(state.messages) }
    val stale = SessionText.stale(state.link, state.updatedAt, now)
    val lead = listOf(state.older, state.loading).count { it }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = lead + Blocks.openAt(blocks))
    ConversationPlacement(state, blocks, list, lead)
    val maxCard = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    Scaffold(
        topBar = {
            ScreenBar(
                navigation = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (state.running) {
                        TextButton(onClick = actions.onStop, Modifier.testTag("conv-stop")) {
                            Text(stringResource(R.string.stop_go), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = actions.onOverflow) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                },
            ) {
                Column(Modifier.clickable(onClickLabel = stringResource(R.string.show_details), onClick = actions.onDetails).testTag("conv-title")) {
                    Text(state.agent, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        bottomBar = {
            // While the turn waits on the user, the card is the bottom: it has its own typed answer, and a
            // landscape screen has no room for both.
            Column(Modifier.imePadding()) {
                val ask = state.ask
                if (ask != null) {
                    Column(Modifier.heightIn(max = maxCard).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        AskCard(ask, now, actions.ask)
                    }
                } else {
                    Composer(state.composer, state.readOnly, draft, actions.composer)
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // The list opens at the last answer, far below its top: a stale copy or a failure stays above it, seen.
            if (stale != null || state.error != null) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    stale?.let { StaleBanner(it) }
                    state.error?.let { ErrorBanner(it, actions.onRetry) }
                }
            }
            MessageList(state, blocks, list, now, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MessageList(
    state: ConversationState,
    blocks: List<Block>,
    list: LazyListState,
    now: Long,
    actions: ConversationActions,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.fillMaxWidth().testTag("conv-list"),
        state = list,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (state.older) {
            item(key = OLDER_KEY) {
                Gap { SkeletonRows(1) }
                // Asked once the list was laid out with the last page and the loader is in view, never ahead of the scroll.
                LaunchedEffect(state.messages.firstOrNull()?.i) {
                    withFrameNanos { }
                    snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == OLDER_KEY } }.first { it }
                    actions.onOlder()
                }
            }
        }
        if (state.loading) item(key = "loading") { Gap { SkeletonRows(3) } }
        items(blocks, key = { "b-" + it.first }) { b ->
            Gap {
                when (b) {
                    is Block.User -> UserBubble(
                        text = b.message.text.orEmpty(), state = null, clipped = b.message.clipped != null,
                        onShowAll = { actions.onShowAll(b.message) }, tag = "user-${b.message.i}",
                    )
                    is Block.Answer -> AnswerBlock(b.message, onShowAll = { actions.onShowAll(b.message) })
                    is Block.Steps -> StepsRow(b)
                    is Block.Note -> NoteRow(b.message)
                }
            }
        }
        items(state.outgoing, key = { "o-" + it.id }) { o ->
            Gap { UserBubble(text = o.text, state = o.state, clipped = false, onShowAll = {}, tag = "out-${o.id}") }
        }
        if (state.running) item(key = "live") { LiveLine(state.live, now) }
    }
}

@Composable
private fun Gap(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { content() }
}
