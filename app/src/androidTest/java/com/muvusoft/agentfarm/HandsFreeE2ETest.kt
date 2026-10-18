package com.muvusoft.agentfarm

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.net.ShellPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The headset mode's switch (S-7): with the microphone granted, turning it on in Settings brings the link service to
 * the foreground holding the microphone, and turning it off takes the microphone back - read from the system's own
 * record of the service. The line under the switch says whether a headset is there.
 */
@RunWith(AndroidJUnit4::class)
class HandsFreeE2ETest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun off() {
        ShellPrefs(ctx).handsFree = false
    }

    @Test
    fun theSwitchGivesTheLinkServiceTheMicrophoneWhileOnAndTakesItBackOff() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.RECORD_AUDIO)
        val pair = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        ActivityScenario.launch<MainActivity>(pair).use {
            rule.onNodeWithTag("pair-go").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("Paired with", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("before: the link service runs without the microphone", false, microphoneHeld(false))
            rule.onNodeWithTag("open-settings").performClick()
            rule.onNodeWithTag("handsfree-switch").performScrollTo().performClick()
            rule.onNodeWithTag("handsfree-switch").assertIsOn()
            rule.onNodeWithTag("handsfree-state").performScrollTo()
            // What the emulator's audio outputs say: no headset, or one it presents as such.
            val said = hasText(ctx.getString(R.string.handsfree_no_headset)) or hasText(ctx.getString(R.string.handsfree_ready))
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("handsfree-state") and said).fetchSemanticsNodes().isNotEmpty() }
            Shots.take("settings-handsfree")
            assertEquals("on: the link service holds the microphone", true, microphoneHeld(true))
            rule.onNodeWithTag("handsfree-switch").performScrollTo().performClick()
            rule.onNodeWithTag("handsfree-switch").assertIsOff()
            assertEquals("off: the microphone is given back", false, microphoneHeld(false))
        }
    }

    /**
     * Whether the system records the link service as holding the microphone, polled until it says [want] (10 s);
     * the service's own line goes into the failure, so a record read wrongly is seen as such.
     */
    private fun microphoneHeld(want: Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + 10_000
        var line = ""
        while (true) {
            val record = shell("dumpsys activity services ${ctx.packageName}/.net.LinkService")
            line = record.lineSequence().map { it.trim() }.firstOrNull { it.contains("types=") || it.contains("foregroundServiceType") } ?: record.take(600)
            val bits = Regex("(?:types|foregroundServiceType)=(?:0x)?([0-9a-fA-F]+)\\b").find(line)?.groupValues?.get(1)?.toLong(16)
            val held = (bits != null && bits and MICROPHONE != 0L) || Regex("types=\\S*microphone").containsMatchIn(line)
            if (held == want || SystemClock.elapsedRealtime() > end) {
                if (held != want) throw AssertionError("the link service's record: $line")
                return held
            }
            Thread.sleep(250)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private companion object {
        /** ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE */
        const val MICROPHONE = 0x80L
    }
}
