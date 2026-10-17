package com.muvusoft.agentfarm

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.atomic.AtomicLong

/**
 * The keyboard as a person meets it when they press Send: up, and the screen still under it. A person types for
 * seconds, so the keyboard rising is never inside their tap; a test types at once, and without this the step after
 * the typing pays for the keyboard's slide and the window giving way to it. Every window of the app is watched (a
 * sheet is a window of its own); a blinking cursor draws less often than [QUIET_MS].
 */
object Keyboard {
    private const val QUIET_MS = 400L

    /** True once the keyboard is up and no window of the app drew for [QUIET_MS]; false when that did not come in [ms]. */
    fun settle(ms: Long = 5_000): Boolean {
        val drawn = AtomicLong(SystemClock.elapsedRealtime())
        val listener = ViewTreeObserver.OnDrawListener { drawn.set(SystemClock.elapsedRealtime()) }
        val views = onMain { windows().onEach { it.viewTreeObserver.addOnDrawListener(listener) } }
        try {
            val end = SystemClock.elapsedRealtime() + ms
            while (SystemClock.elapsedRealtime() < end) {
                if (SystemClock.elapsedRealtime() - drawn.get() >= QUIET_MS && onMain { up() }) return true
                Thread.sleep(20)
            }
            return false
        } finally {
            onMain { views.forEach { v -> v.viewTreeObserver.takeIf { it.isAlive }?.removeOnDrawListener(listener) } }
        }
    }

    private fun up() = windows().any { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) == true }

    /** Below Android 10 the windows cannot be listed: the screen in front is the one looked at. */
    private fun windows(): List<View> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return WindowInspector.getGlobalWindowViews()
        return ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).map { it.window.decorView }
    }

    private fun <T> onMain(f: () -> T): T {
        var r: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { r = runCatching(f) }
        return r!!.getOrThrow()
    }
}
