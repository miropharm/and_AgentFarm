package com.muvusoft.agentfarm.core.session

import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.Recent
import com.muvusoft.agentfarm.core.state.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private fun q(vararg labels: String, multi: Boolean = false) =
        AskQuestion("Hangisi?", multiSelect = multi, options = labels.map { AskOption(it) })

    @Test
    fun aPermissionAndAShortOnePickQuestionAreAnsweredFromTheRow() {
        assertEquals(listOf(Quick.Allow, Quick.Deny), Asks.quick(Ask(AskKind.PERMISSION, "rm -rf build", "Bash", since = 0)))
        assertEquals(
            listOf(Quick.Option("Evet"), Quick.Option("Hayır")),
            Asks.quick(Ask(AskKind.QUESTION, "", questions = listOf(q("Evet", "Hayır")), since = 0)),
        )
    }

    @Test
    fun anythingLongerOpensTheConversation() {
        fun quick(vararg qs: AskQuestion) = Asks.quick(Ask(AskKind.QUESTION, "", questions = qs.toList(), since = 0))
        assertEquals(emptyList<Quick>(), quick(q("a", "b", "c", "d")))
        assertEquals(emptyList<Quick>(), quick(q("a", "b", multi = true)))
        assertEquals(emptyList<Quick>(), quick(q("a"), q("b", "c")))
        assertEquals(emptyList<Quick>(), quick(q("Önce testleri çalıştır, sonra yayınla", "Hayır")))
        assertEquals(emptyList<Quick>(), quick(AskQuestion("Ne yapayım?")))
        assertEquals(emptyList<Quick>(), Asks.quick(Ask(AskKind.APPROVAL, "workflow step", since = 0)))
    }

    @Test
    fun theRowShowsTheFirstQuestionsFirstLine() {
        assertEquals("Hangisi?", Asks.firstLine(Ask(AskKind.QUESTION, "detail", questions = listOf(q("a", "b")), since = 0)))
        assertEquals("git push", Asks.firstLine(Ask(AskKind.PERMISSION, "\n  git push  \n--force", since = 0)))
    }

    @Test
    fun stepAndTurnWords() {
        assertEquals("Bash: npm test · 12 s", SessionText.step(Step("Bash", "npm test\n--watch", 1_000), 13_000))
        assertEquals("Read · 2 min", SessionText.step(Step("Read", null, 0), 120_000))
        assertEquals("3 min · $0.42", SessionText.turn(0, 0.4249, 180_000))
        assertEquals("5 s", SessionText.turn(0, null, 5_000))
        assertEquals("<$0.01", SessionText.usd(0.004))
        assertEquals("$0.00", SessionText.usd(0.0))
    }

    @Test
    fun aCopyThatIsNotLiveSaysHowOldItIs() {
        assertNull(SessionText.stale(Link.Online("10.0.0.2:7300", "manage", emptyMap(), 0), 0, 1))
        assertEquals("Offline · updated 12 min ago", SessionText.stale(Link.Offline("closed", 0), 0 + 1, 12 * 60_000 + 1))
        assertEquals("Connecting · updated 5 s ago", SessionText.stale(Link.Connecting("x", 2, 0), 1_000, 6_000))
        assertEquals("Offline · nothing saved yet", SessionText.stale(Link.Refused("busy", null, 0), 0, 9))
    }

    @Test
    fun nowIsEmptyOnlyWhenNothingIsComingEither() {
        val s = NowState("Ev PC", Link.Offline("closed", 0), 0)
        assertTrue(s.empty)
        assertFalse(s.copy(loading = true).empty)
        assertFalse(s.copy(error = "the farm did not answer").empty)
        assertFalse(s.copy(recent = listOf(Recent("c1", "developer", "t", "bitti", 0))).empty)
    }
}
