package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The pairing screen, driven the way a user does: a scanned link opens the app, one press pairs. */
@RunWith(AndroidJUnit4::class)
class PairingScreenTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun open(link: String?): ActivityScenario<MainActivity> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(ctx, MainActivity::class.java)
        if (link != null) intent.action = Intent.ACTION_VIEW
        if (link != null) intent.data = Uri.parse(link)
        return ActivityScenario.launch(intent)
    }

    @Test
    fun aScannedLinkPairsWithOnePress() {
        val link = FakeHost.freshLink()
        open(link).use {
            rule.onNodeWithTag("pair-go").performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("pair-message") and hasText("Paired with", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(hasTestTag("farm-farm_fake")).assertIsDisplayed()
        }
    }

    @Test
    fun aPastedStrangerLinkIsNamedAsSuch() {
        open(null).use {
            rule.onNodeWithTag("pair-link").performTextReplacement("https://example.com/pair")
            rule.onNodeWithTag("pair-go").performClick()
            rule.onNode(hasTestTag("pair-message") and hasText("not an Agent Farm pairing link", substring = true)).assertIsDisplayed()
        }
    }
}
