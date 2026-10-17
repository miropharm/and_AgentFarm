package com.muvusoft.agentfarm.core.away

import com.muvusoft.agentfarm.core.Time
import com.muvusoft.agentfarm.core.lang.Words
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** A row of the last snapshot's "needs you" list. */
data class AwayNeed(val agent: String, val title: String)

/**
 * The farm's VS Code is closed and its headless service holds the socket (farm.away): it serves no pages and
 * sends no events, but it can start VS Code (farm.open) and hands over the last snapshot it has.
 */
data class Away(
    /** The farm's own sentence ("Agent Farm is not running: VS Code is closed on …"). */
    val sentence: String,
    /** When VS Code was last seen alive; null = unknown. */
    val since: Long? = null,
    /** When somebody last asked the service to open VS Code. */
    val openedAt: Long? = null,
    /** When the last snapshot was written; null = there is none. */
    val snapshotAt: Long? = null,
    val needs: Long? = null,
    val unreadNotices: Long? = null,
    val runningTasks: Long? = null,
    val top: List<AwayNeed> = emptyList(),
)

object AwayVerdict {
    /** Rows of the snapshot's needs shown under the wait; the rest is counted. */
    const val TOP = 5

    val NO_ANSWER: String get() = Words.t("The farm did not answer; try again in a moment.")

    /** farm.away's answer: an Away while VS Code is closed, null while Agent Farm runs (or an answer it cannot read). */
    fun of(result: JsonElement?): Away? {
        val o = result as? JsonObject ?: return null
        if (o.bool("away") != true) return null
        val farm = o.str("farm").orEmpty()
        val snap = o["snapshot"] as? JsonObject
        val totals = snap?.get("totals") as? JsonObject
        val top = (snap?.get("needs") as? JsonArray).orEmpty().mapNotNull { r ->
            (r as? JsonObject)?.let { AwayNeed(it.str("agent").orEmpty(), it.str("title").orEmpty()) }?.takeIf { it.title.isNotBlank() }
        }
        return Away(
            sentence = o.str("sentence")?.takeIf { it.isNotBlank() } ?: Words.t("Agent Farm is not running: VS Code is closed on {0}.", farm),
            since = o.num("since"),
            openedAt = o.num("openedAt"),
            snapshotAt = snap?.num("ts"),
            needs = totals?.num("needs"),
            unreadNotices = totals?.num("unreadNotices"),
            runningTasks = totals?.num("runningTasks"),
            top = top.take(TOP),
        )
    }

    /** The status strip while away. */
    fun short(a: Away, now: Long): String =
        a.snapshotAt?.let { Words.t("VS Code is closed · last state {0} ago", Time.ago(now - it)) } ?: Words.t("VS Code is closed")

    /** The wait screen's lines under the sentence: since when, how old the numbers are, the numbers. */
    fun lines(a: Away, now: Long): List<String> {
        val out = mutableListOf<String>()
        a.since?.let { out += Words.t("VS Code was last seen {0} ago.", Time.ago(now - it)) }
        if (a.snapshotAt == null) {
            out += Words.t("There is no saved state to show.")
        } else {
            out += Words.t("The numbers below are from {0} ago; nothing behind them moves until VS Code opens.", Time.ago(now - a.snapshotAt))
            val counts = listOfNotNull(
                a.needs?.let { Words.t("{0} waiting for you", it) },
                a.unreadNotices?.let { if (it == 1L) Words.t("{0} unread notice", it) else Words.t("{0} unread notices", it) },
                a.runningTasks?.let { if (it == 1L) Words.t("{0} running task", it) else Words.t("{0} running tasks", it) },
            )
            if (counts.isNotEmpty()) out += counts.joinToString(" · ")
        }
        return out
    }

    fun row(n: AwayNeed): String = if (n.agent.isBlank()) n.title else "${n.agent} · ${n.title}"

    /** What farm.open answered, as one sentence; `error` is the farm's own refusal when the call failed. */
    fun opened(ok: Boolean, result: JsonElement?, error: String?, farm: String, now: Long): String {
        if (!ok) return error?.takeIf { it.isNotBlank() } ?: Words.t("Agent Farm could not be opened.")
        val o = result as? JsonObject
        return when {
            o?.bool("already") == true -> Words.t("Agent Farm is already running on {0}.", farm)
            o?.bool("again") == true -> o.num("since")
                ?.let { Words.t("VS Code is already starting on {0} (asked {1} ago); it connects by itself once it is up.", farm, Time.ago(now - it)) }
                ?: Words.t("VS Code is already starting on {0}; it connects by itself once it is up.", farm)
            o?.bool("opening") == true -> Words.t("Starting VS Code on {0}; the farm's pages open here once it is up.", farm)
            else -> Words.t("Agent Farm answered, but not with a start.")
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.num(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
}
