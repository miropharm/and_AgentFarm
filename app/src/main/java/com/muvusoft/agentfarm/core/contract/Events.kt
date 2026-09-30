package com.muvusoft.agentfarm.core.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// The `data` of every event type in contract/remote-contract.v1.json. EVENT_TYPES maps each type to its
// model; an event type missing here is caught by test/test_contract_models.js and the JVM contract test.

sealed interface FarmEventData

@Serializable
data class AskOption(val label: String)

@Serializable
data class NeedsChanged(val count: Long, val top: JsonObject? = null) : FarmEventData

@Serializable
data class AskOpened(
    val session: String,
    val agent: String,
    val askId: String,
    val question: String,
    val options: List<AskOption>,
) : FarmEventData

@Serializable
data class PermissionOpened(
    val session: String,
    val agent: String,
    val permId: String,
    val tool: String,
    val summary: String,
) : FarmEventData

@Serializable
data class TurnFinished(val session: String, val agent: String, val title: String, val summary: String) : FarmEventData

@Serializable
data class NoticePosted(val id: String, val title: String, val level: Long, val agent: String) : FarmEventData

@Serializable
data class QuotaWarn(val engine: String, val percent: Double, val window: String) : FarmEventData

@Serializable
data class SessionEnded(val session: String, val agent: String) : FarmEventData

val EVENT_TYPES: Map<String, KSerializer<out FarmEventData>> = mapOf(
    "needs.changed" to NeedsChanged.serializer(),
    "ask.opened" to AskOpened.serializer(),
    "permission.opened" to PermissionOpened.serializer(),
    "turn.finished" to TurnFinished.serializer(),
    "notice.posted" to NoticePosted.serializer(),
    "quota.warn" to QuotaWarn.serializer(),
    "session.ended" to SessionEnded.serializer(),
)
