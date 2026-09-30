package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
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
        assertEquals(listOf("S1", "S2"), a.actions.map { it.label })
        val args = a.actions[0].args
        assertEquals("console.answer", a.actions[0].op)
        assertEquals("s1", args["id"]!!.jsonPrimitive.content)
        assertEquals("S1", args["answers"]!!.jsonObject["Hangisi?"]!!.jsonPrimitive.content)
        assertFalse(a.actions[0].unlock)
    }

    @Test
    fun aQuestionWithTooManyOptionsTakesATypedReply() {
        val a = Alerts.of("Masaüstü", ask(Alerts.MAX_BUTTONS + 1))!!
        assertEquals(1, a.actions.size)
        assertEquals("response", a.actions[0].replyArg)
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
    fun aQuestionIsNeverAnsweredWithTheApprovalOp() {
        assertTrue(Alerts.of("x", ask(2))!!.actions.none { it.op == "needs.act" })
        assertTrue(Alerts.of("x", ask(5))!!.actions.none { it.op == "needs.act" })
    }

    @Test
    fun aFinishedTurnIsQuietAndStateOnlyEventsMakeNoNotification() {
        assertEquals(Channel.TURN, Alerts.of("x", TurnFinished("s", "a", "t", "s"))!!.channel)
        assertNull(Alerts.of("x", SessionEnded("s", "a")))
    }
}
