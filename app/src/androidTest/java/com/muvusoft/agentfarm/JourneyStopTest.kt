package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
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
import com.muvusoft.agentfarm.core.lock.LockPolicy
import com.muvusoft.agentfarm.ui.lock.OwnerCheck
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J5, "stop the turn", through the widget's door, on the LAN and on mobile data: the running turns come
 * fresh from the farm, the running one is already picked, Stop + its confirmation stop it and the farm's
 * own words say so. Measured on the scripted farm; journeys.txt carries the steps.
 */
@RunWith(AndroidJUnit4::class)
class JourneyStopTest {
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

    private fun seen(tag: String, text: String? = null, ms: Long = 10_000) = rule.waitUntil(ms) {
        rule.onAllNodes(if (text == null) hasTestTag(tag) else hasTestTag(tag) and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun stopFromTheWidgetOnTheLan() = stop("lan", freshMs = 1_500)

    @Test
    fun stopFromTheWidgetOnMobileData() = stop("mobile", freshMs = 3_000)

    private fun stop(profile: String, freshMs: Long) {
        assumeTrue(
            "this emulator has a screen lock: the system prompt cannot be answered by a test",
            OwnerCheck.availability(ctx) != LockPolicy.Availability.READY,
        )
        FakeHost.requireLink()
        pair()
        val j = Journey("J5", profile, rule).begin()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            j.step("widget stop opens the running turns", taps = 1, targetMs = freshMs) {
                scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_STOP, true))
                seen("stop-to-c_dev")
                rule.onNodeWithTag("stop-to-c_dev").assertIsSelected()
            }
            // The design gives every answer of this journey 1 s (test-plan J5); the question is the phone's own.
            j.step("stop asks for the yes", taps = 1, targetMs = 1_000) {
                rule.onNodeWithTag("stop-go").performClick()
                seen("destructive-dialog", ms = 5_000)
            }
            j.step("the yes stops it", taps = 1, targetMs = 1_000) {
                rule.onNode(hasText(ctx.getString(R.string.stop_go)) and hasAnyAncestor(hasTestTag("destructive-dialog"))).performClick()
                seen("stop-outcome", "Stopped:")
            }
            j.economy()
        } finally {
            scenario?.close()
        }
        j.end()
    }
}
