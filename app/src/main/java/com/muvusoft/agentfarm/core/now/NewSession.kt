package com.muvusoft.agentfarm.core.now

/** What the new-session sheet offers; the session itself is opened by the farm's console.new. */
object NewSession {
    /**
     * The agents a new session can open with (the farm's `console.new` list), the ones used last first: Now's own
     * rows say it, the cards waiting and running now, then the history in the farm's activity order. An agent
     * the farm no longer lists is never offered.
     */
    fun agents(farm: List<String>, s: NowState): List<String> {
        val used = (s.waiting.map { it.agent } + s.running.map { it.agent } + s.recent.map { it.agent }).distinct()
        return used.filter { it in farm } + farm.filter { it !in used }
    }
}
