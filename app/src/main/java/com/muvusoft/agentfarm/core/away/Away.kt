package com.muvusoft.agentfarm.core.away

import com.muvusoft.agentfarm.core.Time
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

    const val NO_ANSWER = "The farm did not answer; try again in a moment."

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
            sentence = o.str("sentence")?.takeIf { it.isNotBlank() } ?: "Agent Farm is not running: VS Code is closed on $farm.",
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
        "VS Code is closed" + (a.snapshotAt?.let { " · last state ${Time.ago(now - it)} ago" } ?: "")

    /** The wait screen's lines under the sentence: since when, how old the numbers are, the numbers. */
    fun lines(a: Away, now: Long): List<String> {
        val out = mutableListOf<String>()
        a.since?.let { out += "VS Code was last seen ${Time.ago(now - it)} ago." }
        if (a.snapshotAt == null) {
            out += "There is no saved state to show."
        } else {
            out += "The numbers below are from ${Time.ago(now - a.snapshotAt)} ago; nothing behind them moves until VS Code opens."
            val counts = listOfNotNull(
                a.needs?.let { "$it waiting for you" },
                a.unreadNotices?.let { count(it, "unread notice") },
                a.runningTasks?.let { count(it, "running task") },
            )
            if (counts.isNotEmpty()) out += counts.joinToString(" · ")
        }
        return out
    }

    private fun count(n: Long, thing: String): String = "$n $thing" + if (n == 1L) "" else "s"

    fun row(n: AwayNeed): String = if (n.agent.isBlank()) n.title else "${n.agent} · ${n.title}"

    /** What farm.open answered, as one sentence; `error` is the farm's own refusal when the call failed. */
    fun opened(ok: Boolean, result: JsonElement?, error: String?, farm: String, now: Long): String {
        if (!ok) return error?.takeIf { it.isNotBlank() } ?: "Agent Farm could not be opened."
        val o = result as? JsonObject
        return when {
            o?.bool("already") == true -> "Agent Farm is already running on $farm."
            o?.bool("again") == true -> "VS Code is already starting on $farm" +
                (o.num("since")?.let { " (asked ${Time.ago(now - it)} ago)" } ?: "") + "; it connects by itself once it is up."
            o?.bool("opening") == true -> "Starting VS Code on $farm; the farm's pages open here once it is up."
            else -> "Agent Farm answered, but not with a start."
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.num(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
}
