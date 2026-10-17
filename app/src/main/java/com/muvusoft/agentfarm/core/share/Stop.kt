package com.muvusoft.agentfarm.core.share

import com.muvusoft.agentfarm.core.lang.Words
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Stopping a running turn from the phone (the widget's stop): console.interrupt on a session that runs. */
object Stop {
    /** The sessions with a turn running, from `console.sessions`; nothing else can be stopped. */
    fun running(result: JsonElement?): List<ShareTarget> = Share.targets(result).filter(Share::runs)

    /** What the stop says: the farm's own words for how it stopped (they differ by engine). */
    fun outcome(ok: Boolean, result: JsonElement?, error: String?): String {
        if (!ok) return Words.t("Not stopped: {0}", error ?: Words.t("the farm did not answer"))
        val how = ((result as? JsonObject)?.get("how") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return if (how.isNullOrBlank()) Words.t("Stopped.") else Words.t("Stopped: {0}.", how)
    }
}
