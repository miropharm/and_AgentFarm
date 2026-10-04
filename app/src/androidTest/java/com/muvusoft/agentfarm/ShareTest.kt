package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.lock.LockPolicy
import com.muvusoft.agentfarm.ui.lock.OwnerCheck
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Share → Agent Farm: the text goes into a running Console session through console.send. */
@RunWith(AndroidJUnit4::class)
class ShareTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pair() {
        val pair = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        ActivityScenario.launch<MainActivity>(pair).use {
            rule.onNodeWithTag("pair-go").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("Paired with", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun shared(subject: String?, words: String): Intent {
        val share = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, words)
        return if (subject == null) share else share.putExtra(Intent.EXTRA_SUBJECT, subject)
    }

    private fun farmState(): String = FakeHost.get(Pairing.parse(FakeHost.requireLink())!!, "/_test/state")

    private fun waitOutcome(part: String) = rule.waitUntil(15_000) {
        rule.onAllNodes(hasTestTag("share-outcome") and hasText(part, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aSharedTextGoesIntoTheChosenRunningSession() {
        pair()
        ActivityScenario.launch<MainActivity>(shared("Okuma listesi", "Şu yazıya bak: https://ornek.test/yazi")).use {
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("share-to-c_dev")).fetchSemanticsNodes().isNotEmpty() }
            // A running session is offered first and chosen; an ended one is not offered at all.
            rule.onNodeWithTag("share-to-c_dev").assertIsSelected()
            assertTrue(rule.onAllNodes(hasTestTag("share-to-c_old")).fetchSemanticsNodes().isEmpty())
            rule.waitForIdle()
            Shots.take("share-sheet")
            rule.onNodeWithTag("share-send").performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag("share-outcome")).fetchSemanticsNodes().isNotEmpty() }
            val state = farmState()
            assertTrue(state, Regex("\"op\"\\s*:\\s*\"console.send\"").containsMatchIn(state))
            assertTrue(state, state.contains("c_dev") && state.contains("Okuma listesi"))
            rule.waitForIdle()
            Shots.take("share-sent")
        }
    }

    @Test
    fun aCommandThatCannotBeUndoneWaitsForTheOwnersYesAndGoesAgainWithIt() {
        pair()
        val confirmVerb = ctx.getString(R.string.share_confirm)
        ActivityScenario.launch<MainActivity>(shared(null, "Clean it first: rm -rf build")).use {
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("share-to-c_dev")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("share-send").performClick()
            // The farm stopped it: its own sentence is shown and the button asks for the yes.
            waitOutcome("cannot be undone")
            rule.onNodeWithTag("share-send").assert(hasText(confirmVerb))
            rule.waitForIdle()
            Shots.take("share-confirm")
            assumeTrue(
                "this emulator has a screen lock: the system prompt cannot be answered by a test",
                OwnerCheck.availability(ctx) != LockPolicy.Availability.READY,
            )
            rule.onNodeWithTag("share-send").performClick()
            rule.onNodeWithTag("destructive-dialog").assertIsDisplayed()
            rule.waitForIdle()
            Shots.take("share-confirm-dialog")
            rule.onNode(hasText(confirmVerb) and hasAnyAncestor(hasTestTag("destructive-dialog"))).performClick()
            // The same text went again as a new call carrying the yes, and the farm took it.
            waitOutcome("→ developer")
            val state = farmState()
            assertTrue(state, Regex("\"confirm\"\\s*:\\s*\"risk\"").containsMatchIn(state))
            rule.waitForIdle()
            Shots.take("share-confirmed")
        }
    }
}
