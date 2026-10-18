package com.muvusoft.agentfarm.core.handsfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PressesTest {
    @Test
    fun onePressIsDecidedOnlyOnceItsGapPassedAlone() {
        val p = Presses(500)
        assertNull(p.press(1_000))
        assertNull(p.due(1_499))
        assertEquals(Presses.Kind.ONE, p.due(1_500))
        assertNull(p.due(2_000))
    }

    @Test
    fun aSecondPressWithinTheGapIsTwoAndLeavesNothingDue() {
        val p = Presses(500)
        assertNull(p.press(1_000))
        assertEquals(Presses.Kind.TWO, p.press(1_300))
        assertNull(p.due(1_500))
        assertNull(p.due(5_000))
    }

    @Test
    fun aPressPastTheGapStartsANewCountAndAThirdPressStartsAnother() {
        val p = Presses(500)
        assertNull(p.press(1_000))
        assertNull(p.press(1_600))
        assertEquals(Presses.Kind.ONE, p.due(2_100))
        assertNull(p.press(3_000))
        assertEquals(Presses.Kind.TWO, p.press(3_200))
        assertNull(p.press(3_400))
        assertEquals(Presses.Kind.ONE, p.due(3_900))
    }
}
