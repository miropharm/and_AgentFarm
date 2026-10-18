package com.muvusoft.agentfarm

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.link.Reconnect
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A hundred dropped sockets in a row, an event posted each time while the link is down: every drop comes back
 * with the event it missed, none is lost, and at the end the farm holds one socket and the phone no more file
 * descriptors, threads or heap than after the first few drops. The figures go into journeys.txt (Q1 · none).
 */
@RunWith(AndroidJUnit4::class)
class ReconnectSoakE2ETest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = ConnectionManager(scope, saved = emptyList(), save = { }, onWake = { _, _ -> }, onReceipt = { _, _, _ -> }, pushToken = { null })

    @After
    fun tearDown() {
        manager.stopAll()
        scope.cancel()
    }

    @Test
    fun aHundredDropsComeBackWithNothingLostAndNothingLeftBehind() {
        val farm = runBlocking {
            (PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) as PairingVerdict.Result.Paired).farm
        }
        val seen = ConcurrentLinkedQueue<Long>()
        val collector = scope.launch { manager.frames.collect { f -> if (f.farm == farm.id) (f.frame as? EventFrame)?.let { seen += it.seq } } }
        manager.setFarms(listOf(farm))
        await(farm, "online") { it.link is Link.Online }
        // The pools and the TLS session cache fill on the first drops; what is left after them is the baseline.
        repeat(WARM) { drop(farm) }
        val before = Usage.now()
        val posted = ArrayList<Long>(DROPS)
        val took = LongArray(DROPS)
        for (k in 0 until DROPS) {
            val t0 = SystemClock.elapsedRealtime()
            posted += drop(farm)
            took[k] = SystemClock.elapsedRealtime() - t0
        }
        val after = Usage.now()
        collector.cancel()

        val j = Journey("Q1", "none")
        val sorted = took.sorted()
        j.note("$DROPS drops: back with the missed event in median ${sorted[DROPS / 2]} ms, p95 ${sorted[DROPS * 95 / 100]} ms, max ${sorted.last()} ms (the first backoff step is ${Reconnect.FIRST_MS}-${Reconnect.FIRST_MS * 5 / 4} ms)")
        val got = seen.groupingBy { it }.eachCount()
        val lost = posted.filter { it !in got }
        j.check("every missed event came", lost.isEmpty(), "${posted.size - lost.size} / ${posted.size}" + if (lost.isEmpty()) "" else " · lost $lost")
        val twice = posted.count { (got[it] ?: 0) > 1 }
        j.check("no event told twice", twice == 0, "$twice told more than once")
        // Per device: the app's own link service holds its socket to the same farm beside this test's.
        val sockets = JSONObject(FakeHost.get("/_test/state")).getJSONObject("socketsOf").optInt(farm.device)
        j.check("the farm holds one socket of this device", sockets == 1, "$sockets")
        j.check("file descriptors", after.fds - before.fds <= MAX_MORE_FDS, "${before.fds} -> ${after.fds} (at most +$MAX_MORE_FDS)")
        j.check("threads", after.threads - before.threads <= MAX_MORE_THREADS, "${before.threads} -> ${after.threads} (at most +$MAX_MORE_THREADS)")
        j.check("heap after a collection", after.heapKb - before.heapKb <= MAX_MORE_HEAP_KB, "${before.heapKb} -> ${after.heapKb} KB (at most +$MAX_MORE_HEAP_KB KB)")
        j.end()
    }

    /** The farm drops the socket; once the phone sees the link down, an event; back when the phone has it. */
    private fun drop(farm: PairedFarm): Long {
        FakeHost.post("/_test/drop", "{}")
        await(farm, "the drop") { it.link !is Link.Online }
        val seq = Regex("\"seq\"\\s*:\\s*(\\d+)").find(FakeHost.post("/_test/event", "{\"type\":\"needs.changed\"}"))!!.groupValues[1].toLong()
        await(farm, "back with $seq") { it.link is Link.Online && it.lastSeq >= seq }
        return seq
    }

    private fun await(farm: PairedFarm, what: String, cond: (FarmStatus) -> Boolean) = runBlocking {
        try {
            withTimeout(20_000) { manager.state.first { s -> s.farm(farm.id)?.let(cond) == true } }
        } catch (e: Exception) {
            throw AssertionError("timed out waiting for $what; state: ${manager.state.value.farm(farm.id)}", e)
        }
    }

    /** What the process holds, read after the collector has run. */
    private data class Usage(val fds: Int, val threads: Int, val heapKb: Long) {
        companion object {
            fun now(): Usage {
                repeat(3) {
                    Runtime.getRuntime().gc()
                    Thread.sleep(300)
                }
                val rt = Runtime.getRuntime()
                return Usage(count("/proc/self/fd"), count("/proc/self/task"), (rt.totalMemory() - rt.freeMemory()) / 1024)
            }

            private fun count(dir: String) = File(dir).list()?.size ?: -1
        }
    }

    private companion object {
        const val WARM = 5
        const val DROPS = 100
        const val MAX_MORE_FDS = 10
        const val MAX_MORE_THREADS = 10
        const val MAX_MORE_HEAP_KB = 16 * 1024L
    }
}
