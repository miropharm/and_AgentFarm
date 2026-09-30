package com.muvusoft.agentfarm

import android.app.Application
import android.content.Context
import com.muvusoft.agentfarm.net.ConnectionManager
import com.muvusoft.agentfarm.net.FarmStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One connection manager and one farm store for the whole process: the screens and the link service share them. */
class AgentFarmApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store by lazy { FarmStore(this) }
    val manager by lazy { ConnectionManager(scope) }

    companion object {
        fun of(context: Context): AgentFarmApp = context.applicationContext as AgentFarmApp
    }
}
