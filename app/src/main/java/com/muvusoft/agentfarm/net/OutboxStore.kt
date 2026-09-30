package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.state.Outbox
import com.muvusoft.agentfarm.core.state.OutboxItem

/** The outbox on this phone's disk, so a call pressed offline survives the process being killed. */
class OutboxStore(context: Context) {
    private val prefs = context.getSharedPreferences("outbox", Context.MODE_PRIVATE)

    fun load(now: Long): List<OutboxItem> = Outbox.decode(prefs.getString(ITEMS, null), now)

    fun save(items: List<OutboxItem>) = prefs.edit().putString(ITEMS, Outbox.encode(items)).apply()

    private companion object {
        const val ITEMS = "items"
    }
}
