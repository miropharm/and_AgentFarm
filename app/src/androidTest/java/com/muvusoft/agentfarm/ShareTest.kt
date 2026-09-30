package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Share → Agent Farm: the text goes into a running Console session through console.send. */
@RunWith(AndroidJUnit4::class)
class ShareTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aSharedTextGoesIntoTheChosenRunningSession() {
        val pair = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        ActivityScenario.launch<MainActivity>(pair).use {
            rule.onNodeWithTag("pair-go").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("eşlendi", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val words = "Şu yazıya bak: https://ornek.test/yazi"
        val share = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Okuma listesi").putExtra(Intent.EXTRA_TEXT, words)
        ActivityScenario.launch<MainActivity>(share).use {
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("share-to-c_dev")).fetchSemanticsNodes().isNotEmpty() }
            // A running session is offered first and chosen; an ended one is not offered at all.
            rule.onNodeWithTag("share-to-c_dev").assertIsSelected()
            assertTrue(rule.onAllNodes(hasTestTag("share-to-c_old")).fetchSemanticsNodes().isEmpty())
            rule.waitForIdle()
            Shots.take("share-sheet")
            rule.onNodeWithTag("share-send").performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag("share-outcome")).fetchSemanticsNodes().isNotEmpty() }
            val state = FakeHost.get(Pairing.parse(FakeHost.requireLink())!!, "/_test/state")
            assertTrue(state, Regex("\"op\"\\s*:\\s*\"console.send\"").containsMatchIn(state))
            assertTrue(state, state.contains("c_dev") && state.contains("Okuma listesi"))
            rule.waitForIdle()
            Shots.take("share-sent")
        }
    }
}
