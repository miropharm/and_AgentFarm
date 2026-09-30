package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.CallResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
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
