package com.muvusoft.agentfarm

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Rule

/**
 * What every journey test does and looks at: pairs with the scripted farm, opens Now, and sees the screen the way
 * a person does (placed on screen, not composed ahead), and reads what the farm was asked.
 */
abstract class JourneyBase {
    @get:Rule
    val rule = createEmptyComposeRule()

    protected val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    protected fun pair() {
        val pair = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        ActivityScenario.launch<MainActivity>(pair).use {
            rule.onNodeWithTag("pair-go").performClick()
            until(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("Paired with", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    protected fun open(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_NOW, true))

    protected fun until(ms: Long = 10_000, what: () -> Boolean) = rule.waitUntil(ms, what)

    /** Every named look on screen within [ms]; past it, the failure names the looks that were missing. */
    protected fun seen(ms: Long, vararg looks: Pair<String, () -> Boolean>) {
        if (runCatching { rule.waitUntil(ms) { looks.all { it.second() } } }.isSuccess) return
        throw AssertionError("not seen in $ms ms: " + looks.filterNot { it.second() }.joinToString { it.first })
    }

    /** Placed on screen, the way the eye finds it; a row the list composed ahead of time is not. */
    protected fun shown(m: SemanticsMatcher) =
        rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced && !it.boundsInRoot.isEmpty }

    /** The farm's answer on Now: its card, and no stale copy's banner above it. */
    protected fun fresh() = until(15_000) { shown(hasTestTag("running-c_dev")) && !shown(hasTestTag("stale")) }

    /** The user's words in a Conversation: the phone's own bubble, or the record's. */
    protected fun said(words: String) = hasText(words, substring = true) and (hasAnyAncestor(tagStarts("out-")) or hasAnyAncestor(tagStarts("user-")))

    protected fun answered(words: String) = hasText(words, substring = true) and hasAnyAncestor(tagStarts("answer-"))

    /** Where it starts can be read: its top, unclipped, lies inside the conversation list. */
    protected fun startsInView(m: SemanticsMatcher): Boolean {
        val area = rule.onAllNodes(hasTestTag("conv-list"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return false
        return rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().any { n ->
            n.layoutInfo.isPlaced && n.positionInRoot.y >= area.top - 1 && n.positionInRoot.y < area.bottom - 8
        }
    }

    /** This app's notifications gone: a journey's notification is its own, never one an earlier test left. */
    protected fun clearNotifications() = ctx.getSystemService(NotificationManager::class.java).cancelAll()

    /** A notification of this app whose tag starts so (and whose text holds [text]), once it is posted; null past [ms]. */
    protected fun notification(tagPrefix: String, text: String? = null, ms: Long = 10_000): StatusBarNotification? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val end = SystemClock.elapsedRealtime() + ms
        while (SystemClock.elapsedRealtime() < end) {
            nm.activeNotifications.firstOrNull { n ->
                n.tag?.startsWith(tagPrefix) == true && (text == null || n.notification.extras.getCharSequence("android.text")?.contains(text) == true)
            }?.let { return it }
            Thread.sleep(100)
        }
        return null
    }

    protected fun tagStarts(prefix: String) =
        SemanticsMatcher("testTag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

    protected fun calls(from: Int = 0): List<JSONObject> {
        val all = JSONObject(FakeHost.get("/_test/log")).getJSONArray("calls")
        return (from until all.length()).map { all.getJSONObject(it) }
    }

    protected fun args(call: JSONObject): JSONObject = call.optJSONObject("args") ?: JSONObject()
}
