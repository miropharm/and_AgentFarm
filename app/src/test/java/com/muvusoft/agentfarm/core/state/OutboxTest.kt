package com.muvusoft.agentfarm.core.state

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OutboxTest {
    private val now = 100_000_000L
    private fun item(id: String, age: Long) =
        OutboxItem(id, "farm_a", "console.answer", buildJsonObject { put("id", JsonPrimitive("s1")) }, now - age, attempts = 1)

    @Test
    fun whatWasSavedComesBackWhole() {
        val items = listOf(item("c_1", 1_000), item("c_2", 2_000))
        assertEquals(items, Outbox.decode(Outbox.encode(items), now))
    }

    @Test
    fun anOldCallIsDroppedNotSent() {
        val items = listOf(item("c_old", Outbox.MAX_AGE_MS), item("c_new", Outbox.MAX_AGE_MS - 1))
        assertEquals(listOf("c_new"), Outbox.decode(Outbox.encode(items), now).map { it.id })
        val s = ShellState(outbox = items)
        assertEquals(listOf("c_new"), ShellReducer.expire(s, now).outbox.map { it.id })
    }

    @Test
    fun nothingExpiredLeavesTheStateAsItIs() {
        val s = ShellState(outbox = listOf(item("c_1", 1)))
        assertSame(s, ShellReducer.expire(s, now))
    }

    @Test
    fun unreadableStorageReadsAsEmpty() {
        assertEquals(emptyList<OutboxItem>(), Outbox.decode(null, now))
        assertEquals(emptyList<OutboxItem>(), Outbox.decode("{not json", now))
    }
}
