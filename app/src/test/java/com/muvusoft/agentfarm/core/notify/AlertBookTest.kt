package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskAnswersSoon
import com.muvusoft.agentfarm.core.contract.AskClosed
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.GateClosed
import com.muvusoft.agentfarm.core.contract.GateOpened
import com.muvusoft.agentfarm.core.contract.NeedsChanged
import com.muvusoft.agentfarm.core.contract.PermissionClosed
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertBookTest {
    private fun ask(session: String, id: String) = AskOpened(session, "developer", id, "Devam?", listOf(AskOption("Evet")))

    private fun AlertBook.post(farm: String, e: FarmEventData) = posted(farm, e, Alerts.of(farm, e)!!)

    @Test
    fun anEmptyWaitListTakesDownOnlyThatFarmsAlerts() {
        val book = AlertBook()
        book.post("a", ask("s1", "q1"))
        book.post("a", ask("s2", "q2"))
        book.post("b", ask("s3", "q3"))
        assertEquals(emptyList<String>(), book.settle("a", NeedsChanged(1)))
        assertEquals(listOf("ask:q1", "ask:q2"), book.settle("a", NeedsChanged(0)))
        assertEquals(emptyList<String>(), book.settle("a", NeedsChanged(0)))
        assertEquals(listOf("ask:q3"), book.settle("b", NeedsChanged(0)))
    }

    @Test
    fun anEndedSessionTakesDownItsOwnAlerts() {
        val book = AlertBook()
        book.post("a", ask("s1", "q1"))
        book.post("a", ask("s2", "q2"))
        assertEquals(listOf("ask:q1"), book.settle("a", SessionEnded("s1", "developer")))
        assertEquals(emptyList<String>(), book.settle("b", SessionEnded("s2", "developer")))
    }

    @Test
    fun aQuestionAnsweredElsewhereTakesDownOnlyItsOwnAlert() {
        val book = AlertBook()
        book.post("a", ask("s1", "q1"))
        book.post("a", ask("s1", "q2"))
        assertEquals(listOf("ask:q1"), book.settle("a", AskClosed("s1", "q1")))
        assertEquals(emptyList<String>(), book.settle("a", AskClosed("s1", "q1")))
        assertEquals(emptyList<String>(), book.settle("b", AskClosed("s1", "q2")))
        assertEquals(listOf("ask:q2"), book.settle("a", NeedsChanged(0)))
    }

    @Test
    fun aPermissionAnsweredElsewhereTakesDownItsAlert() {
        val book = AlertBook()
        val perm = PermissionOpened("s1", "developer", "permission|s1", "Bash", "git status", "permission|s1")
        book.post("a", perm)
        book.post("a", ask("s1", "q1"))
        assertEquals(listOf("perm:permission|s1"), book.settle("a", PermissionClosed("s1", "permission|s1")))
        assertEquals(emptyList<String>(), book.settle("a", NeedsChanged(1)))
    }

    @Test
    fun aQuestionAboutToAnswerItselfShowsItsOwnNotificationAgainWithTheTimeLeft() {
        val book = AlertBook()
        book.post("a", ask("s1", "q1"))
        val soon = AskAnswersSoon("card-1", "developer", "q1", dueAt = 1, minutes = 2)
        val r = book.alertFor("a", "Masaüstü", soon)!!
        assertEquals("ask:q1", r.tag)
        assertEquals(listOf("Evet", "Stop"), r.actions.map { it.label })
        assertTrue(r.title, r.title.startsWith("Answers itself in 2 min · developer is asking"))
        assertTrue("a reminder sounds again", r.renotify)
        book.posted("a", soon, r)
        // The record stays the question's: its session still takes it down, and a second reminder does not stack.
        assertTrue(book.alertFor("a", "Masaüstü", soon)!!.title.indexOf("Answers itself") == book.alertFor("a", "Masaüstü", soon)!!.title.lastIndexOf("Answers itself"))
        assertEquals(listOf("ask:q1"), book.settle("a", SessionEnded("s1", "developer")))
    }

    @Test
    fun aReminderForAQuestionThisPhoneNeverSawStandsAlone() {
        val book = AlertBook()
        val r = book.alertFor("a", "Masaüstü", AskAnswersSoon("card-1", "developer", "q9", dueAt = 1, minutes = 5))!!
        assertEquals("ask:q9", r.tag)
        assertTrue(r.title, r.title.contains("developer's question answers itself in 5 min · Masaüstü"))
        assertTrue(r.actions.isEmpty() && r.renotify)
        book.posted("a", AskAnswersSoon("card-1", "developer", "q9", dueAt = 1, minutes = 5), r)
        assertEquals(listOf("ask:q9"), book.settle("a", AskClosed("card-1", "q9")))
    }

    @Test
    fun aGateLeavesWithItsOwnCloseOrAnEmptyWaitListNeverWithASession() {
        val book = AlertBook()
        book.post("a", GateOpened("wq1", "r1", "Yayın", "Sürsün mü?", listOf("Evet")))
        assertEquals(emptyList<String>(), book.settle("a", SessionEnded("r1", "developer")))
        assertEquals(listOf("gate:wq1"), book.settle("a", GateClosed("wq1")))
        book.post("a", GateOpened("wq2", "r1", "Yayın", "Sürsün mü?", listOf("Evet")))
        assertEquals(listOf("gate:wq2"), book.settle("a", NeedsChanged(0)))
    }

    @Test
    fun alertsThatDoNotWaitAreNotTracked() {
        val book = AlertBook()
        book.post("a", TurnFinished("s1", "developer", "bitti", ""))
        assertEquals(emptyList<String>(), book.settle("a", NeedsChanged(0)))
    }
}
