package com.muvusoft.agentfarm.core.power

import com.muvusoft.agentfarm.core.power.LinkRest.Act
import com.muvusoft.agentfarm.core.power.LinkRest.Hold
import com.muvusoft.agentfarm.core.power.LinkRest.Memo
import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkRestTest {
    private val farm = PairedFarm("farm_a", "Masaüstü", "dev_1", "manage", "ab", listOf("192.168.1.2:8743"), "k_a")
    private val online = Link.Online("192.168.1.2:8743", "manage", emptyMap(), 0)
    private val idle = FarmStatus(farm, online, running = 0)
    private val away = LinkRest.Phone(visible = false, pushToken = true)

    private fun hold(f: FarmStatus = idle, outbox: Boolean = false, wakeable: Boolean = true, phone: LinkRest.Phone = away) =
        LinkRest.hold(f, outbox, wakeable, phone)

    @Test
    fun anIdleFarmThatCanWakeThePhoneHoldsNothing() {
        assertNull(hold())
    }

    @Test
    fun whatTheFarmWouldNotPushHoldsTheSocket() {
        assertEquals(Hold.VISIBLE, hold(phone = away.copy(visible = true)))
        assertEquals(Hold.OUTBOX, hold(outbox = true))
        assertEquals(Hold.WAITING, hold(idle.copy(needs = 1)))
        assertEquals(Hold.RUNNING, hold(idle.copy(running = 2)))
        assertEquals("a count not asked yet is not a zero", Hold.UNKNOWN, hold(idle.copy(running = null)))
    }

    @Test
    fun withoutAPushWayTheSocketIsTheOnlyWayAndStaysOpen() {
        assertEquals("the farm keeps no token", Hold.NO_PUSH, hold(wakeable = false))
        assertEquals("the phone has none", Hold.NO_PUSH, hold(phone = away.copy(pushToken = false)))
        assertEquals("the screen still comes first", Hold.VISIBLE, hold(wakeable = false, phone = LinkRest.Phone(true, false)))
    }

    @Test
    fun aConnectedFarmRestsOnceNothingHeldItForTheGrace() {
        val first = LinkRest.look(online, null, Memo(), 1_000)
        assertEquals(Act.NONE, first.act)
        assertEquals(1_000 + LinkRest.GRACE_MS, first.nextAt)
        val early = LinkRest.look(online, null, first.memo, 1_000 + LinkRest.GRACE_MS - 1)
        assertEquals("the idle clock is not restarted by a second look", Act.NONE, early.act)
        val due = LinkRest.look(online, null, early.memo, 1_000 + LinkRest.GRACE_MS)
        assertEquals(Act.REST, due.act)
        assertEquals(Memo(), due.memo)
    }

    @Test
    fun somethingHoldingItStartsTheGraceOver() {
        val idleSince = LinkRest.look(online, null, Memo(), 0).memo
        val held = LinkRest.look(online, Hold.RUNNING, idleSince, LinkRest.GRACE_MS - 10)
        assertEquals(Act.NONE, held.act)
        assertNull(held.nextAt)
        val again = LinkRest.look(online, null, held.memo, LinkRest.GRACE_MS)
        assertEquals(Act.NONE, again.act)
        assertEquals(LinkRest.GRACE_MS + LinkRest.GRACE_MS, again.nextAt)
    }

    @Test
    fun aRestingFarmOpensWhenHeldAndForASparseCheck() {
        val resting = Link.Resting(10_000)
        assertEquals(Act.RESUME, LinkRest.look(resting, Hold.VISIBLE, Memo(), 10_001).act)
        val waits = LinkRest.look(resting, null, Memo(), 10_001)
        assertEquals(Act.NONE, waits.act)
        assertEquals(10_000 + LinkRest.PEEK_EVERY_MS, waits.nextAt)
        val peek = LinkRest.look(resting, null, Memo(), 10_000 + LinkRest.PEEK_EVERY_MS)
        assertEquals(Act.RESUME, peek.act)
        assertEquals(Memo(peek = true), peek.memo)
    }

    @Test
    fun aCheckThatFindsNothingRestsSoonAndOneThatFindsWorkHoldsLikeAnyOther() {
        val connected = LinkRest.look(online, null, Memo(peek = true), 0)
        assertEquals(LinkRest.PEEK_GRACE_MS, connected.nextAt)
        assertEquals(Act.REST, LinkRest.look(online, null, connected.memo, LinkRest.PEEK_GRACE_MS).act)
        val found = LinkRest.look(online, Hold.WAITING, Memo(peek = true), 0)
        assertEquals("work found ends the check", Memo(), found.memo)
        assertEquals(LinkRest.GRACE_MS, LinkRest.look(online, null, found.memo, 0).nextAt)
    }

    @Test
    fun anUnreachableFarmRestsTooAndOneOnItsWayIsLeftToArrive() {
        val off = Link.Offline("unreachable", 0)
        assertEquals(Act.REST, LinkRest.look(off, null, Memo(idleSince = 0), LinkRest.GRACE_MS).act)
        val connecting = Link.Connecting("192.168.1.2:8743", 2, 0)
        val memo = Memo(idleSince = 0, peek = true)
        val onWay = LinkRest.look(connecting, null, memo, LinkRest.GRACE_MS * 10)
        assertEquals(Act.NONE, onWay.act)
        assertEquals("the check and its clock survive the attempt", memo, onWay.memo)
        assertEquals(Act.NONE, LinkRest.look(Link.Refused("busy", null, 0), null, memo, LinkRest.GRACE_MS * 10).act)
    }
}
