package com.muvusoft.agentfarm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.NetworkWatch
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

    private fun airplane(on: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}").close()
    }

    @Test
    fun aNetworkThatComesBackIsUsedAtOnceNotAfterTheBackoff() {
        val farm = paired()
        val watch = NetworkWatch(InstrumentationRegistry.getInstrumentation().targetContext) { manager.onNetwork(it) }
        watch.start()
        try {
            manager.setFarms(listOf(farm))
            await(farm.id, "online") { it.link is Link.Online }
            airplane(true)
            // Attempt 6 waits 16-20 s before the next one; the backoff alone would leave the link down that long.
            runBlocking {
                withTimeout(90_000) { manager.state.first { s -> ((s.farm(farm.id)?.link as? Link.Connecting)?.attempt ?: 0) >= 6 } }
            }
            val back = System.currentTimeMillis()
            airplane(false)
            runBlocking {
                try {
                    withTimeout(12_000) { manager.state.first { s -> s.farm(farm.id)?.link is Link.Online } }
                } catch (e: Exception) {
                    throw AssertionError("not back within 12 s of the network; state: ${manager.state.value.farm(farm.id)}", e)
                }
            }
            Log.i("ConnectionE2ETest", "online again ${System.currentTimeMillis() - back} ms after the network came back")
        } finally {
            airplane(false)
            watch.stop()
        }
    }

    @Test
    fun aFarmFoundAtANewAddressIsReachedThereAtOnce() {
        val farm = paired()
        val real = farm.addresses.first()
        // The PC moved: the pairing's address answers nothing any more. (The emulator's network carries
        // no multicast, so the finder's answer is handed in directly; FarmFinder is tried on a real phone.)
        manager.setFarms(listOf(farm.copy(addresses = listOf("10.0.2.2:1"))))
        runBlocking { withTimeout(30_000) { manager.state.first { s -> ((s.farm(farm.id)?.link as? Link.Connecting)?.attempt ?: 0) >= 4 } } }
        // From attempt 4 the backoff waits 8-10 s; the farm's answer on the network cuts that short.
        val t = System.currentTimeMillis()
        manager.onFound(farm.id, real)
        val s = await(farm.id, "online at the found address") { it.link is Link.Online }
        val took = System.currentTimeMillis() - t
        assertEquals(real, (s.link as Link.Online).address)
        assertTrue("online $took ms after the answer", took < 6_000)
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
