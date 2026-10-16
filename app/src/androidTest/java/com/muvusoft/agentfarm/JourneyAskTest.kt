package com.muvusoft.agentfarm

import android.view.KeyEvent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The question and permission card and the notification's door, on the scripted farm, on the LAN and on mobile
 * data. J4: a question's notification opens its Conversation with the card open, one tap answers it and the card
 * goes; Now's waiting row opens the same card; a question the PC answers closes on the phone by itself; a
 * permission is allowed with one tap. J6: a finished turn's notification opens the Conversation at its answer.
 * The app is opened the way the widget opens it (Launched): ActivityScenario's screen lives in the test's own task,
 * where a notification's tap would start a second app screen instead of reaching this one, as it does on a phone.
 */
@RunWith(AndroidJUnit4::class)
class JourneyAskTest : JourneyBase() {
    @Test
    fun askOnTheLan() = journeys("lan", freshMs = 1_500)

    @Test
    fun askOnMobileData() = journeys("mobile", freshMs = 3_000)

    private fun journeys(profile: String, freshMs: Long) {
        FakeHost.requireLink()
        pair()
        val failed = listOf({ j4(profile, freshMs) }, { j6(profile, freshMs) })
            .mapNotNull { runCatching(it).exceptionOrNull()?.let { e -> e.message ?: e.javaClass.simpleName } }
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    private fun j4(profile: String, freshMs: Long) {
        val j = Journey("J4", profile, rule).begin()
        val s = Launched.now()
        try {
            fresh()
            clearNotifications()
            ask(QUESTION)
            val n = notification("ask:", QUESTION) ?: throw AssertionError("the question's notification never came")
            j.step("the notification: its Conversation, the card open", taps = 1, targetMs = freshMs) {
                n.notification.contentIntent.send()
                seen(15_000, "the card with its question" to { shown(hasText(QUESTION) and hasAnyAncestor(hasTestTag("ask-card"))) })
            }
            val before = calls().size
            j.step("one tap answers: the card goes", taps = 1, targetMs = freshMs) {
                rule.onNode(hasText("master", substring = true) and hasAnyAncestor(hasTestTag("ask-card"))).performClick()
                until(10_000) { !shown(hasTestTag("ask-card")) }
            }
            val answers = calls(before).count { it.getString("op") == "console.answer" }
            j.check("the answer reached the farm once", answers == 1, "$answers console.answer calls")
            until(10_000) { !shown(hasTestTag("conv-stop")) }
            back()
            ask(SECOND)
            until(15_000) { shown(hasTestTag("waiting-c_psy")) }
            j.step("Now's waiting row: the same card open", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("waiting-c_psy").performClick()
                seen(15_000, "the card with its question" to { shown(hasText(SECOND) and hasAnyAncestor(hasTestTag("ask-card"))) })
            }
            j.step("answered on the PC: the card closes by itself", taps = 0, targetMs = freshMs) {
                FakeHost.post("/_test/answer", """{"id":"c_psy","answer":"yeni dal"}""")
                until(10_000) { !shown(hasTestTag("ask-card")) }
            }
            until(10_000) { !shown(hasTestTag("conv-stop")) }
            FakeHost.post("/_test/turn", """{"id":"c_psy","stepMs":100,"permission":{"tool":"Bash","summary":"$COMMAND"}}""")
            until(15_000) { shown(hasText(COMMAND) and hasAnyAncestor(hasTestTag("ask-card"))) }
            j.step("a permission: Allow, one tap, the card goes", taps = 1, targetMs = freshMs) {
                rule.onNode(hasText(ctx.getString(R.string.allow)) and hasAnyAncestor(hasTestTag("ask-card"))).performClick()
                until(10_000) { !shown(hasTestTag("ask-card")) }
            }
            val allowed = calls(before).filter { it.getString("op") == "needs.act" }.map { args(it).optString("action") }
            j.check("the permission was allowed once, by the farm's key", allowed == listOf("permission-allow"), "needs.act $allowed")
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun j6(profile: String, freshMs: Long) {
        val j = Journey("J6", profile, rule).begin()
        val s = Launched.now()
        try {
            fresh()
            clearNotifications()
            FakeHost.post("/_test/turn", """{"id":"c_psy","stepMs":100,"answer":"$ANSWER"}""")
            val n = notification("turn:c_psy") ?: throw AssertionError("the finished turn's notification never came")
            j.step("the notification: the answer's start", taps = 1, targetMs = freshMs) {
                n.notification.contentIntent.send()
                seen(15_000, "the answer's start" to { startsInView(answered(ANSWER)) })
            }
            back()
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun ask(question: String) =
        FakeHost.post("/_test/turn", """{"id":"c_psy","stepMs":100,"ask":{"question":"$question","options":["master","yeni dal"]}}""")

    /** Back from a Conversation opened over Now is Now, never out of the app. */
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        until { shown(hasTestTag("now-list")) }
    }

    private companion object {
        const val QUESTION = "Hangi dala birleştireyim?"
        const val SECOND = "Sürüm etiketi atılsın mı?"
        const val COMMAND = "git push origin master"
        const val ANSWER = "Bildirimden açıldı: sürüm notu hazır."
    }
}
