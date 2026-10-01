package com.muvusoft.agentfarm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun buildLabelShowsVersion() {
        assertEquals("Version 0.1.0-b7", buildLabel("0.1.0-b7"))
    }
}
