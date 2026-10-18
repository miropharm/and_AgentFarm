package com.muvusoft.agentfarm.core.handsfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeTest {
    @Test
    fun theModeIsReadyOnlyWithTheMicrophoneAndAHeadset() {
        assertEquals(HandsFreeState.OFF, HandsFree.state(on = false, microphone = true, headset = true))
        assertEquals(HandsFreeState.NEEDS_MICROPHONE, HandsFree.state(on = true, microphone = false, headset = true))
        assertEquals(HandsFreeState.NO_HEADSET, HandsFree.state(on = true, microphone = true, headset = false))
        assertEquals(HandsFreeState.READY, HandsFree.state(on = true, microphone = true, headset = true))
    }

    @Test
    fun theServiceAsksForTheMicrophoneOnlyFromTheForegroundWithTheModeOnAndThePermission() {
        assertTrue(HandsFree.asksMicrophone(on = true, microphone = true, visible = true))
        assertFalse("a push starts the service from the background", HandsFree.asksMicrophone(on = true, microphone = true, visible = false))
        assertFalse(HandsFree.asksMicrophone(on = true, microphone = false, visible = true))
        assertFalse(HandsFree.asksMicrophone(on = false, microphone = true, visible = true))
    }
}
