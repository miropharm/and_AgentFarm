package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Now's journeys on the scripted farm, on the LAN and on mobile data, in the order a person meets them: J1 open and
 * see (the copy at once, the fresh answer within the network's target, what waits above what runs), J8 the
 * history 20 at a time, J7 a new session up to its card, J9 the link drops and comes back (the copy and its age
 * stay, a new session typed meanwhile waits on the phone, the way back reads only the head).
 */
@RunWith(AndroidJUnit4::class)
class JourneyNowTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nowOnTheLan() = journeys("lan", freshMs = 1_500)

    @Test
    fun nowOnMobileData() = journeys("mobile", freshMs = 3_000)

    private fun journeys(profile: String, freshMs: Long) {
        FakeHost.requireLink()
        pair()
        // J9 offers the agents J7 read while the farm answered: the order is the journey's own.
        val failed = listOf({ j1(profile, freshMs) }, { j8(profile) }, { j7(profile, freshMs) }, { j9(profile) })
            .mapNotNull { runCatching(it).exceptionOrNull()?.let { e -> e.message ?: e.javaClass.simpleName } }
        FakeHost.post("/_test/sleep", "{\"asleep\":false}")
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    private fun j1(profile: String, freshMs: Long) {
        val j = Journey("J1", profile).begin()
        open().use { fresh() }
        // What only the fresh answer knows: a card that asks since the copy was saved.
        FakeHost.post("/_test/turn", """{"id":"c_psy","stepMs":10,"ask":{"question":"Hangi dal?","options":["master","yeni dal"]}}""")
        var s: ActivityScenario<MainActivity>? = null
        try {
            val copy = j.step("reopen: the copy at once", taps = 0, targetMs = 500) {
                s = open()
                until { shown(hasTestTag("running-c_dev")) }
            }
            j.step("the fresh answer: what waits above what runs", taps = 0, targetMs = (freshMs - copy).coerceAtLeast(0)) {
                until(15_000) { shown(hasTestTag("waiting-c_psy")) && shown(hasTestTag("running-c_dev")) }
                val wait = rule.onNodeWithTag("waiting-c_psy", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val run = rule.onNodeWithTag("running-c_dev", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("what waits is drawn above what runs: $wait / $run", wait.top < run.top)
            }
            j.economy()
        } finally {
            s?.close()
        }
        j.end()
    }

    private fun j8(profile: String) {
        val j = Journey("J8", profile).begin()
        val s = open()
        try {
            fresh()
            for (k in 1..3) {
                j.step("scroll: 20 more", taps = 1, targetMs = 1_000) { more(k) }
            }
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun j7(profile: String, freshMs: Long) {
        val j = Journey("J7", profile).begin()
        val s = open()
        try {
            fresh()
            j.step("+ New: the agents, one already picked", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("now-new").performClick()
                until { picked() }
            }
            j.step("the agent and the first message", taps = 1, targetMs = 1_000) {
                rule.onNodeWithTag("new-agent-developer").performClick()
                rule.onNodeWithTag("new-agent-developer").assertIsSelected()
                rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("new-session"))).performTextInput("Telefon ana ekranının testlerini çalıştır")
            }
            j.step("send: the session opens and runs", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("composer-send").performClick()
                until { shown(hasTestTag("running-c_new3")) && rule.onAllNodes(hasTestTag("new-session")).fetchSemanticsNodes().isEmpty() }
            }
            j.note("the new session's own Conversation opens once it is native; until then its card runs on Now")
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun j9(profile: String) {
        val j = Journey("J9", profile).begin()
        val s = open()
        try {
            fresh()
            more(1)
            rule.onNodeWithTag("now-list").performScrollToIndex(0)
            j.step("the link drops: the copy and its age stay, the wake is offered", taps = 0, targetMs = 5_000) {
                FakeHost.post("/_test/sleep", "{\"asleep\":true}")
                until { shown(hasText("· updated", substring = true)) && shown(hasTestTag("running-c_dev")) && shown(hasTestTag("wake-go")) }
            }
            val before = calls().size
            j.step("a new session typed offline waits on this phone", taps = 2, targetMs = 1_000) {
                rule.onNodeWithTag("now-new").performClick()
                until { picked() }
                rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("new-session"))).performTextInput(OFFLINE_WORDS)
                rule.onNodeWithTag("composer-send").performClick()
                until(2_000) { shown(hasText("Waiting on this phone", substring = true)) }
            }
            closeSheet()
            j.step("the farm answers: the words go once, only the head is read", taps = 1, targetMs = 10_000) {
                FakeHost.post("/_test/sleep", "{\"asleep\":false}")
                rule.onNodeWithTag("wake-go").performClick()
                until(15_000) { !shown(hasTestTag("stale")) && calls(before).count(::offlineNew) == 1 }
                val back = calls(before).filter { it.getString("op") == "sessions.recent" }.map(::args)
                assertTrue("the way back reads the head only: $back", back.isNotEmpty() && back.none { it.has("cursor") })
            }
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun pair() {
        val pair = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        ActivityScenario.launch<MainActivity>(pair).use {
            rule.onNodeWithTag("pair-go").performClick()
            until(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("Paired with", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun open(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_NOW, true))

    private fun until(ms: Long = 10_000, what: () -> Boolean) = rule.waitUntil(ms, what)

    /** Placed on screen, the way the eye finds it; a row the list composed ahead of time is not. */
    private fun shown(m: SemanticsMatcher) =
        rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced && !it.boundsInRoot.isEmpty }

    /** The farm's answer on screen: its card, and no stale copy's banner above it. */
    private fun fresh() = until(15_000) { shown(hasTestTag("running-c_dev")) && !shown(hasTestTag("stale")) }

    /** Down to the list's end: the k-th next page asked for, and rows where its loader was. */
    private fun more(k: Int) {
        rule.onNodeWithTag("now-list").performScrollToNode(hasTestTag("skeleton"))
        until { pages() >= k && !shown(hasTestTag("skeleton")) }
    }

    private fun picked() = rule.onAllNodes(tagStarts("new-agent-") and isSelected()).fetchSemanticsNodes().isNotEmpty()

    /** The sheet yields to Back; the first Back may only close the keyboard. */
    private fun closeSheet() {
        repeat(3) {
            if (rule.onAllNodes(hasTestTag("new-session")).fetchSemanticsNodes().isEmpty()) return
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
        }
        until { rule.onAllNodes(hasTestTag("new-session")).fetchSemanticsNodes().isEmpty() }
    }

    private fun tagStarts(prefix: String) =
        SemanticsMatcher("testTag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

    private fun calls(from: Int = 0): List<JSONObject> {
        val all = JSONObject(FakeHost.get("/_test/log")).getJSONArray("calls")
        return (from until all.length()).map { all.getJSONObject(it) }
    }

    private fun args(call: JSONObject): JSONObject = call.optJSONObject("args") ?: JSONObject()

    private fun pages() = calls().count { it.getString("op") == "sessions.recent" && args(it).has("cursor") }

    private fun offlineNew(call: JSONObject) = call.getString("op") == "console.new" && args(call).optString("prompt") == OFFLINE_WORDS

    private companion object {
        const val OFFLINE_WORDS = "Bağlantı yokken yazıldı: sürüm notunu hazırla"
    }
}
