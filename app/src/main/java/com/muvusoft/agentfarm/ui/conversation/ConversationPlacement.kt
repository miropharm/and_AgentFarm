package com.muvusoft.agentfarm.ui.conversation

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muvusoft.agentfarm.core.conversation.Block
import com.muvusoft.agentfarm.core.conversation.Blocks
import com.muvusoft.agentfarm.core.conversation.ConversationState

/** The key of the loader above the first message, shared with the list that draws it. */
const val OLDER_KEY = "older"

/**
 * Where the list stands. It follows the conversation until the person drags it, and again once they send: at the
 * last answer's start (the farm's page after the copy, a turn's answer), at their own message while it is on its
 * way. A list keeps its first visible row in place, so older messages coming in under the loader at the top
 * would leave the loader in view and ask again: the message that was first stays first instead. [lead] is the
 * number of rows above the first message.
 */
@Composable
fun ConversationPlacement(state: ConversationState, blocks: List<Block>, list: LazyListState, lead: Int) {
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    // What is followed: the person's own newest words while they are on their way, else the last answer's start
    // (the last thing asked while a turn has no answer yet). Placed again whenever a row comes or goes: a target
    // that cannot reach the top leaves the list at its end, so what was added below it (the live line, the steps
    // of the running turn) is in view rather than under the fold.
    val target = if (state.outgoing.isNotEmpty()) lead + blocks.size + state.outgoing.size - 1 else lead + Blocks.openAt(blocks)
    val rows = lead + blocks.size + state.outgoing.size + if (state.running) 1 else 0
    LaunchedEffect(target, rows, follow) {
        if (follow && rows > lead) list.scrollToItem(target)
    }
    val sent = state.outgoing.lastOrNull()?.id
    var lastSent by remember { mutableStateOf(sent) }
    LaunchedEffect(sent) {
        if (sent == null || sent == lastSent) return@LaunchedEffect
        lastSent = sent
        follow = true
    }
    val firstKey = blocks.firstOrNull()?.first
    var lastFirst by remember { mutableStateOf(firstKey) }
    LaunchedEffect(firstKey) {
        val was = lastFirst
        lastFirst = firstKey
        if (was == null || was == firstKey || list.layoutInfo.visibleItemsInfo.firstOrNull()?.key != OLDER_KEY) return@LaunchedEffect
        val at = blocks.indexOfFirst { it.first == was }
        if (at >= 0) list.scrollToItem(lead + at)
    }
}
