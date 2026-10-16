package com.muvusoft.agentfarm

import android.app.UiAutomation
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.ScreenFixtures.NOW
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.ui.components.AskActions
import com.muvusoft.agentfarm.ui.components.ComposerActions
import com.muvusoft.agentfarm.ui.conversation.ConversationActions
import com.muvusoft.agentfarm.ui.conversation.ConversationScreen
import com.muvusoft.agentfarm.ui.now.NowActions
import com.muvusoft.agentfarm.ui.now.NowScreen
import com.muvusoft.agentfarm.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The visual language, drawn: Now and Conversation in every state the user meets, light and dark, at 200 %
 * font and in landscape, from the user's kind of data. What each scene must show is asserted on screen; the
 * shots (shots/u17-*.png) are what the user approves the look from. shots/u17-state.txt proves each capture.
 */
@RunWith(AndroidJUnit4::class)
class VisualLanguageTest {
    @get:Rule
    val rule = createComposeRule()

    private class Scene(val name: String, val dark: Boolean, val font: Float, val draw: @Composable () -> Unit)

    private var scene by mutableStateOf<Scene?>(null)

    private val nowActions = NowActions(
        onOpen = {}, onQuick = { _, _ -> }, onNew = {}, onRefresh = {}, onMoreRecent = {}, onRetry = {}, onFarm = {}, onMenu = {},
    )
    private val convActions = ConversationActions(
        onBack = {}, onDetails = {}, onStop = {}, onOverflow = {}, onOlder = {}, onRetry = {}, onShowAll = {},
        ask = AskActions(onAnswer = {}, onFree = {}, onAllow = {}, onDeny = {}),
        composer = ComposerActions(onText = {}, onSend = {}, onAttach = {}, onDictate = {}),
    )

    private fun now(s: NowState): @Composable () -> Unit = { NowScreen(s, NOW, refreshing = false, actions = nowActions) }
    private fun conv(s: ConversationState, draft: String = ""): @Composable () -> Unit =
        { ConversationScreen(s, NOW, draft, convActions) }

    private fun start() {
        rule.setContent {
            val s = scene ?: return@setContent
            key(s.name, s.dark, s.font) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, s.font)) {
                    AppTheme(dark = s.dark) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { s.draw() }
                    }
                }
            }
        }
    }

    private fun shoot(name: String, dark: Boolean, font: Float = 1f, draw: @Composable () -> Unit, check: () -> Unit = {}) {
        scene = Scene(name, dark, font, draw)
        rule.waitForIdle()
        check()
        val file = "u17-$name-${if (dark) "dark" else "light"}" + (if (font != 1f) "-font200" else "") + (if (rotation() % 2 == 1) "-land" else "")
        Shots.take(file)
        record(file, dark, font)
    }

    private fun both(name: String, draw: @Composable () -> Unit, check: () -> Unit = {}) {
        shoot(name, dark = false, draw = draw, check = check)
        shoot(name, dark = true, draw = draw, check = check)
    }

    @Test
    fun nowInEveryState() {
        start()
        both("now-loading", now(ScreenFixtures.nowLoading)) { rule.onNodeWithTag("skeleton").assertIsDisplayed() }
        both("now-empty", now(ScreenFixtures.nowEmpty)) { rule.onNodeWithText("Nothing is running right now").assertIsDisplayed() }
        both("now-one", now(ScreenFixtures.nowOne)) { rule.onNodeWithTag("running-c_dev").assertIsDisplayed() }
        both("now-many", now(ScreenFixtures.nowMany)) {
            rule.onNodeWithText("Waiting for you").assertIsDisplayed()
            rule.onNodeWithText("Allow").assertIsDisplayed()
            rule.onNodeWithText("Evet").assertIsDisplayed()
        }
        both("now-offline", now(ScreenFixtures.nowOffline)) { rule.onNodeWithText("Offline · updated 12 min ago").assertIsDisplayed() }
        both("now-error", now(ScreenFixtures.nowError)) { rule.onNodeWithTag("retry").assertIsDisplayed() }
    }

    @Test
    fun conversationInEveryState() {
        start()
        // It opens at the START of the last answer: its heading is on screen, not the end of a long answer.
        both("conv-answer", conv(ScreenFixtures.convAnswer)) { rule.onNodeWithText(ScreenFixtures.LAST_ANSWER_HEAD).assertIsDisplayed() }
        both("conv-live", conv(ScreenFixtures.convLive)) {
            rule.onNodeWithTag("live").assertIsDisplayed()
            rule.onNodeWithText("Sending…").assertIsDisplayed()
        }
        // A scene that needs a press presses it: the folded steps, opened.
        shoot("conv-steps-open", dark = false, draw = conv(ScreenFixtures.convLive)) {
            rule.onNodeWithText("3 steps · Bash, Read · 1 failed").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("./gradlew connectedDebugAndroidTest").assertIsDisplayed()
        }
        both("conv-question", conv(ScreenFixtures.convQuestion)) { rule.onNodeWithText("Hep koyu").assertIsDisplayed() }
        both("conv-permission", conv(ScreenFixtures.convPermission)) { rule.onNodeWithText("git push origin master --follow-tags").assertIsDisplayed() }
        both("conv-after-turn", conv(ScreenFixtures.convAfterTurn, draft = "Testler bitince haber ver")) {
            rule.onNodeWithTag("after-turn").assertIsDisplayed()
        }
        both("conv-read-only", conv(ScreenFixtures.convReadOnly)) { rule.onNodeWithTag("read-only").assertIsDisplayed() }
        both("conv-offline", conv(ScreenFixtures.convOffline)) { rule.onNodeWithTag("stale").assertIsDisplayed() }
        both("conv-loading", conv(ScreenFixtures.convLoading)) { rule.onNodeWithTag("skeleton").assertIsDisplayed() }
    }

    @Test
    fun theLargestFontBreaksNothing() {
        start()
        shoot("now-many", dark = false, font = 2f, draw = now(ScreenFixtures.nowMany)) { rule.onNodeWithText("Waiting for you").assertIsDisplayed() }
        shoot("conv-answer", dark = false, font = 2f, draw = conv(ScreenFixtures.convAnswer)) {
            rule.onNodeWithText(ScreenFixtures.LAST_ANSWER_HEAD).assertIsDisplayed()
        }
        shoot("conv-question", dark = true, font = 2f, draw = conv(ScreenFixtures.convQuestion)) { rule.onNodeWithText("Hep koyu").assertIsDisplayed() }
    }

    @Test
    fun landscape() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(1500)
            start()
            shoot("now-many", dark = false, draw = now(ScreenFixtures.nowMany)) { rule.onNodeWithText("Waiting for you").assertIsDisplayed() }
            shoot("conv-answer", dark = true, draw = conv(ScreenFixtures.convAnswer)) {
                rule.onNodeWithText(ScreenFixtures.LAST_ANSWER_HEAD).assertIsDisplayed()
            }
            shoot("conv-permission", dark = false, draw = conv(ScreenFixtures.convPermission)) { rule.onNodeWithText("Allow").assertIsDisplayed() }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun rotation(): Int =
        (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY).rotation

    /** What each capture really was: the display's rotation, the theme drawn, the font scale. */
    private fun record(file: String, dark: Boolean, font: Float) {
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "u17-state.txt").appendText("$file rotation=${rotation()} dark=$dark fontScale=$font\n")
    }
}
