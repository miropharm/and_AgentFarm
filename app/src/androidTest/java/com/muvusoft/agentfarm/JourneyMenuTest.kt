package com.muvusoft.agentfarm

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J10, the farm's other pages, on the scripted farm, on the LAN and on mobile data: Now's Menu lists them and one
 * opens in two taps under the phone's own top bar, which names it by the page's own title; Back is the Menu; a
 * session row on the page opens the phone's own Conversation, and Back from it is the page. Each time the Menu is
 * shown it asks the farm's list once (pages.list), drawing its last list meanwhile.
 */
@RunWith(AndroidJUnit4::class)
class JourneyMenuTest : JourneyBase() {
    @Test
    fun menuOnTheLan() = j10("lan", freshMs = 1_500)

    @Test
    fun menuOnMobileData() = j10("mobile", freshMs = 3_000)

    private fun j10(profile: String, freshMs: Long) {
        FakeHost.requireLink()
        pair()
        val j = Journey("J10", profile, rule).begin()
        val s = Launched.now()
        try {
            fresh()
            WebPages.warm()
            // A history row's session, the kind a farm page lists: its id is the farm's, read off Now's own row.
            val history = { rule.onAllNodes(tagStarts("recent-"), useUnmergedTree = true).fetchSemanticsNodes() }
            until(15_000) { history().isNotEmpty() }
            val session = history().first().config[SemanticsProperties.TestTag].removePrefix("recent-")
            var menus = 0
            j.step("the Menu: the farm's pages in their groups", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("nav-menu").performClick()
                seen(15_000, "the farm's Tasks page" to { shown(hasTestTag("menu-tasks")) })
            }
            menus++
            j.step("a page: under the phone's own bar, named by its title", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("menu-tasks").performClick()
                seen(15_000, "the bar's title" to { shown(hasTestTag("page-title") and hasText(PAGE)) })
            }
            j.step("Back: the Menu", taps = 1, targetMs = freshMs) {
                rule.onNodeWithTag("page-back").performClick()
                seen(10_000, "the Menu" to { shown(hasTestTag("menu-tasks")) })
            }
            menus++
            rule.onNodeWithTag("menu-tasks").performClick()
            until(15_000) { shown(hasTestTag("page-title") and hasText(PAGE)) && WebPages.js(TITLE) == PAGE }
            j.step("a session row on the page: the phone's own Conversation", taps = 1, targetMs = freshMs) {
                WebPages.js("acquireVsCodeApi().postMessage({type:'afnav',to:'session',args:{sessionId:'$session'}})")
                seen(15_000, "the Conversation" to { shown(hasTestTag("conv-title")) })
            }
            j.step("Back: the page it was opened from", taps = 1, targetMs = freshMs) {
                back()
                seen(15_000, "the page" to { shown(hasTestTag("page-title") and hasText(PAGE)) })
            }
            back()
            until(10_000) { shown(hasTestTag("menu-tasks")) }
            menus++
            back()
            until(10_000) { shown(hasTestTag("now-list")) }
            val asked = calls().count { it.getString("op") == "pages.list" }
            j.check("the farm's list asked once each time the Menu was shown", asked == menus, "pages.list ×$asked, Menu shown $menus times")
            j.economy()
        } finally {
            s.close()
        }
        j.end()
    }

    private fun back() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private companion object {
        const val PAGE = "Fake page: tasks"
        const val TITLE = "document.getElementById('title') ? document.getElementById('title').textContent : ''"
    }
}
