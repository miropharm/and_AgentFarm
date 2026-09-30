package com.muvusoft.agentfarm.core.state

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.FarmRef
import com.muvusoft.agentfarm.core.contract.FeatureAnswer
import com.muvusoft.agentfarm.core.contract.Gap
import com.muvusoft.agentfarm.core.contract.Ping
import com.muvusoft.agentfarm.core.contract.Refuse
import com.muvusoft.agentfarm.core.contract.Welcome
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellReducerTest {
    private val home = PairedFarm("farm_a", "Masaüstü", "dev_1", "manage", "ab", listOf("192.168.1.2:8378"), "k_a")
    private val office = PairedFarm("farm_b", "Ofis", "dev_2", "read", "cd", listOf("10.0.0.5:8378"), "k_b")
    private val empty = JsonObject(emptyMap())
    private fun item(id: String, farm: String = "farm_a", at: Long = 1) = OutboxItem(id, farm, "console.send", null, at)

    @Test
    fun pairingAddsFocusesAndRepairingKeepsTheEventPosition() {
        var s = ShellReducer.pair(ShellState(), home, 10)
        assertEquals("farm_a", s.focused)
        s = ShellReducer.pair(s, office, 11)
        assertEquals("farm_a", s.focused)
        s = ShellReducer.onFrame(s, "farm_a", EventFrame(7, "session.ended", 1, empty), "x", 12)
        s = ShellReducer.pair(s, home.copy(scope = "admin"), 13)
        assertEquals(2, s.farms.size)
        assertEquals("admin", s.farm("farm_a")!!.farm.scope)
        assertEquals(7L, s.farm("farm_a")!!.lastSeq)
    }

    @Test
    fun forgettingMovesFocusAndDropsItsOutbox() {
        var s = ShellReducer.pair(ShellReducer.pair(ShellState(), home, 1), office, 2)
        s = ShellReducer.enqueue(s, item("c1", "farm_a"))
        s = ShellReducer.enqueue(s, item("c2", "farm_b"))
        s = ShellReducer.forget(s, "farm_a")
        assertEquals("farm_b", s.focused)
        assertEquals(listOf("c2"), s.outbox.map { it.id })
        assertNull(ShellReducer.forget(s, "farm_b").focused)
    }

    @Test
    fun welcomeAndRefuseSetTheLink() {
        var s = ShellReducer.pair(ShellState(), home, 1)
        val features = mapOf("remote.access" to FeatureAnswer(true))
        s = ShellReducer.onFrame(s, "farm_a", Welcome(FarmRef("farm_a", "M"), "dev_1", "manage", features, 57), "192.168.1.2:8378", 5)
        assertEquals(Link.Online("192.168.1.2:8378", "manage", features, 5), s.farm("farm_a")!!.link)
        s = ShellReducer.onFrame(s, "farm_a", Refuse("revoked", "cihaz iptal edildi"), "x", 6)
        assertEquals(Link.Refused("revoked", "cihaz iptal edildi", 6), s.farm("farm_a")!!.link)
    }

    @Test
    fun seqOnlyGrowsAndAGapMovesPastTheLoss() {
        var s = ShellReducer.pair(ShellState(), home, 1)
        assertNull(ShellReducer.resumeAfter(s, "farm_a"))
        s = ShellReducer.onFrame(s, "farm_a", EventFrame(12, "t", 1, empty), "x", 2)
        s = ShellReducer.onFrame(s, "farm_a", EventFrame(9, "t", 1, empty), "x", 3)
        assertEquals(12L, ShellReducer.resumeAfter(s, "farm_a"))
        s = ShellReducer.onFrame(s, "farm_a", Gap(13, 40), "x", 4)
        assertEquals(40L, ShellReducer.resumeAfter(s, "farm_a"))
        assertEquals(s, ShellReducer.onFrame(s, "farm_a", Ping(1), "x", 5))
    }

    @Test
    fun outboxIsIdempotentOrderedAndClearedByItsResult() {
        var s = ShellReducer.pair(ShellState(), home, 1)
        s = ShellReducer.enqueue(s, item("c2", at = 20))
        s = ShellReducer.enqueue(s, item("c1", at = 10))
        s = ShellReducer.enqueue(s, item("c1", at = 99))
        assertEquals(listOf("c1", "c2"), ShellReducer.pending(s, "farm_a").map { it.id })
        s = ShellReducer.sent(s, "c1")
        assertEquals(1, s.outbox.first { it.id == "c1" }.attempts)
        s = ShellReducer.onFrame(s, "farm_b", CallResult("c1", true), "x", 2)
        assertEquals(2, s.outbox.size)
        s = ShellReducer.onFrame(s, "farm_a", CallResult("c1", true, duplicate = true), "x", 3)
        assertEquals(listOf("c2"), s.outbox.map { it.id })
    }

    @Test
    fun callIdsAreDistinctAndPrefixed() {
        val a = newCallId(1_790_760_000_000, 1)
        val b = newCallId(1_790_760_000_000, 2)
        assertTrue(a.startsWith("c_") && a != b)
    }
}
