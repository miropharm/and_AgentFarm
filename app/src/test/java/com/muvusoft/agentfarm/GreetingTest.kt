package com.muvusoft.agentfarm

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun buildLabelShowsVersion() {
        assertEquals("Sürüm 0.0.1-b7", buildLabel("0.0.1-b7"))
    }
}
