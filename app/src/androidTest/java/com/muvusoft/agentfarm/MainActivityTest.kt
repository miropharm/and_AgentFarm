package com.muvusoft.agentfarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.core.GREETING_TITLE
import com.muvusoft.agentfarm.core.buildLabel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun greetingAndVersionAreShown() {
        rule.onNodeWithText(GREETING_TITLE).assertIsDisplayed()
        rule.onNodeWithText(buildLabel(BuildConfig.VERSION_NAME)).assertIsDisplayed()
    }
}
