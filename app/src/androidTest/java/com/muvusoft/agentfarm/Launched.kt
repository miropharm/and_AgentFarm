package com.muvusoft.agentfarm

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/**
 * The app opened the way the widget and the launcher open it: one startActivity and nothing in between
 * (ActivityScenario first starts a screen of its own, a cost no user pays). [createdMs]: how long Android took
 * to create the app's screen after the tap; the rest of a step's time is the app's.
 */
class Launched private constructor() : AutoCloseable {
    private val asked = SystemClock.elapsedRealtime()

    @Volatile private var activity: Activity? = null

    @Volatile private var created = 0L

    private val watch = ActivityLifecycleCallback { a, stage ->
        if (a is MainActivity && stage == Stage.PRE_ON_CREATE && activity == null) {
            activity = a
            created = SystemClock.elapsedRealtime()
        }
    }

    val createdMs: Long get() = if (created == 0L) -1 else created - asked

    override fun close() {
        val i = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        monitor.removeLifecycleCallback(watch)
        val a = activity ?: return
        i.runOnMainSync { a.finish() }
        val end = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < end) {
            var gone = false
            i.runOnMainSync { gone = runCatching { monitor.getLifecycleStageOf(a) == Stage.DESTROYED }.getOrDefault(true) }
            if (gone) return
            Thread.sleep(50)
        }
    }

    companion object {
        /** Now, as the widget opens it. */
        fun now(): Launched {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val l = Launched()
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(l.watch)
            ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN_NOW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return l
        }
    }
}
