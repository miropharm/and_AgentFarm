package com.muvusoft.agentfarm.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.muvusoft.agentfarm.core.link.Reconnect

/** Tells the farm links when the phone's default network comes, goes or changes (Wi-Fi to mobile and back). */
class NetworkWatch(context: Context, private val onNet: (Reconnect.Net) -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    // Kept across a loss: Wi-Fi lost and then mobile available is a switch, not a first network.
    @Volatile private var last: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val before = last
            last = network
            onNet(if (before != null && before != network) Reconnect.Net.SWITCHED else Reconnect.Net.AVAILABLE)
        }

        override fun onLost(network: Network) {
            if (network == last) onNet(Reconnect.Net.LOST)
        }
    }

    fun start() {
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
