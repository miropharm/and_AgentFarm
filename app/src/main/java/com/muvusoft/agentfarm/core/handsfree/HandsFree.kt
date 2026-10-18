package com.muvusoft.agentfarm.core.handsfree

/** Where the headset mode stands: off, waiting for the microphone permission, without a headset, or ready to listen. */
enum class HandsFreeState { OFF, NEEDS_MICROPHONE, NO_HEADSET, READY }

/**
 * The headset mode's verdicts, from what the phone reports. The mode answers by voice with the phone in a pocket,
 * so it listens only through a headset: without one it waits, and what it reads never goes to the loudspeaker.
 */
object HandsFree {
    fun state(on: Boolean, microphone: Boolean, headset: Boolean): HandsFreeState = when {
        !on -> HandsFreeState.OFF
        !microphone -> HandsFreeState.NEEDS_MICROPHONE
        !headset -> HandsFreeState.NO_HEADSET
        else -> HandsFreeState.READY
    }

    /**
     * Whether the link service asks for the microphone as it comes to the foreground: only while the mode is on and
     * the permission granted, and only with the app on screen - Android refuses a microphone service started from the
     * background, and the link service is also started by a push. Started so, it goes on without the microphone.
     */
    fun asksMicrophone(on: Boolean, microphone: Boolean, visible: Boolean): Boolean = on && microphone && visible
}
