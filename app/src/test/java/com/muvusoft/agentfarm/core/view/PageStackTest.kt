package com.muvusoft.agentfarm.core.view

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
    fun onlyTheNewestPagesAreKept() {
        var s = listOf("p0")
        for (i in 1..30) s = PageStack.open(s, "p$i")
        assertEquals(PageStack.MAX, s.size)
        assertEquals("p30", s.last())
        assertEquals("p11", s.first())
    }
}
