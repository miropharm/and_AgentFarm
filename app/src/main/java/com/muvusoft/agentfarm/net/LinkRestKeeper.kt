package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.power.LinkRest
import com.muvusoft.agentfarm.core.state.ShellState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/**
 * Runs core/power/LinkRest over every paired farm: looks again on every change of the shell state or of the app
 * being on screen, and at the time the last look named; rests or opens each farm's link as it is told. Only the
 * collector touches [memos].
 */
class LinkRestKeeper(
    private val manager: ConnectionManager,
    private val visible: StateFlow<Boolean>,
    private val pushToken: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val memos = HashMap<String, LinkRest.Memo>()

    suspend fun run() {
        combine(manager.state, visible) { s, v -> s to v }.collectLatest { (s, v) ->
            while (true) {
                val next = look(s, v) ?: break
                delay((next - clock()).coerceAtLeast(MIN_WAIT_MS))
            }
        }
    }

    /** One look over every farm; the earliest time one of them asked to be looked at again, or null. */
    private fun look(s: ShellState, visible: Boolean): Long? {
        val phone = LinkRest.Phone(visible = visible, pushToken = !pushToken().isNullOrBlank())
        val now = clock()
        memos.keys.retainAll(s.farms.map { it.farm.id }.toSet())
        var next: Long? = null
        for (f in s.farms) {
            val id = f.farm.id
            val hold = LinkRest.hold(f, outbox = s.outbox.any { it.farm == id }, wakeable = manager.isWakeable(id), phone = phone)
            val l = LinkRest.look(f.link, hold, memos[id] ?: LinkRest.Memo(), now)
            memos[id] = l.memo
            when (l.act) {
                LinkRest.Act.REST -> manager.rest(id)
                LinkRest.Act.RESUME -> manager.wake(id)
                LinkRest.Act.NONE -> Unit
            }
            l.nextAt?.let { at -> next = next?.let { minOf(it, at) } ?: at }
        }
        return next
    }

    private companion object {
        /** A look named for a moment already past waits this long, so a clock that lags never spins the loop. */
        const val MIN_WAIT_MS = 1_000L
    }
}
