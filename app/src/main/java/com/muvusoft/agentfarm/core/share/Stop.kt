package com.muvusoft.agentfarm.core.share

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Stopping a running turn from the phone (the widget's stop): console.interrupt on a session that runs. */
object Stop {
    /** The sessions with a turn running, from `console.sessions`; nothing else can be stopped. */
    fun running(result: JsonElement?): List<ShareTarget> = Share.targets(result).filter { it.status == "running" }

    /** What the stop says: the farm's own words for how it stopped (they differ by engine). */
    fun outcome(ok: Boolean, result: JsonElement?, error: String?): String {
        if (!ok) return "Not stopped: ${error ?: "the farm did not answer"}"
        val how = ((result as? JsonObject)?.get("how") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return if (how.isNullOrBlank()) "Stopped." else "Stopped: $how."
    }
}
