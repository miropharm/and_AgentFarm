package com.muvusoft.agentfarm.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushWakeTest {
    @Test
    fun aWakeNamesAPairedFarm() {
        assertEquals("farm_7f3a", PushWake.farmOf(mapOf("wake" to "1", "farm" to "farm_7f3a"), listOf("farm_7f3a", "farm_b")))
    }

    @Test
    fun anythingElseWakesNothing() {
        val paired = listOf("farm_7f3a")
        assertNull(PushWake.farmOf(mapOf("wake" to "1", "farm" to "farm_other"), paired))
        assertNull(PushWake.farmOf(mapOf("wake" to "0", "farm" to "farm_7f3a"), paired))
        assertNull(PushWake.farmOf(mapOf("farm" to "farm_7f3a"), paired))
        assertNull(PushWake.farmOf(mapOf("wake" to "1"), paired))
        assertNull(PushWake.farmOf(emptyMap(), paired))
    }

    @Test
    fun aFarmThatKeepsTokensIsGivenOneOnlyWhenThePhoneHasOne() {
        assertTrue(PushWake.registers(listOf("remote.access", "remote.wake"), "tok"))
        assertFalse(PushWake.registers(listOf("remote.access"), "tok"))
        assertFalse(PushWake.registers(listOf("remote.wake"), null))
        assertFalse(PushWake.registers(listOf("remote.wake"), " "))
    }
}
