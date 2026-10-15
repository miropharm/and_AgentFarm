package com.muvusoft.agentfarm

import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.LinkService
import com.muvusoft.agentfarm.net.PairClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** An event from the farm becomes a notification; pressing its button sends the op back to the farm. */
@RunWith(AndroidJUnit4::class)
class AlertE2ETest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = AgentFarmApp.of(ctx)

    private fun online(): String {
        val r = runBlocking { PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) }
        val farm = (r as PairingVerdict.Result.Paired).farm
        app.store.save(farm)
        LinkService.sync(ctx, anyFarm = true)
        runBlocking { withTimeout(20_000) { app.manager.state.first { it.farm(farm.id)?.link is Link.Online } } }
        return farm.id
    }

    private fun waitFor(tag: String): StatusBarNotification? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            nm.activeNotifications.firstOrNull { it.tag == tag }?.let { return it }
            Thread.sleep(250)
        }
        return null
    }

    private fun hostCalls(): String = FakeHost.get(Pairing.parse(FakeHost.requireLink())!!, "/_test/state")

    @Test
    fun aQuestionShowsItsOptionsAndAButtonAnswersIt() {
        online()
        val data = "{\"session\":\"s_ask\",\"agent\":\"developer\",\"askId\":\"a_e2e\",\"question\":\"Devam?\",\"options\":[{\"label\":\"Evet\"},{\"label\":\"Hayır\"}]}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.opened\",\"data\":$data}")
        val n = waitFor("ask:a_e2e")
        assertNotNull("no notification for the question", n)
        val actions = n!!.notification.actions
        assertEquals(listOf("Evet", "Hayır", "Stop"), actions.map { it.title.toString() })
        assertEquals("ask", n.notification.channelId)

        actions[0].actionIntent.send()
        val end = System.currentTimeMillis() + 10_000
        var calls = hostCalls()
        while (!calls.contains("s_ask") && System.currentTimeMillis() < end) { Thread.sleep(300); calls = hostCalls() }
        assertTrue("the farm did not receive the answer: $calls", calls.contains("\"op\":\"console.answer\"") && calls.contains("Evet"))
    }

    @Test
    fun aToolApprovalAsksForUnlockButARejectionDoesNot() {
        online()
        val data = "{\"session\":\"s_p\",\"agent\":\"developer\",\"permId\":\"p_e2e\",\"tool\":\"Bash\",\"summary\":\"git push\",\"key\":\"permission|s_p\"}"
        FakeHost.post("/_test/event", "{\"type\":\"permission.opened\",\"data\":$data}")
        val n = waitFor("perm:p_e2e")
        assertNotNull("no notification for the permission", n)
        val (allow, deny, stop) = n!!.notification.actions.toList()
        assertEquals("Allow", allow.title.toString())
        assertEquals("Stop", stop.title.toString())
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertTrue(allow.isAuthenticationRequired)
            assertTrue(!deny.isAuthenticationRequired)
            assertTrue("a stop cannot be undone: it asks for the unlock", stop.isAuthenticationRequired)
        }
        // The system asks for the unlock before it fires the button; fired here directly, the stop reaches the farm.
        stop.actionIntent.send()
        val end = System.currentTimeMillis() + 10_000
        var calls = hostCalls()
        while (!calls.contains("console.interrupt") && System.currentTimeMillis() < end) { Thread.sleep(300); calls = hostCalls() }
        assertTrue("the farm did not receive the stop: $calls", Regex("\"op\":\"console.interrupt\",\"args\":\\{\"id\":\"s_p\"").containsMatchIn(calls))
    }

    @Test
    fun aQuestionAboutToAnswerItselfComesBackWithItsButtonsAndTheTimeLeft() {
        online()
        val data = "{\"session\":\"s_soon\",\"agent\":\"developer\",\"askId\":\"a_soon\",\"question\":\"Sürüm çıksın mı?\",\"options\":[{\"label\":\"Evet\"},{\"label\":\"Hayır\"}]}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.opened\",\"data\":$data}")
        assertNotNull("no notification for the question", waitFor("ask:a_soon"))
        val soon = "{\"session\":\"card_soon\",\"agent\":\"developer\",\"askId\":\"a_soon\",\"dueAt\":1,\"minutes\":2}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.answersSoon\",\"data\":$soon}")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val end = System.currentTimeMillis() + 10_000
        fun title() = nm.activeNotifications.firstOrNull { it.tag == "ask:a_soon" }?.notification?.extras?.getCharSequence("android.title")?.toString().orEmpty()
        while (!title().startsWith("Answers itself") && System.currentTimeMillis() < end) Thread.sleep(250)
        assertTrue("the reminder did not reach the question: ${title()}", title().startsWith("Answers itself in 2 min"))
        val n = nm.activeNotifications.first { it.tag == "ask:a_soon" }
        assertEquals(listOf("Evet", "Hayır", "Stop"), n.notification.actions.map { it.title.toString() })
        assertEquals(1, nm.activeNotifications.count { it.tag == "ask:a_soon" })
    }

    @Test
    fun aWorkflowsGateIsAnsweredFromItsNotification() {
        online()
        val data = "{\"gateId\":\"wq_e2e\",\"run\":\"r_e2e\",\"workflow\":\"Yayın\",\"question\":\"Sürüm çıksın mı?\",\"options\":[\"Evet\",\"Bekle\"]}"
        FakeHost.post("/_test/event", "{\"type\":\"gate.opened\",\"data\":$data}")
        val n = waitFor("gate:wq_e2e")
        assertNotNull("no notification for the gate", n)
        assertEquals("ask", n!!.notification.channelId)
        val actions = n.notification.actions
        assertEquals(listOf("Evet", "Bekle"), actions.map { it.title.toString() })
        actions[1].actionIntent.send()
        val end = System.currentTimeMillis() + 10_000
        var calls = hostCalls()
        while (!calls.contains("wq_e2e") && System.currentTimeMillis() < end) { Thread.sleep(300); calls = hostCalls() }
        assertTrue("the farm did not receive the answer: $calls", Regex("\"op\":\"workflows.answer\",\"args\":\\{\"id\":\"wq_e2e\",\"option\":2\\}").containsMatchIn(calls))
    }

    @Test
    fun aQuestionAnsweredAtTheDeskLeavesThePhone() {
        online()
        val data = "{\"session\":\"s_stale\",\"agent\":\"developer\",\"askId\":\"a_stale\",\"question\":\"Sürsün mü?\",\"options\":[{\"label\":\"Evet\"}]}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.opened\",\"data\":$data}")
        assertNotNull("no notification for the question", waitFor("ask:a_stale"))
        FakeHost.post("/_test/event", "{\"type\":\"needs.changed\",\"data\":{\"count\":0}}")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val end = System.currentTimeMillis() + 10_000
        while (nm.activeNotifications.any { it.tag == "ask:a_stale" } && System.currentTimeMillis() < end) Thread.sleep(250)
        assertTrue("the answered question still shows", nm.activeNotifications.none { it.tag == "ask:a_stale" })
    }
}
