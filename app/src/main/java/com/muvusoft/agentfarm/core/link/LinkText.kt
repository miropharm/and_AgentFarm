package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.Time.ago
import com.muvusoft.agentfarm.core.away.Away
import com.muvusoft.agentfarm.core.away.AwayVerdict
import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.lang.Words
import com.muvusoft.agentfarm.core.state.Link

/**
 * The status strip's words for one farm's link. A wait has one shape: kind · reason · since;
 * `short` is the strip, `long` the detail sheet (what clears it included).
 */
object LinkText {
    data class Text(val short: String, val long: String)

    fun of(link: Link, now: Long, away: Away? = null): Text = when (link) {
        is Link.Online -> if (away != null) {
            Text(
                AwayVerdict.short(away, now),
                Words.t("{0} Its service answers at {1}; open the farm to start Agent Farm from here.", away.sentence, link.address),
            )
        } else {
            Text(
                Words.t("Connected · {0}", link.address),
                Words.t("Connected: {0}, scope {1}, {2} ago.", link.address, link.scope, ago(now - link.since)),
            )
        }
        is Link.Connecting -> Text(
            if (link.attempt <= 1) Words.t("Connecting · {0}", link.address) else Words.t("Reconnecting · attempt {0}", link.attempt),
            Words.t(
                "Connecting: {0}, attempt {1}, started {2} ago. It connects by itself once the farm is reachable.",
                link.address, link.attempt, ago(now - link.since),
            ),
        )
        is Link.Offline -> Text(
            Words.t("Not connected · {0}", offline(link.reason)),
            Words.t("Not connected: {0}, for {1}. It connects by itself once the farm is reachable.", offline(link.reason), ago(now - link.since)),
        )
        is Link.Resting -> Text(
            Words.t("Resting · wakes when something happens"),
            Words.t(
                "Resting for {0}: the link closed to spare the battery. The farm wakes this phone when a question, a permission or a finished turn arrives; it also opens when the app comes forward and every few minutes to catch up.",
                ago(now - link.since),
            ),
        )
        is Link.Refused -> Text(
            Words.t("Refused · {0}", refused(link.reason, link.minContract)),
            Words.t("The farm refused the link: {0}", refused(link.reason, link.minContract)) + (link.detail?.let { " ($it)" } ?: "") +
                ". " + clears(link.reason, link.minContract),
        )
    }

    /** The link detail sheet: the long form first, then where the phone looks for the farm. */
    fun sheet(link: Link, addresses: List<String>, now: Long, away: Away? = null): List<String> =
        listOf(of(link, now, away).long) + (if (addresses.isEmpty()) emptyList() else listOf(Words.t("Addresses: {0}", addresses.joinToString(", "))))

    /** Which side is behind: the farm needs a newer contract than this app speaks, or the other way round. */
    private fun appIsBehind(minContract: Long?): Boolean? = minContract?.let { it > CONTRACT_VERSION }

    fun refused(reason: String, minContract: Long? = null): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> Words.t("this app is out of date")
            false -> Words.t("Agent Farm is out of date")
            null -> Words.t("the versions do not match")
        }
        "revoked" -> Words.t("this device's access was revoked")
        "unknown-device" -> Words.t("the farm does not know this device")
        "bad-signature" -> Words.t("the device signature could not be verified")
        "not-allowed" -> Words.t("not allowed")
        "busy" -> Words.t("the farm is busy")
        else -> reason
    }

    private fun clears(reason: String, minContract: Long?): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> Words.t("Update the app on the phone: the farm needs contract {0}, this app speaks {1}.", minContract, CONTRACT_VERSION)
            false -> Words.t("Update Agent Farm: this app speaks contract {0}, the farm an older one.", CONTRACT_VERSION)
            null -> Words.t("Update the app or Agent Farm.")
        }
        "busy", "not-allowed" -> Words.t("It tries again shortly.")
        else -> Words.t("Pair again with a new QR code from Agent Farm.")
    }

    private fun offline(reason: String): String = when (reason) {
        "paired" -> Words.t("not connected yet")
        "unreachable" -> Words.t("its addresses could not be reached")
        "closed" -> Words.t("the link closed")
        "stopped" -> Words.t("stopped")
        else -> reason
    }
}
