package com.muvusoft.agentfarm.core.handsfree

/**
 * The headset button's presses in the headset mode (S-10, S-11; design: the vault's Kulaklik Modu note): one press
 * asks how the farm is, two within [gapMs] start a new session by voice. A first press is decided only once the gap
 * after it passed with no second one ([due]); a third press starts a new count.
 */
class Presses(private val gapMs: Long = GAP_MS) {
    enum class Kind { ONE, TWO }

    private var first: Long? = null

    /** A press at [at]: [Kind.TWO] when it is the second within the gap, else null - [due] decides it later. */
    @Synchronized
    fun press(at: Long): Kind? {
        val f = first
        if (f != null && at - f <= gapMs) {
            first = null
            return Kind.TWO
        }
        first = at
        return null
    }

    /** At [at]: [Kind.ONE] once for a press whose gap passed alone, else null. */
    @Synchronized
    fun due(at: Long): Kind? {
        val f = first ?: return null
        if (at - f < gapMs) return null
        first = null
        return Kind.ONE
    }

    companion object {
        /** About what a headset's own double press takes (the system's own double tap is 300-500 ms). */
        const val GAP_MS = 500L
    }
}
