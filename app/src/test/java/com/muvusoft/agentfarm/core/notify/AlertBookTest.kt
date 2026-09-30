package com.muvusoft.agentfarm.core.notify

import com.muvusoft.agentfarm.core.contract.AskClosed
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.FarmEventData
import com.muvusoft.agentfarm.core.contract.NeedsChanged
import com.muvusoft.agentfarm.core.contract.PermissionClosed
import com.muvusoft.agentfarm.core.contract.PermissionOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
import org.junit.Assert.assertEquals
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
    fun alertsThatDoNotWaitAreNotTracked() {
        val book = AlertBook()
        book.post("a", TurnFinished("s1", "developer", "bitti", ""))
        assertEquals(emptyList<String>(), book.settle("a", NeedsChanged(0)))
    }
}
