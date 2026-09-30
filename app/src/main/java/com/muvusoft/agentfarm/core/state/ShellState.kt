package com.muvusoft.agentfarm.core.state

import com.muvusoft.agentfarm.core.contract.FeatureAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A farm this phone has paired with. Everything needed to reach it again without the QR. */
@Serializable
data class PairedFarm(
    val id: String,
    val name: String,
    val device: String,
    val scope: String,
    /** SHA-256 of the farm's certificate, lowercase hex; no other certificate is trusted. */
    val fp: String,
    /** host:port candidates, most local first. */
    val addresses: List<String>,
    /** The Keystore alias of this pairing's device key; each pairing has its own, so a failed re-pair leaves the old one working. */
    val key: String,
)

/** Where the link to one farm stands. Every non-online state is a wait: kind, reason, since. */
sealed interface Link {
    val since: Long

    data class Offline(val reason: String, override val since: Long) : Link
    data class Connecting(val address: String, val attempt: Int, override val since: Long) : Link
    data class Online(
        val address: String,
        val scope: String,
        val features: Map<String, FeatureAnswer>,
        override val since: Long,
    ) : Link
    /** The farm said no; `reason` is the contract's refuse reason, `detail` the farm's own sentence. */
    data class Refused(val reason: String, val detail: String?, override val since: Long) : Link
}

data class FarmStatus(
    val farm: PairedFarm,
    val link: Link,
    /** The highest event seq received from this farm; 0 = none yet. */
    val lastSeq: Long = 0,
    /** What waits for the user on this farm (needs.changed count). */
    val needs: Long = 0,
    /** When this farm last reported a finished turn (event ts); null = none seen. */
    val lastTurnAt: Long? = null,
)

/** A call written while offline, sent when its farm is reachable; its id makes a resend harmless. */
data class OutboxItem(
    val id: String,
    val farm: String,
    val op: String,
    val args: JsonObject?,
    val createdAt: Long,
    val attempts: Int = 0,
)

data class ShellState(
    val farms: List<FarmStatus> = emptyList(),
    val focused: String? = null,
    val outbox: List<OutboxItem> = emptyList(),
) {
    fun farm(id: String): FarmStatus? = farms.firstOrNull { it.farm.id == id }
}
