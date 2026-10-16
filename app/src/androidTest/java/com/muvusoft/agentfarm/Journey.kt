package com.muvusoft.agentfarm

import android.os.SystemClock
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
class Journey(private val code: String, private val profile: String) {
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
        val t0 = SystemClock.elapsedRealtime()
        val error = runCatching(act).exceptionOrNull()
        val took = SystemClock.elapsedRealtime() - t0
        this.taps += taps
        ms += took
        val pass = error == null && took <= targetMs
        Shots.take("journey-$code-$profile-$n-${name.replace(' ', '-')}")
        val why = error?.let { " · " + (it.message ?: it.javaClass.simpleName).lineSequence().first().take(160) }.orEmpty()
        write("$code · $profile · $n $name · taps $taps · $took ms · target ≤ $targetMs ms · ${if (pass) "PASS" else "FAIL"}$why")
        if (!pass) failed += "$name: " + (error?.message ?: "$took ms past $targetMs ms")
        return took
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

    private fun write(line: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "journeys.txt").appendText(line + "\n")
    }
}
