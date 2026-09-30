package com.muvusoft.agentfarm.core.power

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPolicyTest {
    @Test
    fun theTwoStatesReadDifferently() {
        assertNotEquals(BatteryPolicy.line(true).state, BatteryPolicy.line(false).state)
    }

    @Test
    fun theOptimizedStateTellsWhatToPick() {
        assertTrue(BatteryPolicy.line(false).detail.contains("Optimize etme"))
    }

    @Test
    fun everyStateOffersTheSettingsDoor() {
        listOf(true, false).forEach { assertTrue(BatteryPolicy.line(it).action.isNotBlank()) }
    }
}
