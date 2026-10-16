package com.muvusoft.agentfarm

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The frames the app drew during one journey step, from Android's own frame metrics on every app window in view
 * (a screen that opens during the step included): the emulator's floor beside the step's time, so the app's own
 * share is the step less its frames. A frame is the step's when its vsync falls inside it. Watching never delays
 * the step: the window is joined on the main thread's next turn, not waited for.
 */
class Frames private constructor(private val from: Long) {
    private val drawn = ConcurrentLinkedQueue<LongArray>()

    /** Touched on the main thread only. */
    private val windows = mutableSetOf<Window>()

    @Volatile private var until = Long.MAX_VALUE

    private val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        drawn += longArrayOf(m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP), m.getMetric(FrameMetrics.TOTAL_DURATION))
    }

    private val resumed = ActivityLifecycleCallback { a, stage -> if (stage == Stage.RESUMED) join(a) }

    private fun join(a: Activity) {
        val w = a.window
        if (windows.add(w)) runCatching { w.addOnFrameMetricsAvailableListener(listener, metrics) }
    }

    /** The step is over: a frame after this moment is not its own. */
    fun stop() {
        until = System.nanoTime()
    }

    /** The step's frames and the slowest of them; read a moment after [stop], once the last frame's metrics came in. */
    fun line(): String {
        val own = drawn.filter { it[0] in from..until }
        return "frames ${own.size}, slowest ${(own.maxOfOrNull { it[1] } ?: 0L) / 1_000_000} ms"
    }

    fun close() {
        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(resumed)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            windows.forEach { w -> runCatching { w.removeOnFrameMetricsAvailableListener(listener) } }
            windows.clear()
        }
    }

    companion object {
        private val metrics by lazy { Handler(HandlerThread("journey-frames").apply { start() }.looper) }

        fun watch(): Frames {
            val f = Frames(System.nanoTime())
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            monitor.addLifecycleCallback(f.resumed)
            Handler(Looper.getMainLooper()).post { monitor.getActivitiesInStage(Stage.RESUMED).forEach(f::join) }
            return f
        }
    }
}
