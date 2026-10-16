package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.contract.WakeNic
import com.muvusoft.agentfarm.core.state.Link

/**
 * Wake-on-LAN for a sleeping farm PC: the magic packet (6 x FF, then the MAC 16 times) sent as a UDP
 * broadcast to port 9 of each network card the farm named in its last welcome. It only reaches a PC on
 * the phone's own network, and only one whose card and firmware are set to wake on it.
 */
object Wake {
    const val PORT = 9
    const val EVERYWHERE = "255.255.255.255"
    private val MAC = Regex("^[0-9a-fA-F]{2}([:-][0-9a-fA-F]{2}){5}$")

    /** The magic packet for a MAC written aa:bb:cc:dd:ee:ff or aa-bb-…; null for anything else, or all zeros. */
    fun packet(mac: String): ByteArray? {
        val m = mac.trim()
        if (!MAC.matches(m)) return null
        val bytes = m.split(':', '-').map { it.toInt(16).toByte() }
        if (bytes.all { it == 0.toByte() }) return null
        return ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten().toByteArray()
    }

    /** Each packet with where it goes: the card's own broadcast address, and the local network's in any case. */
    fun sends(nics: List<WakeNic>): List<Pair<ByteArray, String>> = nics.flatMap { nic ->
        val p = packet(nic.mac) ?: return@flatMap emptyList()
        listOf(nic.broadcast, EVERYWHERE).distinct().filter { it.isNotBlank() }.map { p to it }
    }

    /** The wake is offered while the farm cannot be reached and a card to wake is known; never while it answers. */
    fun offered(link: Link?, nics: List<WakeNic>): Boolean =
        (link is Link.Offline || link is Link.Connecting) && nics.any { packet(it.mac) != null }

    /**
     * Over the farm's saved copy the wake waits until the link has failed once ([failed]: it was seen offline since
     * the screen opened): every opening starts with a try, and a button that shows for that second only to vanish
     * when the farm answers is noise. Once failed it stays through the retries.
     */
    fun offeredOverCopy(link: Link?, nics: List<WakeNic>, failed: Boolean): Boolean =
        offered(link, nics) && (failed || link is Link.Offline)
}
