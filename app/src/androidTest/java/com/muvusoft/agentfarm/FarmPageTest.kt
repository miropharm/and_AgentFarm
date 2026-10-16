package com.muvusoft.agentfarm

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A farm row opens the farm's page in the WebView: HTML and resources come through the native interceptor. */
@RunWith(AndroidJUnit4::class)
class FarmPageTest {
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
        warmWebView()
    }

    /**
     * The CI emulator starts the WebView engine for the first time in 25-65 s (logcat of runs
     * 36693541296 and 36695915061: the page's own requests began only then, and then took under a
     * second). A throwaway page absorbs that start, so the test measures the shell, not the emulator.
     */
    private fun warmWebView() {
        val done = CountDownLatch(1)
        var warm: WebView? = null
        rule.activityRule.scenario.onActivity { a ->
            warm = WebView(a).apply {
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) = done.countDown()
                }
                loadData("<p>warm</p>", "text/html", null)
            }
        }
        done.await(180, TimeUnit.SECONDS)
        // Destroyed afterwards, never from inside its own callback (that killed the renderer, run 36696690290).
        rule.activityRule.scenario.onActivity { warm?.destroy() }
    }

    private fun webView(): WebView? {
        var found: WebView? = null
        rule.activityRule.scenario.onActivity { a -> found = find(a.window.decorView) }
        return found
    }

    private fun find(v: View): WebView? = when (v) {
        is WebView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }

    private fun js(script: String): String {
        var out = ""
        val done = CountDownLatch(1)
        rule.activityRule.scenario.onActivity { a ->
            val w = find(a.window.decorView)
            if (w == null) done.countDown() else w.evaluateJavascript(script) { out = it; done.countDown() }
        }
        done.await(5, TimeUnit.SECONDS)
        return out.trim('"')
    }

    private fun jsUntil(script: String, want: (String) -> Boolean): String {
        // The first WebView of a cold emulator starts its engine process in ~25 s (logcat, CI 36693541296).
        val end = System.currentTimeMillis() + 60_000
        var last = ""
        while (System.currentTimeMillis() < end) {
            // The test drives Compose's frame clock: without a sync the page's WebView is never even
            // composed (CI 36699469608: the page loaded the moment the loop gave up and synced).
            rule.waitForIdle()
            if (webView() != null) { last = js(script); if (want(last)) return last }
            Thread.sleep(300)
        }
        return last
    }

    /** What was on screen instead of the page: the wait sentence, or the WebView's address and text. */
    private fun diagnosis(): String {
        val wait = rule.onAllNodes(hasTestTag("page-wait"), useUnmergedTree = true).fetchSemanticsNodes()
            .joinToString { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() }
        val link = AgentFarmApp.of(InstrumentationRegistry.getInstrumentation().targetContext).manager.state.value.farm(farmId)?.link
        if (webView() == null) return "no WebView; wait=[$wait]; link=$link"
        return "WebView at ${js("location.href")}; body=[${js("document.body ? document.body.innerText.slice(0,200) : 'no body'")}]; link=$link"
    }

    private fun openFarm() {
        rule.waitUntil(20_000) {
            // The row is clickable, so its texts are merged into it; the link line is found in the unmerged tree.
            rule.onAllNodes(hasTestTag("link-$farmId") and hasText("Connected", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("farm-$farmId").performClick()
        // A farm opens on the native Now; the farm's own pages are behind the Menu.
        rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("nav-menu")).fetchSemanticsNodes().isNotEmpty() }
        openFromMenu()
    }

    /** The Menu lists the farm's pages (pages.list): Tasks opens the farm's own page. */
    private fun openFromMenu() {
        rule.onNodeWithTag("nav-menu").performClick()
        rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("menu-tasks")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("menu-tasks").performClick()
    }

    private fun titleUntil(want: String): String =
        jsUntil("document.getElementById('title') ? document.getElementById('title').textContent : ''") { it == want }

    @Test
    fun aPageLinkOpensTheNextPageInTheShellAndBackReturns() {
        openFarm()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        // X-443: afnav is the shell's, not the farm's; the next page opens here, in a view of its own.
        js("acquireVsCodeApi().postMessage({type:'afnav',to:'needs'})")
        assertEquals(diagnosis(), "Fake page: needs", titleUntil("Fake page: needs"))
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        // A link to something that is not a page opens nothing.
        js("acquireVsCodeApi().postMessage({type:'afnav',to:'../x'})")
        Thread.sleep(500)
        assertEquals("Fake page: tasks", titleUntil("Fake page: tasks"))
    }

    @Test
    fun aPageLinkCarriesWhatTheNextPageIsToShow() {
        openFarm()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        // X-456: a transcript opened on the phone is afnav to "session" with the session; view.open carries it.
        js("acquireVsCodeApi().postMessage({type:'afnav',to:'session',args:{sessionId:'sid-2',ts:5}})")
        assertEquals(diagnosis(), "Fake page: session", titleUntil("Fake page: session"))
        val args = jsUntil("document.body.getAttribute('data-args') || ''") { it.contains("sid-2") }
        assertEquals(diagnosis(), true, args.contains("sid-2"))
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
    }

    @Test
    fun theMenuListsTheFarmsPagesAndBackFromOneIsTheMenu() {
        openFarm()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        rule.onNodeWithTag("page-back").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("menu-list")).fetchSemanticsNodes().isNotEmpty() }
        Shots.take("menu")
        // The farm's list (pages.list) in its groups, then the phone's own.
        for (tag in listOf("menu-notices", "menu-settings", "menu-wiki", "menu-farms", "menu-phone-settings")) {
            assertEquals(tag, true, rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty())
        }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("now-list")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aPagesSessionAndConsoleLinksOpenThePhonesOwnScreensUnderItsOwnTopBar() {
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("link-$farmId") and hasText("Connected", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("farm-$farmId").performClick()
        val recent = SemanticsMatcher("a history row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("recent-") == true }
        rule.waitUntil(20_000) { rule.onAllNodes(recent, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val session = rule.onAllNodes(recent, useUnmergedTree = true).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag].removePrefix("recent-")
        openFromMenu()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        // The phone's own bar names the page by the page's own title.
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("page-title") and hasText("Fake page: tasks")).fetchSemanticsNodes().isNotEmpty() }
        js("acquireVsCodeApi().postMessage({type:'afnav',to:'session',args:{sessionId:'$session'}})")
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("conv-title")).fetchSemanticsNodes().isNotEmpty() }
        // Back from the Conversation is the page it was opened from.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        js("acquireVsCodeApi().postMessage({type:'afnav',to:'console'})")
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasTestTag("now-list")).fetchSemanticsNodes().isNotEmpty() && rule.onAllNodes(hasTestTag("farm-page")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aCopyFromAPageLandsOnThePhonesClipboard() {
        openFarm()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        js("acquireVsCodeApi().postMessage({type:'afClipboard',token:'t1',text:'kopya metni'})")
        assertEquals(true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("afClipboardDone") }.contains("afClipboardDone"))
        var clip = ""
        rule.activityRule.scenario.onActivity { a ->
            clip = a.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }
        assertEquals("kopya metni", clip)
    }

    @Test
    fun aFileAPageSavesIsWrittenWhereThePersonChoseOnThePhone() {
        openFarm()
        assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
        val inst = InstrumentationRegistry.getInstrumentation()
        val target = File(inst.targetContext.cacheDir, "saved-export.md").apply { delete() }
        // The system's save picker answered as a person would: here is the file to write.
        val filter = IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("text/markdown") }
        val picker = inst.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))), true)
        try {
            assertEquals("true", js("String(window.afShell.saves())"))
            js("acquireVsCodeApi().postMessage({type:'afSave',token:'s1',name:'notes.md',mime:'text/markdown',data:'aGVsbG8gZnJvbSB0aGUgZmFybQ=='})")
            assertEquals(diagnosis(), true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("afSaveDone") }.contains("afSaveDone"))
            assertEquals(1, picker.hits)
            assertEquals("hello from the farm", target.readText())
        } finally {
            inst.removeMonitor(picker)
            target.delete()
        }
    }

    private fun nightMode(on: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night ${if (on) "yes" else "no"}").close()
    }

    @Test
    fun anOpenPageFollowsTheDevicesThemeWithoutReloading() {
        nightMode(false)
        try {
            openFarm()
            assertEquals(diagnosis(), "Fake page: tasks", titleUntil("Fake page: tasks"))
            js("window.afLoaded = 'first'")
            assertEquals("true", jsUntil("String(matchMedia('(prefers-color-scheme: light)').matches)") { it == "true" })
            assertEquals("true", jsUntil("String(document.body.classList.contains('vscode-light'))") { it == "true" })
            Shots.take("page-light")

            nightMode(true)
            assertEquals(diagnosis(), "false", jsUntil("String(matchMedia('(prefers-color-scheme: light)').matches)") { it == "false" })
            assertEquals("false", jsUntil("String(document.body.classList.contains('vscode-light'))") { it == "false" })
            // The same document: the theme changed under it, nothing reloaded it.
            assertEquals("first", js("window.afLoaded || ''"))
            Shots.take("page-dark")

            nightMode(false)
            assertEquals("true", jsUntil("String(matchMedia('(prefers-color-scheme: light)').matches)") { it == "true" })
            assertEquals("first", js("window.afLoaded || ''"))
        } finally {
            nightMode(false)
        }
    }

    @Test
    fun aFarmRowOpensItsPageThroughTheShell() {
        openFarm()

        val title = jsUntil("document.getElementById('title') ? document.getElementById('title').textContent : ''") { it.startsWith("Fake") }
        assertEquals(diagnosis(), "Fake page: tasks", title)
        assertEquals("function", js("typeof acquireVsCodeApi"))
        assertEquals("16px", js("getComputedStyle(document.body).marginTop"))

        // The host's answer to view.open reaches the page even if it arrived before the page loaded.
        assertEquals(true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("state") }.contains("state"))
        // Page -> shell -> host -> page: a posted message comes back as the host's echo.
        js("acquireVsCodeApi().postMessage({type:'hello'})")
        assertEquals(true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("echo") }.contains("echo"))
    }
}
