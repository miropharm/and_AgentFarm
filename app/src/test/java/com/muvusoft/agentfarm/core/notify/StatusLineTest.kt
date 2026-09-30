package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellReducer
import com.muvusoft.agentfarm.core.state.ShellState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLineTest {
    private val home = PairedFarm("farm_a", "Masaüstü", "dev_1", "manage", "ab", listOf("192.168.1.2:8743"), "k_a")
    private val office = PairedFarm("farm_b", "Ofis", "dev_2", "manage", "cd", listOf("10.0.0.5:8743"), "k_b")
    private val online = Link.Online("192.168.1.2:8743", "manage", emptyMap(), 0)

    private fun two(): ShellState = ShellReducer.pair(ShellReducer.pair(ShellState(), home, 0), office, 0)

    private fun ev(seq: Long, type: String, ts: Long, vararg data: Pair<String, Any>) = EventFrame(
        seq, type, ts,
        JsonObject(data.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) }),
    )

    @Test
    fun noFarmSaysSo() {
        assertEquals("Eşli çiftlik yok", StatusLine.of(ShellState(), 0).title)
    }

    @Test
    fun oneOnlineFarmIsNamed() {
        val s = ShellReducer.link(ShellReducer.pair(ShellState(), home, 0), "farm_a", online)
        assertEquals(StatusLine.Text("Bağlı · Masaüstü", "bekleyen yok"), StatusLine.of(s, 0))
    }

    @Test
    fun partlyConnectedCountsBoth() {
        val s = ShellReducer.link(two(), "farm_a", online)
        assertEquals("1/2 çiftlik bağlı", StatusLine.of(s, 0).title)
    }

    @Test
    fun needsAndTheLastTurnComeFromEvents() {
        var s = ShellReducer.link(ShellReducer.link(two(), "farm_a", online), "farm_b", online)
        s = ShellReducer.onFrame(s, "farm_a", ev(1, "needs.changed", 1_000, "count" to 2), "a", 0)
        s = ShellReducer.onFrame(s, "farm_b", ev(1, "needs.changed", 1_000, "count" to 1), "b", 0)
        s = ShellReducer.onFrame(s, "farm_b", ev(2, "turn.finished", 10_000, "session" to "x", "agent" to "dev", "title" to "t", "summary" to "s"), "b", 0)
        assertEquals(StatusLine.Text("2 çiftlik bağlı", "3 bekleyen · son tur 45 sn önce"), StatusLine.of(s, 55_000))
    }

    @Test
    fun aMalformedEventChangesOnlyTheSeq() {
        val s = ShellReducer.onFrame(two(), "farm_a", ev(7, "needs.changed", 0, "wrong" to 1), "a", 0)
        assertEquals(7, s.farm("farm_a")!!.lastSeq)
        assertEquals(0, s.farm("farm_a")!!.needs)
    }

    @Test
    fun aRefusalIsNotCalledRetrying() {
        val s = ShellReducer.link(ShellReducer.pair(ShellState(), home, 0), "farm_a", Link.Refused("revoked", null, 0))
        assertEquals("Bağlantı reddedildi", StatusLine.of(s, 0).title)
    }
}
