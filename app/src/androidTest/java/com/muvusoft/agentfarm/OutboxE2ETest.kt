package com.muvusoft.agentfarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.OutboxStore
import com.muvusoft.agentfarm.net.PairClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A call pressed while the farm is out of reach survives the process: the next one sends it. */
@RunWith(AndroidJUnit4::class)
class OutboxE2ETest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aCallWrittenOfflineReachesTheFarmAfterARestart() {
        val store = OutboxStore(ctx)
        store.save(emptyList())
        val farm = runBlocking {
            (PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) as PairingVerdict.Result.Paired).farm
        }

        val before = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val offline = ConnectionManager(before, saved = store.load(System.currentTimeMillis()), save = { store.save(it) })
        val id = offline.call(farm.id, "console.answer", buildJsonObject { put("id", JsonPrimitive("s_outbox")) })
        val end = System.currentTimeMillis() + 5_000
        while (store.load(System.currentTimeMillis()).none { it.id == id } && System.currentTimeMillis() < end) Thread.sleep(100)
        before.cancel()
        assertEquals(listOf(id), store.load(System.currentTimeMillis()).map { it.id })

        val after = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restarted = ConnectionManager(after, saved = store.load(System.currentTimeMillis()), save = { store.save(it) })
        try {
            restarted.setFarms(listOf(farm))
            val info = Pairing.parse(FakeHost.requireLink())!!
            val until = System.currentTimeMillis() + 20_000
            var calls = FakeHost.get(info, "/_test/state")
            while (!calls.contains(id) && System.currentTimeMillis() < until) { Thread.sleep(300); calls = FakeHost.get(info, "/_test/state") }
            assertTrue("the farm never received $id: $calls", calls.contains(id))
        } finally {
            restarted.stopAll()
            after.cancel()
            store.save(emptyList())
        }
    }
}
