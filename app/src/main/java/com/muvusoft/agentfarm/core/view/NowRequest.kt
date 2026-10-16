package com.muvusoft.agentfarm.core.view

/**
 * Open Now: the focused farm's (the widget, the tile), or a notification's farm with its session's Conversation over
 * it, so Back from that Conversation is Now and not out of the app.
 */
data class NowRequest(val farm: String? = null, val session: String? = null)
