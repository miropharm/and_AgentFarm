package com.muvusoft.agentfarm

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.net.PairClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The farm's VS Code is closed: its headless service answers, the phone says so and can start Agent Farm (X-449). */
@RunWith(AndroidJUnit4::class)
class AwayE2ETest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var farmId: String

    @Before
    fun pairFirst() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val r = runBlocking { PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) }
        val farm = (r as PairingVerdict.Result.Paired).farm
        FarmStore(ctx).save(farm)
        farmId = farm.id
        rule.activityRule.scenario.recreate()
    }

    @After
    fun farmBack() {
        FakeHost.post("/_test/away", "{\"away\":false}")
    }

    private fun lineSays(text: String, timeoutMs: Long = 30_000) = rule.waitUntil(timeoutMs) {
        rule.onAllNodes(hasTestTag("link-$farmId") and hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun shows(tag: String, timeoutMs: Long = 30_000) = rule.waitUntil(timeoutMs) {
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aFarmWithVsCodeClosedSaysSoAndOpensAgentFarmFromThePhone() {
        lineSays("Connected")
        FakeHost.post("/_test/away", "{\"away\":true}")
        lineSays("VS Code is closed")
        Shots.take("away-row")

        rule.onNodeWithTag("farm-$farmId").performClick()
        shows("away")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("2 waiting for you", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        Shots.take("away-screen")

        rule.onNodeWithTag("away-open").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("away-answer") and hasText("Starting VS Code", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        Shots.take("away-opening")
        // The service lets go of the socket once VS Code is up; the shell reconnects to the full farm and its page opens.
        shows("farm-page", 60_000)
    }
}
