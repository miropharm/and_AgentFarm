package com.muvusoft.agentfarm

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.view.KeyEvent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.lock.LockPolicy
import com.muvusoft.agentfarm.core.power.BatteryPolicy
import com.muvusoft.agentfarm.ui.lock.OwnerCheck
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's own screens as a user reaches them: the link sheet by long-press, settings, and the forget door. */
@RunWith(AndroidJUnit4::class)
class ShellScreensTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val farm = "farm-farm_fake"

    private fun pairedApp(): ActivityScenario<MainActivity> {
        val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(FakeHost.freshLink()))
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        rule.onNodeWithTag("pair-go").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasTestTag("pair-message") and hasText("ile eşlendi", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        return scenario
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Shots.take(name)
    }

    @Test
    fun aLongPressOpensTheLinkSheetAndBackClosesIt() {
        pairedApp().use {
            rule.onNodeWithTag(farm).performTouchInput { longClick() }
            rule.onNodeWithTag("aftip").assertIsDisplayed()
            rule.onNode(hasText("Adresler:", substring = true)).assertIsDisplayed()
            shot("farm-link-sheet")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("aftip")).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag(farm).assertIsDisplayed()
        }
    }

    @Test
    fun settingsNamesWhyTheLockIsOffAndBackReturns() {
        pairedApp().use {
            rule.onNodeWithTag("open-settings").performClick()
            rule.onNodeWithTag("settings").assertIsDisplayed()
            if (OwnerCheck.availability(ctx) != LockPolicy.Availability.READY) {
                rule.onNodeWithTag("lock-switch").assertIsNotEnabled()
                rule.onNodeWithTag("lock-reason").assertIsDisplayed()
            }
            val exempt = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
            rule.onNodeWithTag("battery-state").assertTextEquals(BatteryPolicy.line(exempt).state)
            shot("settings")
            rule.onNodeWithTag("settings-back").performClick()
            rule.onNodeWithTag(farm).assertIsDisplayed()
        }
    }

    @Test
    fun talkBackReadsTheLockRowAsOneSwitchAndTheSectionsAsHeadings() {
        pairedApp().use {
            rule.onNodeWithContentDescription(ctx.getString(R.string.open_settings)).performClick()
            rule.onNodeWithTag("lock-switch")
                .assert(isToggleable())
                .assert(hasText(ctx.getString(R.string.lock_on_open), substring = true))
                .assert(hasText(ctx.getString(R.string.lock_on_open_detail), substring = true))
            // The screen title and its four sections.
            rule.onAllNodes(isHeading()).assertCountEquals(5)
            // Reading aloud is exactly one of three; a choice sticks.
            rule.onNodeWithTag("speak-off").assertIsSelected()
            rule.onNodeWithTag("speak-asks").performScrollTo().performClick().assertIsSelected()
            rule.onNodeWithTag("speak-off").assertIsNotSelected()
            shot("settings-speech")
            rule.onNodeWithTag("speak-off").performScrollTo().performClick()
            rule.onNodeWithContentDescription(ctx.getString(R.string.back)).performClick()
            // The card carries the tag; the row inside it takes the tap and names it.
            rule.onNodeWithTag(farm).onChildren().assertAny(hasClickAction())
        }
    }

    @Test
    fun theWidgetsAndTilesTapOpensTheFocusedFarmsPage() {
        pairedApp().use { }
        val now = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_NOW, true)
        ActivityScenario.launch<MainActivity>(now).use {
            rule.waitUntil(15_000) {
                rule.onAllNodes(hasTestTag("farm-page") or hasTestTag("page-wait")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun forgettingAFarmAsksFirstAndGivingUpKeepsIt() {
        pairedApp().use {
            rule.onNodeWithTag("forget-farm_fake").performClick()
            if (OwnerCheck.availability(ctx) != LockPolicy.Availability.READY) {
                rule.onNodeWithTag("destructive-dialog").assertIsDisplayed()
                shot("forget-confirm")
                rule.onNodeWithText(ctx.getString(R.string.cancel)).performClick()
            }
            rule.onNodeWithTag(farm).assertIsDisplayed()
        }
    }
}
