package com.muvusoft.agentfarm.core.power

import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link

/**
 * Whether a farm's socket is held open or left to rest. A farm that keeps this phone's push token wakes it for
 * everything alerting while no socket is open, so an idle socket only spends battery: it is held while something
 * may still happen that the farm would not push (a turn running, a question waiting, a call to send, the app on
 * screen), and otherwise closed after a grace. A resting link opens again on a push wake, the app coming forward,
 * an outbox item, or a sparse check that reads what changed and rests again. Pure: the caller brings the clock.
 */
object LinkRest {
    /** Idle this long after something happened before the socket closes. */
    const val GRACE_MS = 2 * 60_000L

    /** A check that found nothing to hold rests again this soon. */
    const val PEEK_GRACE_MS = 20_000L

    /** A resting link opens this often to read what changed while it rested. */
    const val PEEK_EVERY_MS = 15 * 60_000L

    /** Why a farm's socket is held. */
    enum class Hold {
        /** The app is on screen: its pages read the farm live. */
        VISIBLE,
        /** A call waits in the outbox for this farm. */
        OUTBOX,
        /** The farm cannot wake this phone (it keeps no push token, or the phone has none): the socket is the only way. */
        NO_PUSH,
        /** Something waits for the user on this farm, and its settling is not pushed. */
        WAITING,
        /** How many turns run is not known yet. */
        UNKNOWN,
        /** A turn is running. */
        RUNNING,
    }

    /** The phone's side of the verdict. */
    data class Phone(val visible: Boolean, val pushToken: Boolean)

    /** What holds a farm's socket open, or null when nothing does and it may rest. */
    fun hold(farm: FarmStatus, outbox: Boolean, wakeable: Boolean, phone: Phone): Hold? {
        val running = farm.running
        return when {
            phone.visible -> Hold.VISIBLE
            outbox -> Hold.OUTBOX
            !wakeable || !phone.pushToken -> Hold.NO_PUSH
            farm.needs > 0 -> Hold.WAITING
            running == null -> Hold.UNKNOWN
            running > 0 -> Hold.RUNNING
            else -> null
        }
    }

    enum class Act { NONE, REST, RESUME }

    /** What a keeper remembers about one farm between looks: since when nothing held it, and whether it is a check. */
    data class Memo(val idleSince: Long? = null, val peek: Boolean = false)

    /** One look's answer: what to do now, what to remember, and when to look again at the latest (null = on the next change). */
    data class Look(val act: Act, val memo: Memo, val nextAt: Long?)

    /**
     * One farm, now. A resting link resumes when something holds it, or for a check once [PEEK_EVERY_MS] passed. A
     * connected or unreachable one rests once nothing held it for the grace (a check's own, shorter, while it is one);
     * a link on its way is left to arrive, and a refused one to its refusal.
     */
    fun look(link: Link, hold: Hold?, memo: Memo, now: Long): Look = when (link) {
        is Link.Resting -> when {
            hold != null -> Look(Act.RESUME, Memo(), null)
            now >= link.since + PEEK_EVERY_MS -> Look(Act.RESUME, Memo(peek = true), null)
            else -> Look(Act.NONE, memo, link.since + PEEK_EVERY_MS)
        }
        is Link.Online, is Link.Offline -> when {
            hold != null -> Look(Act.NONE, Memo(), null)
            else -> {
                val idle = memo.idleSince ?: now
                val at = idle + if (memo.peek) PEEK_GRACE_MS else GRACE_MS
                if (now >= at) Look(Act.REST, Memo(), null) else Look(Act.NONE, memo.copy(idleSince = idle), at)
            }
        }
        is Link.Connecting -> Look(Act.NONE, if (hold != null) Memo() else memo, null)
        is Link.Refused -> Look(Act.NONE, Memo(), null)
    }
}
