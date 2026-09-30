package com.muvusoft.agentfarm

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.ui.lock.LockGate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The open lock with a stand-in for the system prompt: the owner's answer is the only way in. */
@RunWith(AndroidJUnit4::class)
class LockGateTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aRefusedOwnerStaysOutAnAcceptedOneGetsIn() {
        var asked = 0
        var locked = 0
        var answer = false
        rule.setContent {
            LockGate(enabled = true, ask = { onResult -> asked++; onResult(answer) }, onLocked = { locked++ }) { Text("içerik") }
        }
        rule.onNodeWithTag("lock-screen").assertIsDisplayed()
        assertEquals(1, asked)
        assertEquals(1, locked)
        assertEquals(0, rule.onAllNodes(hasText("içerik")).fetchSemanticsNodes().size)

        answer = true
        rule.onNodeWithTag("lock-open").performClick()
        rule.onNodeWithText("içerik").assertIsDisplayed()
        assertEquals(2, asked)
    }

    @Test
    fun anOffLockNeverAsks() {
        var asked = 0
        var locked = 0
        rule.setContent {
            LockGate(enabled = false, ask = { asked++ }, onLocked = { locked++ }) { Text("içerik") }
        }
        rule.onNodeWithText("içerik").assertIsDisplayed()
        assertEquals(0, asked)
        assertEquals(0, locked)
    }
}
