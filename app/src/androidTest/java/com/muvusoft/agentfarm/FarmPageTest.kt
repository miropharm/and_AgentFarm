package com.muvusoft.agentfarm

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

    @Test
    fun aFarmRowOpensItsPageThroughTheShell() {
        rule.waitUntil(20_000) {
            // The row is clickable, so its texts are merged into it; the link line is found in the unmerged tree.
            rule.onAllNodes(hasTestTag("link-$farmId") and hasText("Bağlı", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("farm-$farmId").performClick()

        val title = jsUntil("document.getElementById('title') ? document.getElementById('title').textContent : ''") { it.startsWith("Sahte") }
        assertEquals(diagnosis(), "Sahte sayfa: now", title)
        assertEquals("function", js("typeof acquireVsCodeApi"))
        assertEquals("16px", js("getComputedStyle(document.body).marginTop"))

        // The host's answer to view.open reaches the page even if it arrived before the page loaded.
        assertEquals(true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("state") }.contains("state"))
        // Page -> shell -> host -> page: a posted message comes back as the host's echo.
        js("acquireVsCodeApi().postMessage({type:'hello'})")
        assertEquals(true, jsUntil("document.body.getAttribute('data-seen') || ''") { it.contains("echo") }.contains("echo"))
    }
}
