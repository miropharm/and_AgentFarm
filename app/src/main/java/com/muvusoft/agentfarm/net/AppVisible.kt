package com.muvusoft.agentfarm.net

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether any of the app's screens is started, counted from the activities' own lifecycle (main thread only). */
class AppVisible : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    override fun onActivityStarted(activity: Activity) {
        started++
        _on.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        _on.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
