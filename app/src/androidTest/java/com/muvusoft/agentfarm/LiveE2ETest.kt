package com.muvusoft.agentfarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.Role
import com.muvusoft.agentfarm.core.live.LiveDepth
import com.muvusoft.agentfarm.core.live.LiveEvent
import com.muvusoft.agentfarm.core.live.LiveMerge
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.PairClient
import com.muvusoft.agentfarm.net.watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The live channel against the scripted farm: a watch hears a turn row by row, is asked again after a dropped socket, and ends. */
@RunWith(AndroidJUnit4::class)
class LiveE2ETest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = ConnectionManager(scope, saved = emptyList(), save = { }, onWake = { _, _ -> }, onReceipt = { _, _, _ -> }, pushToken = { null })

    @After
    fun tearDown() {
        manager.stopAll()
        scope.cancel()
    }

    private fun online(): PairedFarm = runBlocking {
        FakeHost.post("/_test/scenario", "{}")
        val r = PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test"))
        val farm = (r as PairingVerdict.Result.Paired).farm
        manager.setFarms(listOf(farm))
        withTimeout(20_000) { manager.state.first { it.farm(farm.id)?.link is Link.Online } }
        farm
    }

    private suspend fun Channel<LiveEvent>.next(what: String, pred: (LiveEvent) -> Boolean): LiveEvent = try {
        withTimeout(10_000) {
            var e = receive()
            while (!pred(e)) e = receive()
            e
        }
    } catch (e: Exception) {
        throw AssertionError("timed out waiting for $what", e)
    }

    private fun watches(): String = Regex("\"watches\"\\s*:\\s*(\\d+)").find(FakeHost.get("/_test/log"))!!.groupValues[1]

    @Test
    fun aWatchHearsTheTurnRowByRowAndIsAskedAgainAfterTheSocketDrops() {
        val farm = online()
        val events = Channel<LiveEvent>(Channel.UNLIMITED)
        val job = scope.launch { manager.watch(farm.id, "c_psy", LiveDepth.FULL).collect { events.send(it) } }
        runBlocking {
            val first = events.next("the first beat") { it is LiveEvent.Beat } as LiveEvent.Beat
            assertEquals("idle", first.data.status)
            assertTrue("the first beat tells no rows", first.data.messages!!.isEmpty())
            assertEquals("1", watches())

            FakeHost.post("/_test/turn", """{"id":"c_psy","stepMs":300,"answer":"Notlar hazır."}""")
            var conv = ConversationState("c_psy", "psikolog", "", "Claude", Link.Offline("closed", 0), 0)
            var sawStep = false
            while (true) {
                val b = events.next("the turn's beats") { it is LiveEvent.Beat } as LiveEvent.Beat
                sawStep = sawStep || b.data.step?.tool == "Bash"
                val ended = LiveMerge.turnEnded(conv, b.data)
                conv = LiveMerge.conversation(conv, b.data, System.currentTimeMillis())
                if (ended) break
            }
            assertTrue("a beat named the call in flight", sawStep)
            assertEquals(listOf(Role.USER, Role.TOOL, Role.TOOL_RESULT, Role.TOOL, Role.TOOL_RESULT, Role.ASSISTANT), conv.messages.map { it.role })
            assertEquals("Notlar hazır.", conv.messages.last().text)
            assertTrue("rows told live are provisional", conv.messages.all { it.provisional })

            FakeHost.post("/_test/drop", "{}")
            events.next("the watch asked again after the welcome") { it == LiveEvent.Resumed }
            val again = events.next("the farm's first beat of the new watch") { it is LiveEvent.Beat } as LiveEvent.Beat
            assertEquals("idle", again.data.status)
        }
        job.cancel()
        val end = System.currentTimeMillis() + 10_000
        while (watches() != "0" && System.currentTimeMillis() < end) Thread.sleep(200)
        assertEquals("a stopped collector unwatches", "0", watches())
    }

    @Test
    fun aWatchTheFarmCannotKeepIsEndedWithItsReason() {
        val farm = online()
        val heard = runBlocking { withTimeout(10_000) { manager.watch(farm.id, "c_nope", LiveDepth.FULL).first() } }
        assertEquals(LiveEvent.Ended("unavailable"), heard)
    }
}
