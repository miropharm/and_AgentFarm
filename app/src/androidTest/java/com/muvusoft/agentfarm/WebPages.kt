package com.muvusoft.agentfarm

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A farm page in the app's screen in front, however that screen was opened (a journey opens it as the widget does,
 * with no scenario to ask): its WebView, a script run in it the way a tap on the page posts, and the WebView engine
 * started ahead of a timed step.
 */
object WebPages {
    private val main get() = InstrumentationRegistry.getInstrumentation()

    private fun front(): Activity? = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()

    /** What the page answers to [script]; empty when there is no page or it did not answer within 5 s. */
    fun js(script: String): String {
        var out = ""
        val done = CountDownLatch(1)
        main.runOnMainSync {
            val w = front()?.window?.decorView?.let { find(it) }
            if (w == null) done.countDown() else w.evaluateJavascript(script) { out = it; done.countDown() }
        }
        done.await(5, TimeUnit.SECONDS)
        return out.trim('"')
    }

    /**
     * The CI emulator starts the WebView engine for the first time in 25-65 s (FarmPageTest): a throwaway page in
     * the screen in front absorbs that start, so a journey's step measures the shell, not the emulator.
     */
    fun warm() {
        val done = CountDownLatch(1)
        var warm: WebView? = null
        main.runOnMainSync {
            val a = front() ?: return@runOnMainSync done.countDown()
            warm = WebView(a).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) = done.countDown()
                }
                loadData("<p>warm</p>", "text/html", null)
            }
        }
        done.await(180, TimeUnit.SECONDS)
        // Destroyed afterwards, never from inside its own callback (that killed the renderer, run 36696690290).
        main.runOnMainSync { warm?.destroy() }
    }

    private fun find(v: View): WebView? = when (v) {
        is WebView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }
}
