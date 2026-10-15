package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.GateOpened
import com.muvusoft.agentfarm.core.contract.MissionEnded
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.Quiet
import com.muvusoft.agentfarm.core.contract.QuotaWarn
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.state.Outbox
import com.muvusoft.agentfarm.core.state.OutboxItem
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private fun ask(n: Int) = AskOpened("s1", "developer", "a1", "Hangisi?", (1..n).map { AskOption("S$it") })

    @Test
    fun aQuestionWithFewOptionsGetsOneButtonPerOption() {
        val a = Alerts.of("Masaüstü", ask(2))!!
        assertEquals(Channel.ASK, a.channel)
        assertEquals(listOf("S1", "S2", "Stop"), a.actions.map { it.label })
        val args = a.actions[0].args
        assertEquals("console.answer", a.actions[0].op)
        assertEquals("s1", args["id"]!!.jsonPrimitive.content)
        assertEquals("S1", args["answers"]!!.jsonObject["Hangisi?"]!!.jsonPrimitive.content)
        assertFalse(a.actions[0].unlock)
    }

    @Test
    fun aQuestionWithTooManyOptionsTakesATypedReply() {
        val a = Alerts.of("Masaüstü", ask(Alerts.MAX_BUTTONS + 1))!!
        assertEquals(listOf("Reply", "Stop"), a.actions.map { it.label })
        assertEquals("response", a.actions[0].replyArg)
    }

    @Test
    fun aWaitingTurnCanBeStoppedFromItsNotificationOnlyAfterTheUnlockAndOnlyWhereAButtonIsLeft() {
        for (a in listOf(Alerts.of("x", ask(1))!!, Alerts.of("x", ask(5))!!, Alerts.of("x", PermissionOpened("s1", "developer", "p1", "Bash", "rm -rf build", "k"))!!)) {
            val stop = a.actions.last()
            assertEquals("console.interrupt", stop.op)
            assertEquals("s1", stop.args["id"]!!.jsonPrimitive.content)
            assertTrue("a stop cannot be undone", stop.unlock)
            assertTrue(a.actions.size <= Alerts.MAX_BUTTONS)
        }
        // Three options fill the buttons Android shows: the answers keep them; the app's own stop remains.
        assertEquals(listOf("S1", "S2", "S3"), Alerts.of("x", ask(Alerts.MAX_BUTTONS))!!.actions.map { it.label })
    }

    @Test
    fun aToolApprovalNeedsTheDeviceUnlockedButARejectionDoesNot() {
        val a = Alerts.of("Masaüstü", PermissionOpened("s1", "developer", "p1", "Bash", "git push", "permission|s1-full"))!!
        val (allow, deny) = a.actions
        assertEquals("needs.act", allow.op)
        assertEquals("permission|s1-full", allow.args["key"]!!.jsonPrimitive.content)
        assertEquals("permission-allow", allow.args["action"]!!.jsonPrimitive.content)
        assertTrue(allow.unlock)
        assertEquals("permission-deny", deny.args["action"]!!.jsonPrimitive.content)
        assertFalse(deny.unlock)
    }

    @Test
    fun aWorkflowsGateAsksLikeAQuestionAndIsAnsweredByItsOptionNumber() {
        val a = Alerts.of("Masaüstü", GateOpened("wq1", "r1", "Yayın", "Sürüm çıksın mı?", listOf("Evet", "Hayır")))!!
        assertEquals(Channel.ASK, a.channel)
        assertEquals("gate:wq1", a.tag)
        assertEquals(listOf("Evet", "Hayır"), a.actions.map { it.label })
        assertTrue(a.actions.all { it.op == "workflows.answer" && it.args["id"]!!.jsonPrimitive.content == "wq1" })
        assertEquals(listOf("1", "2"), a.actions.map { it.args["option"]!!.jsonPrimitive.content })
        val free = Alerts.of("x", GateOpened("wq2", "r1", "Yayın", "Ne yazalım?", emptyList()))!!
        assertEquals("answer", free.actions.single().replyArg)
        assertTrue("a gate has no session to stop", free.actions.none { it.op == "console.interrupt" })
    }

    @Test
    fun aMissionsEndIsATurnAndItsFailureANotice() {
        assertEquals(Channel.TURN, Alerts.of("x", MissionEnded("m1", "Rapor", "done"))!!.channel)
        val failed = Alerts.of("x", MissionEnded("m2", "Rapor", "failed"))!!
        assertEquals(Channel.NOTICE, failed.channel)
        assertTrue(failed.title, failed.title.startsWith("Mission failed"))
    }

    @Test
    fun aQuestionIsNeverAnsweredWithTheApprovalOp() {
        assertTrue(Alerts.of("x", ask(2))!!.actions.none { it.op == "needs.act" })
        assertTrue(Alerts.of("x", ask(5))!!.actions.none { it.op == "needs.act" })
    }

    @Test
    fun aNoticeGoesToTheChannelOfItsImportance() {
        fun channel(level: Long) = Alerts.of("x", NoticePosted("n1", "Rapor hazır", level, "developer"))!!.channel
        assertEquals(Channel.ROUTINE, channel(1))
        assertEquals(Channel.NOTICE, channel(2))
        assertEquals(Channel.IMPORTANT, channel(3))
        assertEquals(Channel.IMPORTANT, channel(4))
        assertEquals(Channel.ROUTINE, channel(0))
        assertEquals(Channel.NOTICE, Alerts.of("x", QuotaWarn("claude", 91.0, "5h"))!!.channel)
    }

    @Test
    fun theFarmsMuteAndAMutedSessionSilenceTheAlertButQuietHoursAloneDoNot() {
        fun silent(vararg reasons: String) =
            Alerts.of("x", ask(2).copy(quiet = Quiet(reasons.toList(), "muted on the farm")))!!.silent
        assertTrue(silent("fleet"))
        assertTrue(silent("session"))
        assertTrue(silent("quiet-hours", "session"))
        assertFalse(silent("quiet-hours"))
        assertFalse(silent())
        assertFalse(Alerts.of("x", ask(2))!!.silent)
        assertTrue(Alerts.of("x", TurnFinished("s", "a", "t", "s", quiet = Quiet(listOf("fleet"))))!!.silent)
        assertTrue(Alerts.of("x", NoticePosted("n", "t", 4, "a", quiet = Quiet(listOf("fleet"))))!!.silent)
    }

    @Test
    fun aFinishedTurnIsQuietAndStateOnlyEventsMakeNoNotification() {
        assertEquals(Channel.TURN, Alerts.of("x", TurnFinished("s", "a", "t", "s"))!!.channel)
        assertNull(Alerts.of("x", SessionEnded("s", "a")))
    }

    @Test
    fun aQueuedSharesReceiptIsQuietWhenDeliveredAndANoticeWhenNot() {
        val item = OutboxItem("c_9", "farm_a", "console.send", null, 1, receipt = "developer")
        val sent = Alerts.receipt(item, "Masaüstü", CallResult("c_9", true))
        assertEquals(Channel.ROUTINE, sent.channel)
        assertEquals("sent:c_9", sent.tag)
        assertEquals("Delivered to developer · Masaüstü", sent.title)
        assertTrue(sent.actions.isEmpty())
        val dropped = Alerts.receipt(item, "Masaüstü", null)
        assertEquals(Channel.NOTICE, dropped.channel)
        assertTrue(dropped.text, dropped.text.contains("${Outbox.MAX_AGE_HOURS} h"))
        assertEquals("Not sent to farm_a · Masaüstü", Alerts.receipt(item.copy(receipt = null), "Masaüstü", null).title)
    }
}
