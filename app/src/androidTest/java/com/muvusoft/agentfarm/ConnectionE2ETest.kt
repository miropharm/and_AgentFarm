package com.muvusoft.agentfarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.PairClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The connection manager against the fake host: welcome, events, calls, a dropped socket, a revoked device. */
@RunWith(AndroidJUnit4::class)
class ConnectionE2ETest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = ConnectionManager(scope, saved = emptyList(), save = { })

    @After
    fun tearDown() {
        manager.stopAll()
        scope.cancel()
    }

    private fun paired(): PairedFarm = runBlocking {
        val r = PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test"))
        (r as PairingVerdict.Result.Paired).farm
    }

    private fun await(farm: String, what: String, cond: (FarmStatus) -> Boolean): FarmStatus = runBlocking {
        try {
            withTimeout(20_000) { manager.state.first { s -> s.farm(farm)?.let(cond) == true } }.farm(farm)!!
        } catch (e: Exception) {
            throw AssertionError("timed out waiting for $what; state: ${manager.state.value.farm(farm)}", e)
        }
    }

    @Test
    fun comesOnlineReceivesEventsAndAnswersCalls() {
        val farm = paired()
        manager.setFarms(listOf(farm))
        await(farm.id, "online") { it.link is Link.Online }

        val seq = Regex("\"seq\"\\s*:\\s*(\\d+)").find(FakeHost.post("/_test/event", "{\"type\":\"turn.finished\"}"))!!.groupValues[1].toLong()
        await(farm.id, "event $seq") { it.lastSeq >= seq }

        manager.call(farm.id, "farm.ping")
        runBlocking { withTimeout(10_000) { manager.state.first { it.outbox.isEmpty() } } }
        assertTrue(manager.state.value.outbox.isEmpty())
    }

    @Test
    fun aDroppedSocketReconnectsAndResumes() {
        val farm = paired()
        manager.setFarms(listOf(farm))
        await(farm.id, "online") { it.link is Link.Online }
        val before = FakeHost.post("/_test/event", "{\"type\":\"notice.posted\"}")
        await(farm.id, "first event") { it.lastSeq > 0 }

        FakeHost.post("/_test/drop", "{}")
        val missed = Regex("\"seq\"\\s*:\\s*(\\d+)").find(FakeHost.post("/_test/event", "{\"type\":\"needs.changed\"}"))!!.groupValues[1].toLong()
        assertTrue(before.isNotEmpty())
        val back = await(farm.id, "resumed to $missed") { it.link is Link.Online && it.lastSeq >= missed }
        assertEquals(missed, back.lastSeq)
    }

    @Test
    fun aRevokedDeviceStopsTrying() {
        val farm = paired()
        FakeHost.post("/_test/revoke", "{\"device\":\"${farm.device}\"}")
        manager.setFarms(listOf(farm))
        val s = await(farm.id, "refused") { it.link is Link.Refused }
        assertEquals("revoked", (s.link as Link.Refused).reason)
        Thread.sleep(3_000)
        assertTrue("a final refusal must not be retried", manager.state.value.farm(farm.id)!!.link is Link.Refused)
    }
}
