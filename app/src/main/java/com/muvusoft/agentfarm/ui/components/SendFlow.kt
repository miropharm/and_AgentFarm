package com.muvusoft.agentfarm.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.Shared
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.Outbox
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.ShareSend
import com.muvusoft.agentfarm.ui.lock.Destructive
import com.muvusoft.agentfarm.ui.lock.rememberDestructiveConfirm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** A send the farm stopped at a gate: the yes goes back to the same farm and target, never to a new pick. */
data class Asked(val farm: String, val target: String, val gate: String, val shared: Shared, val whom: String)

/**
 * One send the way every native door makes it (a share, a new session): words, and a picture that goes up to
 * the PC first, into a running Console session (`console.send`) or opening a new one with an agent
 * (`console.new`). A farm that does not answer gets the words in the outbox and a notification tells how it
 * went. The farm's remote gate (today's budget, a command that cannot be undone) may stop a send: its sentence
 * is the outcome, and [again] asks the owner before the same words go again with the yes.
 */
class SendFlow internal constructor(
    private val context: Context,
    private val manager: ConnectionManager,
    private val scope: CoroutineScope,
    private val confirm: (Destructive) -> Unit,
    private val confirmTitle: String,
    private val confirmVerb: String,
) {
    var outcome by mutableStateOf<String?>(null)
        private set
    var asked by mutableStateOf<Asked?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** The farm took it at once and asked nothing more. */
    var delivered by mutableStateOf(false)
        private set

    /** What the farm answered the delivered send with: the card a new session opened in, for one. */
    var answer by mutableStateOf<JsonElement?>(null)
        private set

    /** `whom` names the target in the outbox's receipt: the agent, or the session's agent. */
    fun send(farm: String, to: String, shared: Shared, whom: String, gate: String? = null) {
        val status = manager.state.value.farm(farm)
        if (status?.link !is Link.Online) {
            val (op, args) = Share.call(to, shared.text, null, gate)
            manager.call(farm, op, args, receipt = whom)
            outcome = Share.queued(whom, status?.farm?.name ?: farm, Outbox.MAX_AGE_HOURS)
            return
        }
        busy = true
        scope.launch {
            val r = ShareSend.send(context, manager, farm, to, shared, gate)
            val ok = r?.ok == true
            outcome = Share.outcome(ok, r?.result, r?.error)
            asked = Share.asks(ok, r?.result)?.let { Asked(farm, to, it, shared, whom) }
            answer = r?.result?.takeIf { ok && asked == null }
            delivered = ok && asked == null
            busy = false
        }
    }

    /** The farm's gate answered yes, after the owner's own confirmation. */
    fun again() {
        val a = asked ?: return
        confirm(Destructive(title = confirmTitle, detail = outcome.orEmpty(), verb = confirmVerb, run = { send(a.farm, a.target, a.shared, a.whom, a.gate) }))
    }

    /** A different pick starts over: a yes given for one target is never spent on another. */
    fun startOver() {
        if (asked != null) clear()
    }

    /** The words or the pick changed: whatever was said about the last send no longer stands. */
    fun clear() {
        asked = null
        outcome = null
    }
}

@Composable
fun rememberSendFlow(manager: ConnectionManager): SendFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirm = rememberDestructiveConfirm()
    val title = stringResource(R.string.share_confirm_title)
    val verb = stringResource(R.string.share_confirm)
    return remember(manager, confirm, title, verb) { SendFlow(context, manager, scope, confirm, title, verb) }
}
