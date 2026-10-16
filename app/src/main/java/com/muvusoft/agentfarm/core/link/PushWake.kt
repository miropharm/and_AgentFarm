package com.muvusoft.agentfarm.core.link

/**
 * A farm wakes this phone through Firebase Cloud Messaging when something alerting happens and no
 * socket of this phone is open. The message carries nothing of the event, only `{ wake: "1",
 * farm: <id> }`: the phone connects and reads what it missed over its own link.
 */
object PushWake {
    /** The welcome feature a farm lists when it keeps wake tokens (whether it can send yet or not). */
    const val FEATURE = "remote.wake"

    /** The bridge op that hands a farm this phone's token; an empty token clears it. */
    const val REGISTER_OP = "remote.wake.register"

    /** The farm a wake names, when this phone is paired with it; anything else wakes nothing. */
    fun farmOf(data: Map<String, String>, paired: Collection<String>): String? =
        data["farm"]?.takeIf { data["wake"] == "1" && it in paired }

    /** Whether a farm whose welcome listed [features] is given [token]. */
    fun registers(features: Collection<String>, token: String?): Boolean = !token.isNullOrBlank() && FEATURE in features
}
