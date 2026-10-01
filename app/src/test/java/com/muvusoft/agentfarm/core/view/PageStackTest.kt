package com.muvusoft.agentfarm.core.view

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageStackTest {
    @Test
    fun linksStackAndBackWalksThemBack() {
        val s = PageStack.open(PageStack.open(listOf("now"), "needs"), "console")
        assertEquals(listOf("now", "needs", "console"), s)
        assertEquals(listOf("now", "needs"), PageStack.back(s))
        assertEquals(listOf("now"), PageStack.back(PageStack.back(s)!!))
    }

    @Test
    fun backOnTheFirstPageLeavesTheFarm() {
        assertNull(PageStack.back(listOf("now")))
    }

    @Test
    fun openingThePageAlreadyShownChangesNothing() {
        assertEquals(listOf("now", "needs"), PageStack.open(listOf("now", "needs"), "needs"))
    }

    @Test
    fun onePageWithOtherArgumentsIsAnotherEntry() {
        // A chain's next leg opens over the leg before it; Back returns to that one.
        val one = ShellRequest.Open("session", buildJsonObject { put("sessionId", "sid-1") })
        val two = ShellRequest.Open("session", buildJsonObject { put("sessionId", "sid-2") })
        val s = PageStack.open(PageStack.open(listOf(ShellRequest.Open("sessions")), one), two)
        assertEquals(listOf(ShellRequest.Open("sessions"), one, two), s)
        assertEquals(s, PageStack.open(s, ShellRequest.Open("session", buildJsonObject { put("sessionId", "sid-2") })))
        assertEquals(one, PageStack.back(s)!!.last())
    }

    @Test
    fun onlyTheNewestPagesAreKept() {
        var s = listOf("p0")
        for (i in 1..30) s = PageStack.open(s, "p$i")
        assertEquals(PageStack.MAX, s.size)
        assertEquals("p30", s.last())
        assertEquals("p11", s.first())
    }
}
