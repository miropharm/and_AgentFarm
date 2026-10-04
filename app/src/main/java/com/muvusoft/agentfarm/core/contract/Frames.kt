package com.muvusoft.agentfarm.core.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// One class per frame kind of contract/remote-contract.v1.json. The serial name IS the frame's `kind`;
// test/test_contract_models.js derives every kind and field from the JSON and finds it here.

@Serializable
data class FarmRef(val id: String, val name: String)

@Serializable
data class FeatureAnswer(val allowed: Boolean, val reason: String? = null)

@Serializable
sealed class Frame

@Serializable
@SerialName("challenge")
data class Challenge(val nonce: String, val farm: FarmRef, val contract: Long) : Frame()

@Serializable
@SerialName("hello")
data class Hello(
    val device: String,
    val contract: Long,
    val signature: String,
    val resumeAfter: Long? = null,
) : Frame()

@Serializable
@SerialName("welcome")
data class Welcome(
    val farm: FarmRef,
    val device: String,
    val scope: String,
    val features: Map<String, FeatureAnswer>,
    val lastSeq: Long,
    /** Authorizes /view and /res while this socket is open; held in memory only. */
    val session: String? = null,
) : Frame()

@Serializable
@SerialName("refuse")
data class Refuse(val reason: String, val detail: String? = null, val minContract: Long? = null) : Frame()

@Serializable
@SerialName("event")
data class EventFrame(
    val seq: Long,
    val type: String,
    val ts: Long,
    val data: JsonObject,
    /** Whose record the event is; absent on events from before the field, which read as the owner's. */
    val user: String? = null,
) : Frame()

@Serializable
@SerialName("gap")
data class Gap(val from: Long, val to: Long) : Frame()

@Serializable
@SerialName("call")
data class Call(val id: String, val op: String, val args: JsonObject? = null) : Frame()

@Serializable
@SerialName("result")
data class CallResult(
    val id: String,
    val ok: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
    val duplicate: Boolean? = null,
) : Frame()

@Serializable
@SerialName("view.open")
data class ViewOpen(val view: String, val page: String, val args: JsonObject? = null) : Frame()

@Serializable
@SerialName("view.post")
data class ViewPost(val view: String, val message: JsonElement) : Frame()

@Serializable
@SerialName("view.msg")
data class ViewMsg(val view: String, val message: JsonElement) : Frame()

@Serializable
@SerialName("view.close")
data class ViewClose(val view: String, val reason: String? = null) : Frame()

@Serializable
@SerialName("ping")
data class Ping(val ts: Long) : Frame()

@Serializable
@SerialName("pong")
data class Pong(val ts: Long) : Frame()
