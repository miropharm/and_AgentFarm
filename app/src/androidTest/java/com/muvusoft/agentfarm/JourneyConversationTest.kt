package com.muvusoft.agentfarm

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Conversation's journeys on the scripted farm, on the LAN and on mobile data: J2 follow a running session (its
 * turn from what was asked, the step it is on live, the answer's start when it ends, the copy at once next time), J3
 * give an instruction (your words at once, the first step and the answer within a second of the PC, the next one
 * from the same screen), J5 stop from the Conversation (Stop and the yes), J9 the link drops in a Conversation (a
 * message waits on the phone, goes once, and the way back reads on from the last row read).
 */
@RunWith(AndroidJUnit4::class)
class JourneyConversationTest : JourneyBase() {
    @Test
    fun conversationOnTheLan() = journeys("lan", freshMs = 1_500, oneWayMs = 50)

    @Test
    fun conversationOnMobileData() = journeys("mobile", freshMs = 3_000, oneWayMs = 300)

    private fun journeys(profile: String, freshMs: Long, oneWayMs: Long) {
        FakeHost.requireLink()
        pair()
        val failed = listOf({ j2(profile, freshMs) }, { j3(profile, freshMs, oneWayMs) }, { j5(profile) }, { j9(profile) })
            .mapNotNull { runCatching(it).exceptionOrNull()?.let { e -> e.message ?: e.javaClass.simpleName } }
        FakeHost.post("/_test/sleep", "{\"asleep\":false}")
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    private fun j2(profile: String, freshMs: Long) {
        val j = Journey("J2", profile).begin()
        val s = open()
        try {
            fresh()
            val asked = SystemClock.elapsedRealtime()
            val turn = JSONObject(FakeHost.post("/_test/turn", """{"id":"c_dev","stepMs":$J2_STEP_MS,"text":"$J2_ASK","answer":"$J2_ANSWER","tools":[["Bash","$J2_TOOL"]]}"""))
            val ends = asked + turn.getLong("ms")
            j.step("tap the running card: its turn from what was asked", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("running-c_dev").performClick()
                seen(15_000, "what was asked, its start in view" to { startsInView(said(J2_ASK)) })
            }
            j.step("the turn streams: the step it is on, live", taps = 0, targetMs = due(asked + J2_STEP_MS)) {
                seen(10_000, "the live step" to { shown(hasText(J2_TOOL, substring = true) and hasAnyAncestor(hasTestTag("live"))) })
            }
            j.step("the turn ends: the answer's start in view", taps = 0, targetMs = due(ends)) {
                seen(15_000, "the answer's start" to { startsInView(answered(J2_ANSWER)) })
            }
            back()
            // Now hears the turn ended: the session leaves Running for the top of the history.
            until { !shown(hasTestTag("running-c_dev")) && shown(tagStarts("recent-")) }
            j.step("in again: the copy at once", taps = 1, targetMs = 500) {
                rule.onAllNodes(tagStarts("recent-"), useUnmergedTree = true).onFirst().performClick()
                seen(5_000, "the answer from the copy" to { startsInView(answered(J2_ANSWER)) })
            }
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun j3(profile: String, freshMs: Long, oneWayMs: Long) {
        val j = Journey("J3", profile).begin()
        val s = open()
        try {
            fresh()
            openIdle(j, freshMs)
            type(J3_WORDS)
            val sentAt = SystemClock.elapsedRealtime()
            j.step("send: your words at once", taps = 1, targetMs = 200) {
                rule.onNodeWithTag("composer-send").performClick()
                until(2_000) { shown(said(J3_WORDS)) }
            }
            // The scripted farm runs a sent turn a step a second: the first call one second after the words reach it.
            val reached = sentAt + oneWayMs
            j.step("the first step: within a second of the PC", taps = 0, targetMs = due(reached + SENT_STEP_MS)) {
                seen(10_000, "the live step" to { shown(hasText("Bash", substring = true) and hasAnyAncestor(hasTestTag("live"))) })
            }
            j.step("the answer: within a second of the turn's end", taps = 0, targetMs = due(reached + SENT_TURN_MS)) {
                seen(15_000, "the answer's start" to { startsInView(answered("Yaptım: $J3_WORDS")) })
            }
            type(J3_NEXT)
            j.step("the next instruction from the same screen", taps = 1, targetMs = 200) {
                rule.onNodeWithTag("composer-send").performClick()
                until(2_000) { shown(said(J3_NEXT)) }
            }
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun j5(profile: String) {
        val j = Journey("J5", profile).begin()
        val s = open()
        try {
            fresh()
            FakeHost.post("/_test/turn", """{"id":"c_dev","stepMs":5000}""")
            rule.onNodeWithTag("running-c_dev").performClick()
            until(15_000) { shown(hasTestTag("conv-stop")) }
            j.step("Stop asks for the yes", taps = 1, targetMs = 1_000) {
                rule.onNodeWithTag("conv-stop").performClick()
                until { shown(hasTestTag("destructive-dialog")) }
            }
            j.step("the yes stops it", taps = 1, targetMs = 1_000) {
                rule.onNode(hasText(ctx.getString(R.string.stop_go)) and hasAnyAncestor(hasTestTag("destructive-dialog"))).performClick()
                until { !shown(hasTestTag("conv-stop")) && !shown(hasTestTag("live")) }
            }
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
            openIdle(null, 0)
            j.step("the link drops: the conversation stays, its age shown", taps = 0, targetMs = 5_000) {
                FakeHost.post("/_test/sleep", "{\"asleep\":true}")
                seen(10_000, "the copy's age" to { shown(hasText("· updated", substring = true)) }, "the conversation" to { shown(hasTestTag("conv-list")) })
            }
            type(J9_WORDS)
            j.step("send offline: your words wait on this phone", taps = 1, targetMs = 200) {
                rule.onNodeWithTag("composer-send").performClick()
                seen(2_000, "your words" to { shown(said(J9_WORDS)) }, "waiting" to { shown(hasText(ctx.getString(R.string.send_waiting))) })
            }
            val before = calls().size
            j.step("the farm answers: the words go once, read on from the last row", taps = 0, targetMs = 10_000) {
                FakeHost.post("/_test/sleep", "{\"asleep\":false}")
                seen(
                    15_000,
                    "sent once" to { calls(before).count { it.getString("op") == "console.send" && args(it).optString("text") == J9_WORDS } == 1 },
                    "in the record" to { shown(hasText(J9_WORDS, substring = true) and hasAnyAncestor(tagStarts("user-"))) },
                )
                val reads = calls(before).filter { it.getString("op") == "sessions.read" }.map { args(it).optInt("from") }
                assertTrue("the way back reads on from the last row, never the last page again: $reads", reads.isNotEmpty() && reads.all { it > 0 })
            }
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    /** The idle Console card's session (c_psy), the newest one Now lists under its history. */
    private fun openIdle(j: Journey?, freshMs: Long) {
        val act = {
            rule.onAllNodes(tagStarts("recent-"), useUnmergedTree = true).onFirst().performClick()
            seen(15_000, "its agent" to { shown(hasText("psikolog") and hasAnyAncestor(hasTestTag("conv-title"))) }, "the composer" to { shown(hasTestTag("composer-send")) })
        }
        if (j == null) act() else j.step("open the idle session from the history", taps = 1, targetMs = freshMs) { act() }
    }

    /** The typing is the person's own time, not the app's. */
    private fun type(words: String) =
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("composer"))).performTextInput(words)

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        until { shown(hasTestTag("now-list")) }
    }

    /** What is left of a second after the PC did something: a step's target from the moment it starts. */
    private fun due(pcAt: Long) = (pcAt + 1_000 - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    private companion object {
        const val J2_STEP_MS = 3_000L
        const val J2_ASK = "Sürüm testlerini koş ve raporla"
        const val J2_ANSWER = "Rapor hazır: bütün sürüm testleri yeşil."
        const val J2_TOOL = "gradlew-surum-testleri"
        const val SENT_STEP_MS = 1_000L

        /** A sent turn: two calls and their results, then the answer, a step each. */
        const val SENT_TURN_MS = 5 * SENT_STEP_MS
        const val J3_WORDS = "Sürüm notunu hazırla"
        const val J3_NEXT = "Bir de koyu temayı kontrol et"
        const val J9_WORDS = "Bağlantı yokken yazıldı: değişiklik günlüğünü güncelle"
    }
}
