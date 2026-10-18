package com.muvusoft.agentfarm

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The main screens leave nothing behind once closed: Now, a Conversation turned with the phone, the Menu and one
 * of the farm's pages, the new-session sheet, Now turned, then the activity closes. A dump of the heap read by
 * Shark (Leaks) must find no destroyed activity, fragment or view the app's code still holds; one held only by
 * Android's own known paths is counted. The lines go into journeys.txt (Q1 · lan).
 */
@RunWith(AndroidJUnit4::class)
class LeakE2ETest : JourneyBase() {
    @Test
    fun theMainScreensLeaveNothingBehindOnceClosed() {
        FakeHost.requireLink()
        pair()
        val j = Journey("Q1", "lan", rule).begin()
        open().use { s ->
            fresh()
            rule.onNodeWithTag("running-c_dev").performClick()
            until(15_000) { shown(hasTestTag("conv-list")) }
            s.recreate()
            until(15_000) { shown(hasTestTag("conv-list")) }
            backFrom("conv-list")
            fresh()
            rule.onNodeWithTag("nav-menu").performClick()
            until(15_000) { shown(hasTestTag("menu-tasks")) }
            rule.onNodeWithTag("menu-tasks").performClick()
            until(15_000) { shown(hasTestTag("page-title")) }
            backFrom("page-title")
            backFrom("menu-tasks")
            rule.onNodeWithTag("now-new").performClick()
            until { shown(hasTestTag("new-session")) }
            backFrom("new-session")
            s.recreate()
            fresh()
        }
        val found = Leaks.find(ctx)
        found.app.forEach { Log.e(TAG, "held once closed:\n$it") }
        j.check("no screen held once closed", found.app.isEmpty(), if (found.app.isEmpty()) "none" else found.app.joinToString(" || ") { it.replace('\n', ' ') })
        j.note("held only through Android's own known paths: ${found.library.size}" + found.library.joinToString("") { " · " + it.take(120) })
        j.end()
    }

    private companion object {
        const val TAG = "AFLeaks"
    }
}
