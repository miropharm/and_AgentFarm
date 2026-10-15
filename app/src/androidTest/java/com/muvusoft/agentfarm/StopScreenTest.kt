package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
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

/** The widget's stop: the running turns are offered, and the picked one is stopped with console.interrupt after the owner's yes. */
@RunWith(AndroidJUnit4::class)
class StopScreenTest {
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

    @Test
    fun theWidgetsStopStopsThePickedTurnAfterTheOwnersYes() {
        pair()
        val open = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_STOP, true)
        ActivityScenario.launch<MainActivity>(open).use {
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("stop-to-c_dev")).fetchSemanticsNodes().isNotEmpty() }
            // The running turn is offered and picked; an ended session has nothing to stop.
            rule.onNodeWithTag("stop-to-c_dev").assertIsSelected()
            assertTrue(rule.onAllNodes(hasTestTag("stop-to-c_old")).fetchSemanticsNodes().isEmpty())
            rule.waitForIdle()
            Shots.take("stop-sheet")
            assumeTrue(
                "this emulator has a screen lock: the system prompt cannot be answered by a test",
                OwnerCheck.availability(ctx) != LockPolicy.Availability.READY,
            )
            rule.onNodeWithTag("stop-go").performClick()
            rule.onNodeWithTag("destructive-dialog").assertIsDisplayed()
            rule.waitForIdle()
            Shots.take("stop-confirm-dialog")
            val verb = ctx.getString(R.string.stop_go)
            rule.onNode(hasText(verb) and hasAnyAncestor(hasTestTag("destructive-dialog"))).performClick()
            // The farm's own words for how it stopped are shown, and the call named the picked session.
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("stop-outcome") and hasText("Stopped:", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            val state = FakeHost.get(Pairing.parse(FakeHost.requireLink())!!, "/_test/state")
            assertTrue(state, Regex("\"op\"\\s*:\\s*\"console.interrupt\"\\s*,\\s*\"args\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"c_dev\"").containsMatchIn(state))
            rule.waitForIdle()
            Shots.take("stop-done")
        }
    }
}
