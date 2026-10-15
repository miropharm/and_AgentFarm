package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.ShellState

/**
 * A farm's answer on the local network (the contract's transport.discovery): mDNS service
 * _agentfarm._tcp, TXT farm=<farm id>. A paired farm found there is tried at that address first,
 * so a PC whose address changed is reached again without a new QR.
 */
object Discovery {
    const val SERVICE_TYPE = "_agentfarm._tcp"

    data class Found(val farm: String, val address: String)

    private val IPV4 = Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

    /** Which farm answered and where; null for an answer that names no farm or no usable IPv4 address. */
    fun found(txt: Map<String, ByteArray?>, ipv4: String?, port: Int): Found? {
        val farm = txt["farm"]?.toString(Charsets.UTF_8)?.trim().orEmpty()
        if (farm.isEmpty() || ipv4 == null || !IPV4.matches(ipv4) || port !in 1..65535) return null
        return Found(farm, "$ipv4:$port")
    }

    /** Look while a paired farm is out of reach for a reason a new address could fix; a refusal is not one. */
    fun wanted(state: ShellState): Boolean = state.farms.any { it.link is Link.Offline || it.link is Link.Connecting }
}
