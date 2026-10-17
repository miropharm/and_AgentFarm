package com.muvusoft.agentfarm

import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.power.LinkRest
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.LinkService
import com.muvusoft.agentfarm.net.PairClient
import com.muvusoft.agentfarm.net.PushWakeService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The battery policy end to end: with the app off screen and nothing running or waiting on a farm that can wake the
 * phone, the link rests (the farm sees no socket); in deep idle a question is asked, and a push wake brings it in as a
 * notification. The emulator has no Play services, so the token is a stand-in and the wake is the handler FCM calls.
 */
@RunWith(AndroidJUnit4::class)
class RestE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val app = AgentFarmApp.of(ctx)

    @Before
    fun quietFarm() {
        FakeHost.post("/_test/scenario", "{\"profile\":\"none\",\"quiet\":true}")
        app.prefs.pushToken = STAND_IN
    }

    @After
    fun loudAgain() {
        app.prefs.pushToken = null
        shell("dumpsys deviceidle unforce")
        shell("dumpsys battery reset")
        FakeHost.post("/_test/scenario", "{\"profile\":\"none\"}")
    }

    /** Runs a shell command as the shell user and waits for it to finish. */
    private fun shell(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun online(): String {
        val r = runBlocking { PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) }
        val farm = (r as PairingVerdict.Result.Paired).farm
        app.store.save(farm)
        LinkService.sync(ctx, anyFarm = true)
        runBlocking { withTimeout(20_000) { app.manager.state.first { it.farm(farm.id)?.link is Link.Online } } }
        return farm.id
    }

    private fun sockets(): Int = Regex("\"sockets\"\\s*:\\s*(\\d+)").find(FakeHost.get("/_test/state"))!!.groupValues[1].toInt()

    @Test
    fun anIdleLinkRestsAndAPushWakeBringsAQuestionInFromDeepIdle() {
        val farm = online()
        val since = SystemClock.elapsedRealtime()
        val rested = runBlocking {
            withTimeoutOrNull(LinkRest.GRACE_MS + 60_000) { app.manager.state.first { it.farm(farm)?.link is Link.Resting } }
        }
        assertNotNull("the idle link did not rest: ${app.manager.state.value.farm(farm)}", rested)
        Log.i(TAG, "rest: the idle link rested ${SystemClock.elapsedRealtime() - since} ms after it came online")
        val end = SystemClock.elapsedRealtime() + 10_000
        while (sockets() > 0 && SystemClock.elapsedRealtime() < end) Thread.sleep(250)
        assertEquals("the farm still sees a socket of the resting phone", 0, sockets())

        shell("dumpsys deviceidle enable deep")
        shell("dumpsys battery unplug")
        shell("dumpsys deviceidle force-idle deep")
        Log.i(TAG, "rest: deep state ${shell("dumpsys deviceidle get deep").trim()}")
        val data = "{\"session\":\"s_rest\",\"agent\":\"developer\",\"askId\":\"a_rest\",\"question\":\"Uyandın mı?\",\"options\":[{\"label\":\"Evet\"}]}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.opened\",\"data\":$data}")
        val woke = SystemClock.elapsedRealtime()
        assertTrue("the wake names a paired farm", PushWakeService.wake(ctx, mapOf("wake" to "1", "farm" to farm)))

        val nm = ctx.getSystemService(NotificationManager::class.java)
        val until = woke + 20_000
        var shown = false
        while (!shown && SystemClock.elapsedRealtime() < until) {
            shown = nm.activeNotifications.any { it.tag == "ask:a_rest" }
            if (!shown) Thread.sleep(100)
        }
        val ms = SystemClock.elapsedRealtime() - woke
        Log.i(TAG, "rest: the question was shown ${if (shown) "$ms ms" else "NOT within 20 s"} after the wake, in deep idle")
        assertTrue("the question did not arrive after the wake", shown)
        assertTrue("a wake brings the question in within $WAKE_TARGET_MS ms: $ms ms", ms <= WAKE_TARGET_MS)
    }

    private companion object {
        const val TAG = "AFRest"
        const val STAND_IN = "ci-stand-in-token"
        /** A rested link reconnects, replays what it missed and posts it: a few seconds on the emulator's loopback. */
        const val WAKE_TARGET_MS = 10_000L
    }
}
