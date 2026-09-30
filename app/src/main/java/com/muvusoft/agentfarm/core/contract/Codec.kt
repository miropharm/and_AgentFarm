package com.muvusoft.agentfarm.core.contract

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val CONTRACT_NAME = "agentfarm.remote"
const val CONTRACT_VERSION = 1L

/** Reads and writes frames the way the contract spells them: `kind` names the frame, extra fields are allowed. */
object Codec {
    val json: Json = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** A frame, or null when the text is not JSON, names no known kind, or misses a declared field. */
    fun decode(text: String): Frame? = try {
        json.decodeFromString(Frame.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun encode(frame: Frame): String = json.encodeToString(Frame.serializer(), frame)

    /** An event's data as its model; null for a type this build does not know (the client ignores it). */
    fun eventData(frame: EventFrame): FarmEventData? {
        val serializer = EVENT_TYPES[frame.type] ?: return null
        return try {
            json.decodeFromJsonElement(serializer, frame.data)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The exact text a device signs to answer a challenge. */
    fun signedText(farmId: String, deviceId: String, nonce: String): String =
        "$CONTRACT_NAME/$CONTRACT_VERSION|$farmId|$deviceId|$nonce"
}
