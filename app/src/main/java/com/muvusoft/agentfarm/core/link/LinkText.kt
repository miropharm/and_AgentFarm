package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.Time.ago
import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.state.Link

/**
 * The status strip's words for one farm's link. A wait has one shape: kind · reason · since;
 * `short` is the strip, `long` the detail sheet (what clears it included).
 */
object LinkText {
    data class Text(val short: String, val long: String)

    fun of(link: Link, now: Long): Text = when (link) {
        is Link.Online -> Text("Connected · ${link.address}", "Connected: ${link.address}, scope ${link.scope}, ${ago(now - link.since)} ago.")
        is Link.Connecting -> Text(
            if (link.attempt <= 1) "Connecting · ${link.address}" else "Reconnecting · attempt ${link.attempt}",
            "Connecting: ${link.address}, attempt ${link.attempt}, started ${ago(now - link.since)} ago. It connects by itself once the farm is reachable.",
        )
        is Link.Offline -> Text(
            "Not connected · ${offline(link.reason)}",
            "Not connected: ${offline(link.reason)}, for ${ago(now - link.since)}. It connects by itself once the farm is reachable.",
        )
        is Link.Refused -> Text(
            "Refused · ${refused(link.reason, link.minContract)}",
            "The farm refused the link: ${refused(link.reason, link.minContract)}" + (link.detail?.let { " ($it)" } ?: "") +
                ". " + clears(link.reason, link.minContract),
        )
    }

    /** The link detail sheet: the long form first, then where the phone looks for the farm. */
    fun sheet(link: Link, addresses: List<String>, now: Long): List<String> =
        listOf(of(link, now).long) + (if (addresses.isEmpty()) emptyList() else listOf("Addresses: " + addresses.joinToString(", ")))

    /** Which side is behind: the farm needs a newer contract than this app speaks, or the other way round. */
    private fun appIsBehind(minContract: Long?): Boolean? = minContract?.let { it > CONTRACT_VERSION }

    fun refused(reason: String, minContract: Long? = null): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> "this app is out of date"
            false -> "Agent Farm is out of date"
            null -> "the versions do not match"
        }
        "revoked" -> "this device's access was revoked"
        "unknown-device" -> "the farm does not know this device"
        "bad-signature" -> "the device signature could not be verified"
        "not-allowed" -> "not allowed"
        "busy" -> "the farm is busy"
        else -> reason
    }

    private fun clears(reason: String, minContract: Long?): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> "Update the app on the phone: the farm needs contract $minContract, this app speaks $CONTRACT_VERSION."
            false -> "Update Agent Farm: this app speaks contract $CONTRACT_VERSION, the farm an older one."
            null -> "Update the app or Agent Farm."
        }
        "busy", "not-allowed" -> "It tries again shortly."
        else -> "Pair again with a new QR code from Agent Farm."
    }

    private fun offline(reason: String): String = when (reason) {
        "paired" -> "not connected yet"
        "unreachable" -> "its addresses could not be reached"
        "closed" -> "the link closed"
        "stopped" -> "stopped"
        else -> reason
    }
}
