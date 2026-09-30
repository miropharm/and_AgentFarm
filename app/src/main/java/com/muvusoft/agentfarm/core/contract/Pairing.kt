package com.muvusoft.agentfarm.core.contract

import java.net.URLDecoder
import kotlinx.serialization.Serializable

/** What the QR on Agent Farm's screen carries: agentfarm://pair?v&farm&name&code&fp&a */
data class PairingInfo(
    val v: Long,
    val farm: String,
    val name: String,
    val code: String,
    /** SHA-256 of the host certificate, lowercase hex. */
    val fp: String,
    /** host:port candidates, most local first. */
    val addresses: List<String>,
)

@Serializable
data class PairRequest(val code: String, val name: String, val publicKey: String, val platform: String, val app: String)

@Serializable
data class PairResponse(val device: String, val farm: FarmRef, val scope: String)

object Pairing {
    private const val PREFIX = "agentfarm://pair?"
    private val FIELDS = listOf("v", "farm", "name", "code", "fp", "a")

    /** The pairing link's fields, or null when it is not one or a field is missing. */
    fun parse(uri: String): PairingInfo? {
        val text = uri.trim()
        if (!text.startsWith(PREFIX)) return null
        val q = text.removePrefix(PREFIX).split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else decode(part.substring(0, i)) to decode(part.substring(i + 1))
        }.toMap()
        if (FIELDS.any { q[it].isNullOrEmpty() }) return null
        val v = q.getValue("v").toLongOrNull() ?: return null
        val addresses = q.getValue("a").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (addresses.isEmpty()) return null
        return PairingInfo(
            v = v,
            farm = q.getValue("farm"),
            name = q.getValue("name"),
            code = q.getValue("code"),
            fp = q.getValue("fp").lowercase(),
            addresses = addresses,
        )
    }

    private fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")
}
