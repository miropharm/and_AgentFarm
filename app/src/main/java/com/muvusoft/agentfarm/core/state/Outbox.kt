package com.muvusoft.agentfarm.core.state

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The outbox outlives the process, but not forever: an answer pressed hours ago can land on a
 * session that stopped waiting and read as a new prompt, so an old call is dropped, never sent.
 */
object Outbox {
    const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(OutboxItem.serializer())

    fun fresh(items: List<OutboxItem>, now: Long): List<OutboxItem> = items.filter { now - it.createdAt < MAX_AGE_MS }

    fun encode(items: List<OutboxItem>): String = json.encodeToString(list, items)

    /** What was saved and is still fresh; unreadable storage reads as empty. */
    fun decode(text: String?, now: Long): List<OutboxItem> {
        if (text.isNullOrBlank()) return emptyList()
        val items = runCatching { json.decodeFromString(list, text) }.getOrElse { return emptyList() }
        return fresh(items, now)
    }
}
