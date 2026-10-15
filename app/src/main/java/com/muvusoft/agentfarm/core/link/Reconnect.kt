package com.muvusoft.agentfarm.core.link

/** When and where the next connection attempt to a farm goes. Pure: the caller brings the clock and the dice. */
object Reconnect {
    const val FIRST_MS = 1_000L
    const val MAX_MS = 60_000L

    /** Delays between tries after a Wake-on-LAN: a PC takes seconds to a minute to come back on the network. */
    val AFTER_WAKE_MS = listOf(8_000L, 12_000L, 20_000L, 30_000L)

    /** Exponential wait before attempt `attempt` (1 = first retry), capped, with up to +25% jitter from `random01`. */
    fun delayMs(attempt: Int, random01: Double): Long {
        if (attempt <= 0) return 0
        val base = (FIRST_MS shl (attempt - 1).coerceAtMost(16)).coerceAtMost(MAX_MS)
        return base + (base * 0.25 * random01.coerceIn(0.0, 1.0)).toLong()
    }

    /**
     * The addresses in the order to try: where the farm answered on the local network just now (`found`),
     * then the last one that worked, then the link's own order.
     */
    fun order(addresses: List<String>, lastGood: String?, found: String?): List<String> {
        val known = if (lastGood != null && lastGood in addresses) listOf(lastGood) + (addresses - lastGood) else addresses
        return if (found == null) known else listOf(found) + (known - found)
    }

    /** What happened to the phone's default network. */
    enum class Net { AVAILABLE, SWITCHED, LOST }

    /** What a link does about it: nothing, try now instead of waiting out its backoff, or drop its socket. */
    enum class Nudge { NONE, RETRY_NOW, RECONNECT }

    /**
     * A link waiting out its backoff tries at once when a network comes; a link that is up drops its socket
     * when the default network changed under it (Wi-Fi to mobile and back): the old route is dead, and a ping
     * would only notice that after its timeout. The replay of what was missed comes with the next hello.
     */
    fun onNetwork(net: Net, online: Boolean): Nudge = when {
        net == Net.LOST -> Nudge.NONE
        !online -> Nudge.RETRY_NOW
        net == Net.SWITCHED -> Nudge.RECONNECT
        else -> Nudge.NONE
    }

    /** Whether a refusal is final (retrying cannot help) or the link should keep trying. */
    fun isFinal(refuseReason: String): Boolean = refuseReason in FINAL

    private val FINAL = setOf("revoked", "unknown-device", "bad-signature", "contract")
}
