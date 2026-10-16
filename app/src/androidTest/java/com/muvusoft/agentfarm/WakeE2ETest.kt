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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The farm's PC sleeps: its network card from the last welcome is woken from the phone, and the farm opens once it answers. */
@RunWith(AndroidJUnit4::class)
class WakeE2ETest {
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
    fun pcAwake() {
        FakeHost.post("/_test/sleep", "{\"asleep\":false}")
    }

    private fun shows(tag: String, text: String? = null, timeoutMs: Long = 30_000) = rule.waitUntil(timeoutMs) {
        val m = if (text == null) hasTestTag(tag) else hasTestTag(tag) and hasText(text, substring = true)
        rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aSleepingPcIsWokenFromThePhoneAndItsFarmOpensOnceItAnswers() {
        shows("link-$farmId", "Connected")
        // The welcome named the farm's card; the phone keeps it for when the PC cannot answer.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("02:00:5e:10:00:01", AgentFarmApp.of(ctx).prefs.wake(farmId).single().mac)

        FakeHost.post("/_test/sleep", "{\"asleep\":true}")
        rule.onNodeWithTag("farm-$farmId").performClick()
        shows("wake-go")
        rule.waitForIdle()
        Shots.take("wake-offered")

        rule.onNodeWithTag("wake-go").performClick()
        shows("wake-outcome", "Wake signal sent", 10_000)
        rule.waitForIdle()
        Shots.take("wake-sent")

        // The PC is up: the tries after the wake reach it before the backoff would have, and its Now opens.
        FakeHost.post("/_test/sleep", "{\"asleep\":false}")
        shows("now-farm", timeoutMs = 45_000)
    }
}
