package com.muvusoft.agentfarm

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import java.io.File

/**
 * A user's journey measured as they live it, against the scripted farm on one network profile (none, lan,
 * mobile). Each step is what they do (its taps) and what they must then SEE, timed against its target and
 * shot; journeys.txt gets a line per step, a total, and how much the farm was asked. A step past its target,
 * a failed look, or a call past a page fails the test after every line is written.
 */
class Journey(private val code: String, private val profile: String, private val rule: ComposeTestRule? = null) {
    private val failed = mutableListOf<String>()
    private var n = 0
    private var taps = 0
    private var ms = 0L

    /** A fresh farm world on this journey's network; the farm's log starts empty. */
    fun begin(): Journey = apply { FakeHost.post("/_test/scenario", """{"profile":"$profile"}""") }

    /** A note in journeys.txt that is no step: what the journey leaves to a later piece of work. */
    fun note(text: String) = write("$code · $profile · note · $text")

    /** Returns how long the step took, for a target that runs on from it. */
    fun step(name: String, taps: Int, targetMs: Long, act: () -> Unit): Long {
        n++
        val frames = Frames.watch()
        val t0 = SystemClock.elapsedRealtime()
        val error = runCatching(act).exceptionOrNull()
        val took = SystemClock.elapsedRealtime() - t0
        frames.stop()
        this.taps += taps
        ms += took
        val pass = error == null && took <= targetMs
        // An artifact's file name takes no colon, quote or the like: the step's words keep letters and digits only.
        Shots.take("journey-$code-$profile-$n-" + name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-'))
        val floor = frames.line().also { frames.close() }
        val hidden = error?.let { rule?.let(Hidden::of) }
        hidden?.let { Log.e(TAG, "$code · $profile · $n: the composition's own error", it) }
        val why = error?.let { " · " + (it.message ?: it.javaClass.simpleName).lineSequence().first().take(160) }.orEmpty() +
            hidden?.let { " · hidden: " + told(it) }.orEmpty()
        write("$code · $profile · $n $name · taps $taps · $took ms · target ≤ $targetMs ms · $floor · ${if (pass) "PASS" else "FAIL"}$why")
        if (!pass) failed += "$name: " + (error?.message ?: "$took ms past $targetMs ms") + hidden?.let { " · hidden: " + told(it) }.orEmpty()
        return took
    }

    /** A look that is no timed step (the farm's log, a count): its line, and a failure when it does not hold. */
    fun check(name: String, ok: Boolean, detail: String) {
        write("$code · $profile · check · $name · $detail · ${if (ok) "PASS" else "FAIL"}")
        if (!ok) failed += "$name: $detail"
    }

    /** What the farm was asked during the journey; a call past a page is a finding and fails it. */
    fun economy() {
        val log = JSONObject(FakeHost.get("/_test/log"))
        val calls = log.getJSONArray("calls")
        val findings = log.getJSONArray("findings")
        var rows = 0
        var bytes = 0
        val ops = linkedMapOf<String, Int>()
        for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            rows += c.optInt("rows")
            bytes += c.optInt("bytes")
            ops.merge(c.getString("op"), 1, Int::plus)
        }
        val asked = ops.entries.joinToString(", ") { "${it.key} ×${it.value}" }
        val found = (0 until findings.length()).joinToString("; ") { findings.getString(it) }
        write("$code · $profile · economy · ${calls.length()} calls ($asked) · $rows rows · $bytes bytes · ${if (found.isEmpty()) "PASS" else "FAIL · $found"}")
        if (found.isNotEmpty()) failed += "economy: $found"
    }

    /** The total line; the farm goes back to its plain world for the tests after this one. */
    fun end() {
        write("$code · $profile · total · taps $taps · $ms ms · ${if (failed.isEmpty()) "PASS" else "FAIL"}")
        FakeHost.post("/_test/scenario", "{}")
        assertTrue(failed.joinToString("\n"), failed.isEmpty())
    }

    /** An error in one line: its kind, its words, and the app's own frame that threw it. */
    private fun told(e: Throwable): String {
        val at = e.stackTrace.firstOrNull { it.className.startsWith(APP_PACKAGE) } ?: e.stackTrace.firstOrNull()
        return "${e.javaClass.simpleName}: ${e.message.orEmpty().lineSequence().first().take(120)} at $at"
    }

    private fun write(line: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "journeys.txt").appendText(line + "\n")
    }

    private companion object {
        const val TAG = "AFJourney"
        const val APP_PACKAGE = "com.muvusoft.agentfarm"
    }
}
