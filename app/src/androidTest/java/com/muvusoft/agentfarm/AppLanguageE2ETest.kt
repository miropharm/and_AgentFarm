package com.muvusoft.agentfarm

import android.os.SystemClock
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.net.FarmStore
import com.muvusoft.agentfarm.net.ShellPrefs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's own screens speak the language its farm draws its pages in: the PC's setting changes, the farm tells
 * it at once (and in every welcome), and the screen the person was on is drawn again in it. Night mode, a farm
 * page's WebView (which once reset an app's language) and changes replayed after a reconnect leave it as it is.
 */
@RunWith(AndroidJUnit4::class)
class AppLanguageE2ETest : JourneyBase() {
    @After
    fun backToEnglish() {
        FakeHost.post("/_test/sleep", "{\"asleep\":false}")
        FakeHost.post("/_test/lang", "{\"lang\":\"en\"}")
        val prefs = ShellPrefs(ctx)
        FarmStore(ctx).load().forEach { prefs.forgetLanguage(it.id) }
    }

    private fun nightMode(on: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night ${if (on) "yes" else "no"}").close()
    }

    @Test
    fun theAppFollowsItsFarmsLanguageLiveAndKeepsItsPlace() {
        FakeHost.requireLink()
        pair()
        open().use {
            fresh()
            seen(10_000, "Now's tab in English" to { shown(hasText("Now")) }, "the Menu tab in English" to { shown(hasText("Menu")) })

            FakeHost.post("/_test/lang", "{\"lang\":\"tr\"}")
            seen(30_000,
                "Now's tab in Turkish" to { shown(hasText("Şimdi")) },
                "the Menu tab in Turkish" to { shown(hasText("Menü")) },
                "still on Now" to { shown(hasTestTag("now-list")) })
            Shots.take("lang-tr-now")

            nightMode(true)
            try {
                Thread.sleep(2_000)
                seen(10_000, "Turkish at night" to { shown(hasText("Şimdi")) })
                Shots.take("lang-tr-night")
            } finally {
                nightMode(false)
            }

            rule.onNodeWithTag("nav-menu").performClick()
            seen(15_000, "the Menu in Turkish" to { shown(hasTestTag("menu-list")) && shown(hasText("Menü")) })
            rule.onNodeWithTag("menu-tasks").performClick()
            seen(15_000, "a farm page" to { shown(hasTestTag("page-title")) })
            rule.onNodeWithTag("page-back").performClick()
            seen(10_000, "the Menu still in Turkish after a WebView" to {
                shown(hasTestTag("menu-list")) && shown(hasText("Menü")) && shown(hasText("Şimdi"))
            })
            Shots.take("lang-tr-menu")

            // Changes made while the PC slept come back as replayed events after a welcome that already tells the
            // last one: the screen must not pass through them (English, then Turkish again).
            FakeHost.post("/_test/sleep", "{\"asleep\":true}")
            until(15_000) { JSONObject(FakeHost.get("/_test/state")).getInt("sockets") == 0 }
            FakeHost.post("/_test/lang", "{\"lang\":\"en\"}")
            FakeHost.post("/_test/lang", "{\"lang\":\"tr\"}")
            FakeHost.post("/_test/sleep", "{\"asleep\":false}")
            until(60_000) { JSONObject(FakeHost.get("/_test/state")).getInt("sockets") > 0 }
            val end = SystemClock.uptimeMillis() + 5_000
            var flipped = false
            while (SystemClock.uptimeMillis() < end) {
                if (shown(hasText("Menu"))) flipped = true
                Thread.sleep(100)
            }
            assertFalse("a replayed language change redrew the screen in English", flipped)
            seen(5_000, "Turkish after the PC woke" to { shown(hasText("Menü")) })

            FakeHost.post("/_test/lang", "{\"lang\":\"en\"}")
            seen(30_000, "the Menu tab in English again" to { shown(hasText("Menu")) && !shown(hasText("Menü")) })
        }
    }
}
