package com.muvusoft.agentfarm.core.link

/** When and where the next connection attempt to a farm goes. Pure: the caller brings the clock and the dice. */
object Reconnect {
    const val FIRST_MS = 1_000L
    const val MAX_MS = 60_000L

    /** Exponential wait before attempt `attempt` (1 = first retry), capped, with up to +25% jitter from `random01`. */
    fun delayMs(attempt: Int, random01: Double): Long {
        if (attempt <= 0) return 0
        val base = (FIRST_MS shl (attempt - 1).coerceAtMost(16)).coerceAtMost(MAX_MS)
        return base + (base * 0.25 * random01.coerceIn(0.0, 1.0)).toLong()
    }

    /** The addresses in the order to try: the last one that worked first, then the link's own order. */
    fun order(addresses: List<String>, lastGood: String?): List<String> =
        if (lastGood != null && lastGood in addresses) listOf(lastGood) + (addresses - lastGood) else addresses

    /** Whether a refusal is final (retrying cannot help) or the link should keep trying. */
    fun isFinal(refuseReason: String): Boolean = refuseReason in FINAL

    private val FINAL = setOf("revoked", "unknown-device", "bad-signature", "contract")
}
