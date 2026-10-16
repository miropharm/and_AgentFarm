package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.contract.ReadPage
import com.muvusoft.agentfarm.core.contract.ReadStep
import com.muvusoft.agentfarm.core.state.Link
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The Conversation is built from sessions.read pages a page at a time; a copy is the same pages kept.
class ConversationBuildTest {
    private val link = Link.Online("192.168.1.20:7300", "manage", emptyMap(), 0)
    private val empty = ConversationState("c_dev", "developer", "Testler", "claude", link, 0, loading = true)

    private fun step(i: Int, role: String = if (i % 2 == 1) "user" else "assistant", key: String = "k$i", text: String = "m$i") =
        ReadStep(i, key, 1, role, 1000L + i, text)

    private fun page(range: IntRange, total: Int = range.last, key: (Int) -> String = { "k$it" }) =
        ReadPage("s-1", "f", "claude", "Testler", "developer", 1, total, range.first, range.count(), null, 0, range.map { step(it, key = key(it)) })

    private fun ids(s: ConversationState) = s.messages.map { it.i }

    @Test
    fun theLastPageOpensTheConversationAndSaysWhetherMoreIsAbove() {
        val s = ConversationBuild.tail(empty, page(71..100))
        assertEquals((71..100).toList(), ids(s))
        assertTrue(s.older)
        assertFalse(s.loading)
        assertFalse(ConversationBuild.tail(empty, page(1..12)).older)
    }

    @Test
    fun theLastPageReadAgainKeepsTheRowsAboveWhenItReachesThem() {
        val read = ConversationBuild.older(ConversationBuild.tail(empty, page(71..100)), page(41..70))
        val again = ConversationBuild.tail(read, page(81..110, total = 110))
        assertEquals((41..110).toList(), ids(again))
        val next = ConversationBuild.tail(read, page(101..130, total = 130))
        assertEquals("a page right after the last row joins it", (41..130).toList(), ids(next))
    }

    @Test
    fun aPagePastAGapOrWithOtherStepsInTheSamePlacesStartsTheListAgain() {
        val read = ConversationBuild.tail(empty, page(1..30))
        assertEquals((71..100).toList(), ids(ConversationBuild.tail(read, page(71..100))))
        val rewritten = ConversationBuild.tail(read, page(11..40) { "other$it" })
        assertEquals((11..40).toList(), ids(rewritten))
        assertTrue(rewritten.older)
    }

    @Test
    fun aLiveRowThePageCarriesGoesAndANewerOneStays() {
        val read = ConversationBuild.tail(empty, page(1..10))
        val told = read.copy(messages = read.messages + Message(0, 1, Role.ASSISTANT, 1011, "m11", key = "k11", provisional = true) +
            Message(0, 1, Role.TOOL, 5000, tool = "Bash", key = "live", provisional = true))
        val s = ConversationBuild.tail(told, page(1..11))
        assertEquals((1..12).toList(), ids(s))
        assertEquals(listOf(false, true), s.messages.takeLast(2).map { it.provisional })
        assertEquals("live", s.messages.last().key)
    }

    @Test
    fun anOlderPageGoesAboveAndNoRowIsDrawnTwice() {
        val s = ConversationBuild.tail(empty, page(31..60))
        val args = ConversationBuild.olderArgs(s, "s-1")!!
        assertEquals(1, args.getValue("from").jsonPrimitive.int)
        assertEquals(30, args.getValue("limit").jsonPrimitive.int)
        val more = ConversationBuild.older(s, page(1..35))
        assertEquals((1..60).toList(), ids(more))
        assertFalse(more.older)
        assertNull("nothing above the first step", ConversationBuild.olderArgs(more, "s-1"))
        val short = ConversationBuild.olderArgs(ConversationBuild.tail(empty, page(11..40)), "s-1")!!
        assertEquals(listOf(1, 10), listOf(short.getValue("from").jsonPrimitive.int, short.getValue("limit").jsonPrimitive.int))
    }

    @Test
    fun noReadAsksForMoreThanAPage() {
        val s = ConversationBuild.tail(empty, page(1..30))
        for (a in listOf(ConversationBuild.tailArgs("s-1"), ConversationBuild.newArgs(s, "s-1"), ConversationBuild.olderArgs(ConversationBuild.tail(empty, page(71..100)), "s-1")!!)) {
            assertTrue(a.toString(), a.getValue("limit").jsonPrimitive.int <= ConversationBuild.PAGE)
        }
        assertEquals(-ConversationBuild.PAGE, ConversationBuild.tailArgs("s-1").getValue("from").jsonPrimitive.int)
        assertEquals("what was written after the last row read", 31, ConversationBuild.newArgs(s, "s-1").getValue("from").jsonPrimitive.int)
    }

    @Test
    fun aStepReadWholeTakesThePlaceOfItsCutCopy() {
        val cut = page(1..2).let { p -> p.copy(messages = p.messages.map { if (it.i == 2) it.copy(text = "kes", clipped = 9000) else it }) }
        val s = ConversationBuild.tail(empty, cut)
        val m = s.messages.last()
        assertEquals(0, ConversationBuild.wholeArgs("s-1", m).getValue("maxChars").jsonPrimitive.int)
        val whole = ConversationBuild.whole(s, page(2..2).let { p -> p.copy(messages = listOf(p.messages.single().copy(text = "x".repeat(9000)))) })
        assertEquals(9000, whole.messages.last().text!!.length)
        assertNull(whole.messages.last().clipped)
    }

    @Test
    fun aSentMessageGoesWhenTheFarmShowsItBackAndAnEarlierSameMessageIsNotItsEcho() {
        val read = ConversationBuild.tail(empty, page(1..2).let { p -> p.copy(messages = listOf(p.messages[0].copy(text = "devam"), p.messages[1])) })
        val seen = ConversationBuild.seen(read, " devam ")
        assertEquals(1, seen)
        val sent = read.copy(outgoing = listOf(Outgoing("o1", "devam", SendState.SENDING, seen)))
        assertEquals("the old 'devam' is not the echo", 1, ConversationBuild.echoed(sent).outgoing.size)
        val back = sent.copy(messages = sent.messages + Message(0, 2, Role.USER, 2000, "devam", key = "u3", provisional = true))
        assertTrue(ConversationBuild.echoed(back).outgoing.isEmpty())
    }

    @Test
    fun aCopyIsTheReadStepsKeptAndDrawsTheSameAsThePage() {
        val s = ConversationBuild.tail(empty, page(1..150))
        val told = s.copy(messages = s.messages + Message(0, 2, Role.TOOL, 9000, tool = "Bash", key = "live", provisional = true))
        val copy = ConversationCopy.of(42, ConversationKey("c_dev", "s-1", "developer", "Testler"), told)
        assertEquals(ConversationCopy.KEEP, copy.steps.size)
        val back = ConversationCopy.decode(copy.encode())
        assertNotNull(back)
        val drawn = back!!.state(link)
        assertEquals(s.messages.takeLast(ConversationCopy.KEEP), drawn.messages)
        assertTrue(drawn.older)
        assertEquals(42L, drawn.updatedAt)
        assertNull(ConversationCopy.decode("{}"))
    }
}
