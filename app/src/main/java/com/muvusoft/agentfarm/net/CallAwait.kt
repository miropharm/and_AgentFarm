package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.state.Link
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Makes a call and waits for its result, or null after `timeoutMs`. The call is made only once the
 * listener is subscribed, so a fast answer is never missed; a slow one still lands in the outbox's
 * own bookkeeping.
 */
suspend fun ConnectionManager.request(farmId: String, op: String, args: JsonObject? = null, timeoutMs: Long = 15_000): CallResult? =
    withTimeoutOrNull(timeoutMs) {
        var id = ""
        frames.onSubscription { id = call(farmId, op, args) }
            .mapNotNull { f -> (f.frame as? CallResult)?.takeIf { f.farm == farmId && it.id == id } }
            .first()
    }

/**
 * A read and its answer, outside the outbox (ConnectionManager.sendRead): null offline, once the link drops under
 * it (a read is never sent again, so nothing will answer it), or after `timeoutMs`.
 */
suspend fun ConnectionManager.read(farmId: String, op: String, args: JsonObject? = null, timeoutMs: Long = 15_000): CallResult? {
    if (state.value.farm(farmId)?.link !is Link.Online) return null
    return withTimeoutOrNull(timeoutMs) {
        var id: String? = null
        val answer = frames.onSubscription { id = sendRead(farmId, op, args) }
            .mapNotNull { f -> (f.frame as? CallResult)?.takeIf { f.farm == farmId && it.id == id } }
        val dropped = state.filter { it.farm(farmId)?.link !is Link.Online }.map { null }
        merge<CallResult?>(answer, dropped).first()
    }
}
